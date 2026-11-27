"""Clutch Alarm MVP API. Timestamps are Unix milliseconds; tokens are opaque."""
import hashlib
import hmac
import json
import logging
import os
import re
import secrets
import threading
import time
from collections import OrderedDict
from contextlib import asynccontextmanager
from typing import Literal
from uuid import UUID, uuid4

from argon2 import PasswordHasher
from argon2.exceptions import VerificationError
from fastapi import FastAPI, Header, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from sqlalchemy import delete, or_, select, text
from sqlalchemy.exc import IntegrityError, SQLAlchemyError

from database import Base, BehaviorEvent, DeletionReceipt, LoginSession, User, make_database

ACCESS_MS = 15 * 60 * 1000
SESSION_MS = 30 * 24 * 60 * 60 * 1000
PH = PasswordHasher(time_cost=2, memory_cost=19456, parallelism=1)
DUMMY_HASH = PH.hash(secrets.token_urlsafe(32))
LOG = logging.getLogger('clutch')


def now():
    return int(time.time() * 1000)


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def fail(status, code):
    raise HTTPException(status, detail=code)


class DTO(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=False)


class Credentials(DTO):
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=1, max_length=128)

    @field_validator('email')
    @classmethod
    def normalize_email(cls, value):
        value = value.strip().lower()
        if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', value):
            raise ValueError('Invalid email')
        return value


class RefreshCredential(DTO):
    refreshToken: str = Field(min_length=32, max_length=128)


class RefreshRequest(RefreshCredential):
    requestId: UUID


class LogoutRequest(RefreshCredential):
    requestId: UUID | None = None


class EventIn(DTO):
    event_id: UUID
    name: Literal['app_open', 'first_done', 'repeat_done', 'drop']
    action: Literal['notification_open', 'settings_resave'] | None = None
    installationId: UUID
    notificationId: str | None = Field(default=None, min_length=1, max_length=200)
    notificationSequence: int | None = Field(default=None, ge=1, le=2**63-1)
    gameId: str | None = Field(default=None, min_length=1, max_length=200)
    flowId: str | None = Field(default=None, min_length=1, max_length=200)
    schemaVersion: Literal[1]
    occurredAtMillis: int = Field(ge=0, le=2**63-1)

    @model_validator(mode='after')
    def validate_kind(self):
        notification = self.notificationId is not None and self.notificationSequence is not None and self.gameId is not None
        no_notification = all(x is None for x in (self.notificationId, self.notificationSequence, self.gameId))
        valid = (
            (self.name == 'app_open' and self.action is None and no_notification and self.flowId is None)
            or (self.name == 'first_done' and self.action is None and notification and self.notificationSequence == 1 and self.flowId is None)
            or (self.name == 'repeat_done' and self.action == 'notification_open' and notification and self.notificationSequence >= 2 and self.flowId is None)
            or (self.name == 'repeat_done' and self.action == 'settings_resave' and no_notification and self.flowId is not None)
            or (self.name == 'drop' and self.action is None and no_notification and self.flowId is not None)
        )
        if not valid:
            raise ValueError('Invalid event fields')
        return self

    def semantic_key(self):
        # Installation-level analytics only; no email, token or account attribution.
        scope = str(self.installationId)
        if self.name == 'first_done':
            key = [scope, self.name]
        elif self.action == 'notification_open':
            key = [scope, self.name, self.action, self.notificationId]
        elif self.flowId:
            key = [scope, self.name, self.action, self.flowId]
        else:
            key = [scope, self.name, str(self.event_id)]
        return digest(json.dumps(key, separators=(',', ':')))


