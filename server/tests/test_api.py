import uuid
import os
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select, func
from main import create_app
from database import User, LoginSession, BehaviorEvent

@pytest.fixture
def client(tmp_path, monkeypatch):
    # Set TEST_DATABASE_URL to a disposable PostgreSQL DB for a true PostgreSQL run.
    url = os.getenv('TEST_DATABASE_URL')
    if url:
        from sqlalchemy.engine import make_url
        if os.getenv('CLUTCH_ALLOW_TEST_DB_RESET') != 'YES' or not (make_url(url).database or '').endswith('_test'):
            pytest.fail('Use an isolated DB ending in _test and CLUTCH_ALLOW_TEST_DB_RESET=YES; tests delete all rows')
    if not url:
        monkeypatch.setenv('CLUTCH_LOCAL_DEV', '1')
        url = f'sqlite:///{tmp_path / "test.db"}'
    app = create_app(url, 'test-secret-' * 5)
    with TestClient(app) as c:
        if os.getenv('TEST_DATABASE_URL'):
            from database import Base
            with app.state.db() as db:
                for table in reversed(Base.metadata.sorted_tables):
                    db.execute(table.delete())
                db.commit()
        yield c


def credentials(email='Tester@example.com'):
    return {'email': email, 'password': 'password123'}


def login(c, email='Tester@example.com'):
    assert c.post('/api/auth/signup', json=credentials(email)).status_code == 201
    response = c.post('/api/auth/login', json=credentials(email))
    assert response.status_code == 200
    return response.json()


def auth(tokens):
    return {'Authorization': 'Bearer ' + tokens['accessToken']}


def rotate(c, tokens, rid=None):
    return c.post('/api/auth/refresh', json={'refreshToken': tokens['refreshToken'], 'requestId': rid or str(uuid.uuid4())})


def remove(c, tokens):
    return c.request('DELETE', '/api/auth/account', json={'refreshToken': tokens['refreshToken']})


def event(name, install=None, **fields):
    return dict(event_id=str(uuid.uuid4()), name=name, installationId=install or str(uuid.uuid4()),
                schemaVersion=1, occurredAtMillis=1700000000000, **fields)


def test_health(client):
    assert client.get('/health').json()['status'] == 'ok'
    assert client.get('/health/db').json() == {'database': 'connected'}


def test_registration_and_password_hash(client):
    assert client.post('/api/auth/signup', json=credentials()).json() == {'status': 'created'}
    assert client.post('/api/auth/signup', json=credentials('tester@EXAMPLE.com')).status_code == 409
    with client.app.state.db() as db:
        user = db.scalar(select(User))
        assert user.email == 'tester@example.com'
        assert user.password_hash.startswith('$argon2id$') and 'password123' not in user.password_hash
        assert db.scalar(select(func.count()).select_from(LoginSession)) == 0


def test_wrong_credentials_and_validation_redacted(client):
    login(client)
    assert client.post('/api/auth/login', json={'email':'tester@example.com','password':'wrong'}).status_code == 401
    invalid = client.post('/api/auth/signup', json={'email':'bad', 'password':'secret-password'})
    assert invalid.status_code == 422 and 'secret-password' not in invalid.text
    assert client.post('/api/auth/signup', json={'email':'good@ex.com', 'password':'short'}).status_code == 422


def test_login_me_and_token_storage(client):
    tokens = login(client)
    assert client.get('/api/auth/me', headers=auth(tokens)).json() == {'email':'tester@example.com'}
    assert client.get('/api/auth/me').status_code == 401
    with client.app.state.db() as db:
        session = db.scalar(select(LoginSession))
        assert session.access_hash != tokens['accessToken'] and session.refresh_hash != tokens['refreshToken']


