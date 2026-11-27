# 클러치알림 서버 연동 구현 / 배포 안내

기준: 첨부된 `codex/handoff-20261127` 소스. 기존 10경기, 알림 규칙, 화면, QA 인증은 유지한다.
이 문서는 새 구현에 해당하며 기존 `server-handoff.md` / `final-qa-report.md`는 이전 클라이언트 QA 증거이다.

## 1. 기존 GitHub 작업 폴더에 적용

`feature/backend` 등 현재 Render가 바라볼 브랜치를 체크아웃한다. 기존 변경이 있다면 먼저 백업/커밋한다.
`clutch-backend-overlay.zip` 내부 경로는 저장소 루트 기준이다. 기존 최소 서버 `server/main.py` 및 requirements를 교체한다.
Linux/macOS 예:

```bash
cd /path/to/clutch-alert-android
git status
git branch --show-current
unzip -o ~/Downloads/clutch-backend-overlay.zip -d .
git diff --check
git status
git add app server docs/backend-deploy.md
git commit -m "Implement backend auth and durable behavior collection"
git push -u origin HEAD
```

Windows PowerShell (저장소 루트):

```powershell
Expand-Archive -Path "$HOME\Downloads\clutch-backend-overlay.zip" -DestinationPath . -Force
git status
git add app server docs/backend-deploy.md
git commit -m "Implement backend auth and durable behavior collection"
git push -u origin HEAD
```

ZIP 전체본은 새 폴더에서 참고/빌드할 때 사용한다. 기존 `.git`은 건드리지 않는다.

## 2. 로컬 테스트 (Render 전에 가능)

Python 3.12 또는 3.13. `server/`에서 가상환경 생성:

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

Windows: `python -m venv .venv` 후 `.\.venv\Scripts\Activate.ps1`.
기본 테스트는 별도 임시 SQLite를 사용한다. PostgreSQL 검증은 별도 빈 테스트 DB 이름이 `_test`로 끝나야 하고,
`TEST_DATABASE_URL` 및 `CLUTCH_ALLOW_TEST_DB_RESET=YES`를 설정해야 한다. 테스트는 그 DB의 앱 테이블 행을 삭제한다. 운영 DB 사용 금지.

로컬 API 실행(개발 전용 SQLite, Linux/macOS):

```bash
export CLUTCH_LOCAL_DEV=1
export DATABASE_URL=sqlite:///./clutch_local.db
export TOKEN_ROTATION_SECRET="$(python -c 'import secrets; print(secrets.token_hex(32))')"
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

PowerShell:

```powershell
$env:CLUTCH_LOCAL_DEV="1"
$env:DATABASE_URL="sqlite:///./clutch_local.db"
$env:TOKEN_ROTATION_SECRET=(python -c "import secrets; print(secrets.token_hex(32))")
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

`http://127.0.0.1:8000/docs`에서 API 테스트. 실제 모바일 앱은 HTTPS만 허용하므로 이 로컬 HTTP 주소를 앱에 넣지 않는다.
로컬 DB의 토큰 갱신 재현에 같은 환경변수 값을 유지한다.

## 3. Render 설정

기존 Render Postgres/Web Service를 재사용한다. 새 DB를 만들 필요가 없다(기존 DB에 이 앱의 테이블과 충돌하지 않는다는 전제).

| 항목 | 값 |
|---|---|
| Repository | jisungee/clutch-alert-android |
| Branch | 실제로 push한 작업 브랜치 (예: feature/backend) |
| Region | DB와 같은 지역 |
| Root Directory | server |
| Runtime | Python |
| Build Command | `pip install -r requirements.txt` |
| Start Command | `uvicorn main:app --host 0.0.0.0 --port $PORT` |
| Health Check Path | `/health/db` |

Environment에 다음 두 값을 설정한다.
- `DATABASE_URL`: Render DB의 Internal Database URL.
- `TOKEN_ROTATION_SECRET`: 아래 명령 결과. GitHub에 올리지 않고 최초 한 번 생성해 Render에 저장하며 이후 재배포에서 유지한다.