class Limiter:
    """Bounded, process-local defense for the single-worker MVP; not a WAF."""
    def __init__(self):
        self.lock = threading.Lock()
        self.slots = OrderedDict()

    def hit(self, request, group, limit):
        key = (request.client.host if request.client else 'unknown', group)
        minute = int(time.monotonic() // 60)
        with self.lock:
            old_minute, count = self.slots.pop(key, (minute, 0))
            count = count + 1 if old_minute == minute else 1
            self.slots[key] = (minute, count)
            while len(self.slots) > 10000:
                self.slots.popitem(last=False)
        if count > limit:
            raise HTTPException(429, detail='RATE_LIMIT', headers={'Retry-After': '60'})


def create_app(database_url=None, rotation_secret=None):
    @asynccontextmanager
    async def lifespan(app):
        secret = rotation_secret or os.environ.get('TOKEN_ROTATION_SECRET', '')
        if len(secret) < 32:
            raise RuntimeError('TOKEN_ROTATION_SECRET must be a stable random secret (at least 32 characters)')
        app.state.secret = secret.encode()
        engine, factory = make_database(database_url)
        app.state.db = factory
        try:
            Base.metadata.create_all(engine)
            yield
        finally:
            engine.dispose()

    app = FastAPI(title='Clutch Alarm API', version='1.0.0', lifespan=lifespan)
    limiter = Limiter()

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        # FastAPI default validation response may echo passwords/tokens.
        return JSONResponse(status_code=422, content={'detail': 'INVALID_INPUT'})

    @app.exception_handler(SQLAlchemyError)
    async def db_error(request, exc):
        LOG.warning('Database operation failed (%s)', type(exc).__name__)
        return JSONResponse(status_code=503, content={'detail': 'DATABASE_UNAVAILABLE'})

    @app.middleware('http')
    async def no_cache(request, call_next):
        response = await call_next(request)
        response.headers['Cache-Control'] = 'no-store'
        return response

    @app.get('/')
    def root():
        return {'message': 'Clutch Alarm Server is running'}

    @app.get('/health')
    def health():
        return {'status': 'ok', 'service': 'clutch-alarm'}

    @app.get('/health/db')
    def health_db(request: Request):
        with request.app.state.db() as db:
            db.execute(text('SELECT 1'))
        return {'database': 'connected'}

    def response_tokens(session, access, refresh):
        return {'accessToken': access, 'refreshToken': refresh,
                'accessExpiresAtMillis': session.access_expires_at,
                'refreshExpiresAtMillis': session.refresh_expires_at}

    def derive(secret, old, rotation_id, kind):
        return hmac.new(secret, f'{kind}:{old}:{rotation_id}'.encode(), hashlib.sha256).hexdigest()

    def bearer(header):
        if not header or not header.startswith('Bearer ') or not 32 <= len(header[7:]) <= 128:
            fail(401, 'INVALID_SESSION')
        return digest(header[7:])

    def access_session(db, header):
        session = db.scalar(select(LoginSession).where(LoginSession.access_hash == bearer(header)))
        if not session or min(session.access_expires_at, session.refresh_expires_at) <= now():
            fail(401, 'INVALID_SESSION')
        return session

    def locked_refresh(db, hashed, previous=False):
        condition = LoginSession.refresh_hash == hashed
        if previous:
            condition = or_(condition, LoginSession.previous_refresh_hash == hashed)
        return db.scalar(select(LoginSession).where(condition).with_for_update())

    @app.post('/api/auth/signup', status_code=201)
    def signup(body: Credentials, request: Request):
        limiter.hit(request, 'signup', 15)
        if len(body.password) < 8 or not body.password.strip():
            fail(422, 'INVALID_INPUT')
        with request.app.state.db() as db:
            user = User(id=str(uuid4()), email=body.email, password_hash=PH.hash(body.password), created_at=now())
            db.add(user)
            try:
                db.commit()
            except IntegrityError:
                db.rollback()
                if db.scalar(select(User.id).where(User.email == body.email)):
                    fail(409, 'EMAIL_IN_USE')
                raise
        return {'status': 'created'}

    @app.post('/api/auth/login')
    def login(body: Credentials, request: Request):
        limiter.hit(request, 'login', 30)
        with request.app.state.db() as db:
            user = db.scalar(select(User).where(User.email == body.email))
            try:
                verified = PH.verify(user.password_hash if user else DUMMY_HASH, body.password)
            except VerificationError:
                verified = False
            if not user or not verified:
                fail(401, 'INVALID_CREDENTIALS')
            access, refresh = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
            timestamp = now()
            db.execute(delete(LoginSession).where(LoginSession.user_id == user.id, LoginSession.refresh_expires_at <= timestamp))
            session = LoginSession(id=str(uuid4()), user_id=user.id, access_hash=digest(access), refresh_hash=digest(refresh),
                                   access_expires_at=timestamp + ACCESS_MS, refresh_expires_at=timestamp + SESSION_MS, created_at=timestamp)
            db.add(session)
            db.commit()
            return response_tokens(session, access, refresh)

    @app.post('/api/auth/refresh')
    def refresh(body: RefreshRequest, request: Request):
        limiter.hit(request, 'refresh', 120)
        old_hash, rid = digest(body.refreshToken), str(body.requestId)
        with request.app.state.db() as db:
            session = locked_refresh(db, old_hash, previous=True)
            if not session or session.refresh_expires_at <= now():
                fail(401, 'INVALID_SESSION')
            access = derive(request.app.state.secret, body.refreshToken, rid, 'access')
            new_refresh = derive(request.app.state.secret, body.refreshToken, rid, 'refresh')
            if session.previous_refresh_hash == old_hash:
                if session.rotation_id != rid or session.refresh_hash != digest(new_refresh):
                    fail(401, 'INVALID_SESSION')
                return response_tokens(session, access, new_refresh)
            session.previous_refresh_hash = old_hash
            session.rotation_id = rid
            session.access_hash = digest(access)
            session.refresh_hash = digest(new_refresh)
            session.access_expires_at = min(now() + ACCESS_MS, session.refresh_expires_at)
            db.commit()
            return response_tokens(session, access, new_refresh)

    @app.get('/api/auth/me')
    def me(request: Request, authorization: str | None = Header(default=None)):
        with request.app.state.db() as db:
            session = access_session(db, authorization)
            user = db.get(User, session.user_id)
            if not user:
                fail(401, 'INVALID_SESSION')
            return {'email': user.email}

    @app.post('/api/auth/logout', status_code=204)
    def logout(body: LogoutRequest, request: Request):
        hashed = digest(body.refreshToken)
        with request.app.state.db() as db:
            session = locked_refresh(db, hashed, previous=body.requestId is not None)
            if session and (session.refresh_hash == hashed or session.rotation_id == str(body.requestId)):
                db.delete(session)
                db.commit()
        return Response(status_code=204)

    @app.delete('/api/auth/account', status_code=204)
    def delete_account(body: RefreshCredential, request: Request):
        hashed = digest(body.refreshToken)
        with request.app.state.db() as db:
            session = locked_refresh(db, hashed)
            receipt = db.get(DeletionReceipt, hashed)
            if receipt and receipt.expires_at > now():
                return Response(status_code=204)
            if not session or session.refresh_expires_at <= now():
                fail(401, 'INVALID_SESSION')
            # Delete all account sessions atomically with account and retry receipt.
            db.execute(delete(DeletionReceipt).where(DeletionReceipt.expires_at <= now()))
            db.add(DeletionReceipt(refresh_hash=hashed, expires_at=now() + SESSION_MS))
            db.execute(delete(User).where(User.id == session.user_id))
            db.commit()
        return Response(status_code=204)

    @app.post('/api/events')
    def collect(body: EventIn, request: Request):
        limiter.hit(request, 'events', 300)
        payload = body.model_dump(mode='json')
        event_id = str(body.event_id)
        semantic = body.semantic_key()
        payload_hash = digest(json.dumps(payload, sort_keys=True, separators=(',', ':')))
        with request.app.state.db() as db:
            row = BehaviorEvent(event_id=event_id, dedupe_key=semantic, payload_hash=payload_hash,
                                name=body.name, action=body.action, installation_id=str(body.installationId),
                                notification_id=body.notificationId, notification_sequence=body.notificationSequence,
                                game_id=body.gameId, flow_id=body.flowId, schema_version=body.schemaVersion,
                                occurred_at=body.occurredAtMillis, received_at=now())
            db.add(row)
            try:
                db.commit()
                status = 'stored'
            except IntegrityError:
                db.rollback()
                existing = db.get(BehaviorEvent, event_id)
                if existing:
                    if existing.payload_hash != payload_hash:
                        fail(409, 'EVENT_ID_CONFLICT')
                elif not db.scalar(select(BehaviorEvent.event_id).where(BehaviorEvent.dedupe_key == semantic)):
                    raise
                status = 'duplicate'
        return {'event_id': event_id, 'status': status}

    return app

app = create_app()