def test_refresh_rotation_and_lost_response(client):
    tokens = login(client)
    rid = str(uuid.uuid4())
    rotated = rotate(client, tokens, rid)
    assert rotated.status_code == 200
    new = rotated.json()
    assert new['accessToken'] != tokens['accessToken'] and new['refreshToken'] != tokens['refreshToken']
    assert client.get('/api/auth/me', headers=auth(tokens)).status_code == 401
    assert rotate(client, tokens, rid).json() == new
    assert rotate(client, tokens).status_code == 401
    assert client.get('/api/auth/me', headers=auth(new)).status_code == 200
    assert rotate(client, new).status_code == 200
    assert rotate(client, tokens, rid).status_code == 401


def test_logout_current_device_only(client):
    a = login(client)
    b = client.post('/api/auth/login', json=credentials()).json()
    assert client.post('/api/auth/logout', json={'refreshToken':a['refreshToken']}).status_code == 204
    assert client.get('/api/auth/me', headers=auth(a)).status_code == 401
    assert rotate(client, a).status_code == 401
    assert client.get('/api/auth/me', headers=auth(b)).status_code == 200


def test_logout_pending_rotation(client):
    a = login(client); rid = str(uuid.uuid4()); b = rotate(client,a,rid).json()
    assert client.post('/api/auth/logout', json={'refreshToken':a['refreshToken'], 'requestId':rid}).status_code == 204
    assert client.get('/api/auth/me', headers=auth(b)).status_code == 401


def test_delete_account_all_sessions_retry(client):
    a = login(client)
    b = client.post('/api/auth/login', json=credentials()).json()
    assert remove(client,a).status_code == 204
    assert remove(client,a).status_code == 204
    assert client.get('/api/auth/me', headers=auth(b)).status_code == 401
    assert client.post('/api/auth/login', json=credentials()).status_code == 401
    assert client.post('/api/auth/signup', json=credentials()).status_code == 201
    assert remove(client,a).status_code == 204  # receipt must not delete new account
    assert client.post('/api/auth/login', json=credentials()).status_code == 200
    assert remove(client,b).status_code == 401


def test_event_counts_and_retry(client):
    install = str(uuid.uuid4())
    items = [event('app_open', install),
        event('first_done', install, notificationId='one', notificationSequence=1, gameId='game'),
        event('repeat_done', install, action='notification_open', notificationId='two',notificationSequence=2,gameId='game'),
        event('repeat_done', install, action='settings_resave',flowId='save'),
        event('drop', install, flowId='drop')]
    for item in items:
        assert client.post('/api/events', json=item).json() == {'event_id':item['event_id'],'status':'stored'}
        assert client.post('/api/events', json=item).json() == {'event_id':item['event_id'],'status':'duplicate'}
    semantic_repeat = dict(items[1], event_id=str(uuid.uuid4()))
    assert client.post('/api/events',json=semantic_repeat).json()['status'] == 'duplicate'
    with client.app.state.db() as db:
        assert dict(db.execute(select(BehaviorEvent.name,func.count()).group_by(BehaviorEvent.name)).all()) == {'app_open':1,'first_done':1,'repeat_done':2,'drop':1}


def test_invalid_events_and_conflicting_id(client):
    item = event('app_open')
    assert client.post('/api/events',json=item).status_code == 200
    assert client.post('/api/events',json=dict(item,occurredAtMillis=3)).status_code == 409
    assert client.post('/api/events',json=event('first_done')).status_code == 422
    assert client.post('/api/events',json=event('app_open',password='secret')).status_code == 422


def test_expiry(client):
    tokens = login(client)
    with client.app.state.db() as db:
        session = db.scalar(select(LoginSession)); session.access_expires_at = 1; db.commit()
    assert client.get('/api/auth/me',headers=auth(tokens)).status_code == 401
    new = rotate(client,tokens).json()
    assert client.get('/api/auth/me',headers=auth(new)).status_code == 200
    with client.app.state.db() as db:
        session = db.scalar(select(LoginSession)); session.refresh_expires_at = 1; db.commit()
    assert rotate(client,new).status_code == 401
    assert remove(client,new).status_code == 401
