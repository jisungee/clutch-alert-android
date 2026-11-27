import os
from sqlalchemy import (BigInteger, ForeignKey, Integer, String, Text, create_engine, event)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

class Base(DeclarativeBase):
    pass

class User(Base):
    __tablename__ = 'users'
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    email: Mapped[str] = mapped_column(String(254), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(BigInteger)

class LoginSession(Base):
    __tablename__ = 'sessions'
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey('users.id', ondelete='CASCADE'), index=True)
    access_hash: Mapped[str] = mapped_column(String(64), unique=True)
    refresh_hash: Mapped[str] = mapped_column(String(64), unique=True)
    previous_refresh_hash: Mapped[str | None] = mapped_column(String(64), unique=True)
    rotation_id: Mapped[str | None] = mapped_column(String(36))
    access_expires_at: Mapped[int] = mapped_column(BigInteger)
    refresh_expires_at: Mapped[int] = mapped_column(BigInteger)
    created_at: Mapped[int] = mapped_column(BigInteger)

class DeletionReceipt(Base):
    __tablename__ = 'deletion_receipts'
    refresh_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    expires_at: Mapped[int] = mapped_column(BigInteger)

class BehaviorEvent(Base):
    __tablename__ = 'behavior_events'
    event_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    dedupe_key: Mapped[str] = mapped_column(String(64), unique=True)
    payload_hash: Mapped[str] = mapped_column(String(64))
    name: Mapped[str] = mapped_column(String(20), index=True)
    action: Mapped[str | None] = mapped_column(String(30))
    installation_id: Mapped[str] = mapped_column(String(36), index=True)
    notification_id: Mapped[str | None] = mapped_column(String(200))
    notification_sequence: Mapped[int | None] = mapped_column(BigInteger)
    game_id: Mapped[str | None] = mapped_column(String(200))
    flow_id: Mapped[str | None] = mapped_column(String(200))
    schema_version: Mapped[int] = mapped_column(Integer)
    occurred_at: Mapped[int] = mapped_column(BigInteger)
    received_at: Mapped[int] = mapped_column(BigInteger)


def make_database(url: str | None = None):
    url = url or os.environ.get('DATABASE_URL', '')
    if url.startswith('postgres://'):
        url = 'postgresql+psycopg://' + url[len('postgres://'):]
    elif url.startswith('postgresql://'):
        url = 'postgresql+psycopg://' + url[len('postgresql://'):]
    if url.startswith('sqlite:') and os.environ.get('CLUTCH_LOCAL_DEV') == '1':
        engine = create_engine(url, connect_args={'check_same_thread': False})
        @event.listens_for(engine, 'connect')
        def enable_fk(connection, _):
            connection.execute('PRAGMA foreign_keys=ON')
    elif url.startswith('postgresql+psycopg://'):
        engine = create_engine(url, pool_pre_ping=True, pool_size=3, max_overflow=2,
                               connect_args={'connect_timeout': 5})
    else:
        raise RuntimeError('Set DATABASE_URL to PostgreSQL (SQLite only with CLUTCH_LOCAL_DEV=1)')
    return engine, sessionmaker(bind=engine, expire_on_commit=False)