```bash
python -c "import secrets; print(secrets.token_hex(32))"
```

Render에는 `CLUTCH_LOCAL_DEV`를 설정하지 않는다. 서버 첫 실행 시 SQLAlchemy가 네 테이블을 생성한다.
기존 테이블을 변경하는 자동 migration은 제공하지 않는다. 기존 동일 이름의 테이블이 있는 DB에 무작정 적용하지 않는다.
Manual Deploy / Deploy latest commit 후 발급 주소의 `/health`, `/health/db`, `/docs` 확인.
Free Postgres는 장기 저장 용도로 사용하지 않는다. 무료 웹 서버가 휴면 상태일 때 처음 요청이 느릴 수 있다.
Koyeb 사용 시 동일 GitHub 브랜치, Work Directory `server`, Buildpack, Run Command `uvicorn main:app --host 0.0.0.0 --port 8000`, exposed port 8000, 위 환경변수 사용. DB 주소는 Koyeb에서 접근 가능한 주소여야 한다.

## 4. Android 앱 빌드

JDK17, SDK36, ADB 필요. 프로젝트 루트에서 서버 주소를 Gradle 속성으로 주입한다.
`API_BASE_URL`이 비어 있으면 기존 미연동 상태이며 QA 빌드는 실제 서버로 요청하지 않는다.

Linux/macOS:

```bash
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew -PapiBaseUrl=https://YOUR-SERVICE.onrender.com :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat "-PapiBaseUrl=https://YOUR-SERVICE.onrender.com" :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`YOUR-SERVICE`를 본인 Render 주소로 교체. 테스트는 URL 없이 실행해 실서버 접근을 방지한다.
기존 APK와 서명이 다르면 업데이트가 실패할 수 있다. 기존 사용자의 데이터 삭제를 강행하지 말고 새 테스트 기기 또는 동일 서명 사용.
릴리스 APK는 별도 서명 필요. `-PbehaviorQa=true`는 기존 테스트용 모의 인증이며 실제 서버용 빌드에는 사용하지 않는다.

## 5. API 계약 및 설계 선택

| HTTP | Endpoint | 성공 |
|---|---|---|
| GET | /health, /health/db | 200 |
| POST | /api/auth/signup | 201, 중복 409 |
| POST | /api/auth/login | 200 tokens, 오류 401 |
| POST | /api/auth/refresh | 200 tokens, 요청 ID 필수 |
| GET | /api/auth/me | 200 email, Authorization: Bearer accessToken |
| POST | /api/auth/logout | 204, refreshToken으로 현재 세션 폐기 |
| DELETE | /api/auth/account | 204, refreshToken으로 사용자 및 모든 세션 삭제 |
| POST | /api/events | 200 `{event_id,status: stored/duplicate}` |

가입/로그인 JSON `email,password`. 가입은 자동 로그인하지 않는다. 이메일은 소문자로 정규화하며 이메일 소유 검증 및 비밀번호 재설정은 이번 MVP에 포함하지 않았다. 비밀번호 8~128자(가입), Argon2id로 해싱.
access 15분, refresh 30일 고정 만료. 불투명 토큰은 서버 DB에 SHA-256 해시만 저장하고 앱은 기존 Keystore 암호화 저장을 사용한다. 토큰 갱신 요청 ID는 서버 요청 전 영속화하고 이전 토큰+동일 요청 ID의 재시도만 허용한다. 로그아웃은 현재 기기 세션만; 원격 오류에도 기존 정책대로 로컬 정리한다. 탈퇴는 실제 서버 삭제가 확인되어야 성공; 동일 refresh로 응답 유실을 재시도할 수 있도록 해시 영수증을 30일간 유효하게 한다(만료 행은 다음 탈퇴 시 정리). 별도의 비밀번호 재인증 UI는 추가하지 않았다. 앱 재실행 전 탈퇴 응답이 유실되어 서버에서 이미 삭제된 경우 세션 복원은 무효 처리될 수 있다.

행동 이벤트는 설치 ID 기준 익명/가명 분석을 선택했다. 로그인 전 app_open과 계정 전환을 잘못 연결하지 않도록 이메일·인증 토큰·회원 ID와 결합하지 않는다. 따라서 회원 탈퇴는 회원/세션을 삭제하고 설치 기준 행동 데이터는 자동 삭제하지 않는다. 연구 수집 전에 동의·보존·철회 정책을 결정해야 한다. 설치 ID도 개인정보가 될 수 있다. 공개 이벤트 API는 실제 행동 증명 수단이 아니며 외부 조작에 취약하다. 통제된 MVP 검증용이며 공개 장기 연구에는 별도 수집 인증/abuse 방지 및 개인정보 정책이 필요하다.

열람 및 설정 재저장 이벤트는 해당 DataStore 변경과 같은 트랜잭션에 저장한다. app_open/drop은 작은 AtomicFile 저널에 먼저 기록하고 WorkManager가 이를 영속 outbox로 옮긴다. 이벤트 ID/시각/스키마 버전을 최초 기록 시 정하고 ACK 후 제거한다. HTTP 오류는 백오프 재시도, 잘못된 이벤트(400/409/422)는 로컬 격리 보관. 주기 작업은 프로세스 종료/재실행 후 복구를 보조한다. 강제 종료 그 자체를 drop으로 감지하지 않으며 디스크 부족/OS 강제 중지/앱 삭제는 전송 보장 밖이다. app_open/drop의 동기 저널 기록은 짧은 디스크 I/O가 발생하며 실기기 성능 검증 필요. 큐/격리 파일의 장기 보존·용량 정책 및 연구용 통합 모니터링은 남아 있다.

## 6. 서버 저장 검증 및 CSV 추출

앱 새 설치 후 회원가입 → 로그인 → 팀/알림 설정 최초 저장 → 모의 경기 재생 → 첫 실제 시스템 알림 열람 → 두 번째 알림 열람 → 알림 설정 재저장 → 설정 토글 후 저장 없이 나가기.
같은 설치의 동일 프로세스 시나리오 예상: app_open=1, first_done=1, repeat_done=2, drop=1. 다른 실행 이벤트가 섞이지 않게 한다.

PostgreSQL 쿼리:

```sql
SELECT installation_id, name, COUNT(*) AS n
FROM behavior_events
GROUP BY installation_id, name
ORDER BY installation_id, name;
```

외부 PC에서 CSV 내보낼 때는 Render의 External Database URL을 로컬 환경변수 `DATABASE_URL`로 설정하고, DB 접근 허용 범위와 TLS 설정을 확인한다. `server/`에서 `python export_events.py --out events.csv` 실행. 선택적으로 `--installation-id UUID` 필터 사용. 결과 CSV는 Git에 올리지 않는다. DB 외부 접속이 필요 없다면 외부 접근을 제한한다.

## 7. 검증 범위 및 남은 작업

- 실제 실행: 백엔드 pytest 11개 통과 (임시 SQLite), Uvicorn HTTP smoke test 통과(health, DB, signup, login, me, events, delete).
- PostgreSQL 드라이버/인스턴스가 이 작업 환경에 없어 실제 PostgreSQL 통합 테스트 미실행. SQLAlchemy PostgreSQL 경로와 별도 테스트 옵션 제공.
- Android Gradle wrapper 실행 시 services.gradle.org DNS 실패로 Gradle 다운로드가 불가능했고 Android SDK도 없어 신규 Android 컴파일·단위 테스트·실기기 테스트 미실행. 신규 Kotlin 테스트 3개 포함. 기존 149개는 이전 인계 버전 결과이며 이번 변경 후 결과로 간주하지 않는다.
- Render 계정/GitHub 원격에 실제 배포하거나 push하지 않았다. 사용자가 적용/배포해야 한다.
- 연구 MVP 기준으로 API 계약을 새로 결정했다. 운영 전 이메일 인증/재설정, 보안 검토, 분산 rate limiting, DB migrations/백업, 수집 인증 및 동의·보존 정책, 기기 장애/오프라인·권한·회귀 테스트 필요.
