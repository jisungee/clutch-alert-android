# 인증·마이페이지·행동 이벤트 통합 인계

기준: 2026-11-24, 마이페이지 3단계 작업 트리. 실제 서버 명세는 아직 없으며 URL·HTTP 메서드·헤더·요청/응답 DTO를 임의로 정하지 않았다. 아래 Kotlin 계약을 실제 서버 구현에 연결해야 한다. 이전 단계의 개별 보고서는 당시 검증 기록이며 현재 인계 기준은 이 문서다.

## 마이페이지 구현

`app/src/main/java/com/clutchalarm/app/ui/MyPageScreen.kt`는 인증 저장소가 반환한 현재 계정 이메일, 저장된 응원팀, 알림 사용 수를 표시한다. 로그인 입력란의 이메일을 현재 계정으로 추측하지 않는다. 프로필 로딩·오류·재조회가 있고, 확인된 이메일이 없으면 탈퇴를 비활성화한다. 이전 계정의 지연 응답이 다음 계정에 표시되지 않도록 요청 세대를 확인한다.

기존 빨간 상단 바·배경, 테마 글꼴, 둥근 흰 카드, 16/20/24dp 여백과 아이콘을 사용했다. 팀 변경·알림 설정은 기존 편집/저장 흐름을 재사용한다. 로그아웃 확인창을 유지하고 탈퇴 확인창·처리 중·실패/재시도·성공 안내를 추가했다. 탈퇴 실패 때는 인증과 편집 설정을 유지한다. 서버 삭제가 확인되면 이전 인증 화면을 제거하고 로컬 세션을 정리한 뒤 로그인으로 이동한다. 로컬 정리만 실패하면 삭제 API를 다시 호출하지 않고 정리만 재시도한다.

주요 변경: 위 화면, `ui/GameApp.kt`, `ui/LoginScreen.kt`, `ui/SessionScreen.kt`, `ui/LoginViewModel.kt`, `auth/AuthenticationRepository.kt`, `auth/SessionStore.kt`, `src/behaviorQa/.../auth/AuthenticationProvider.kt`, `src/test/.../ui/AccountFlowTest.kt`. 기존 상세 UI·재생·16개 알림 판정 코드는 변경하지 않았다.

## 서버 인증 연결 위치

아래 경로는 저장소 루트 기준이다. 공통 계약은 `app/src/main/java/com/clutchalarm/app/auth/AuthenticationRepository.kt`다. 실제 구현은 `app/src/productionAuth/java/com/clutchalarm/app/auth/AuthenticationProvider.kt#create`에서 공급하며 `MainActivity.kt`가 ViewModel에 주입한다. 일반 디버그와 모든 릴리스는 productionAuth를 사용한다. `-PbehaviorQa=true`의 디버그만 behaviorQa 구현을 사용한다.

| 기능 | 인터페이스 | 서버 연결 시 지켜야 할 결과 의미 |
|---|---|---|
| 회원가입 | `signUp(email, password): RegistrationResult` | 성공 후 자동 인증하지 않고 이메일이 채워진 로그인으로 복귀. 중복 이메일은 `EMAIL_IN_USE`, 연결 실패는 `NETWORK`. 서버 비밀번호 정책·이메일 인증 필요 여부 확정 필요 |
| 로그인 | `signIn(email, password): AuthenticationResult` | 서버 검증과 세션 저장이 성공한 경우에만 `Success`. 잘못된 자격 증명·네트워크·저장 실패를 구분 |
| 현재 계정 | `currentAccount(): AccountProfileResult` | 인증된 subject의 이메일 반환. 클라이언트 입력 이메일로 계정 권한을 결정하지 않음 |
| 세션 복원 | `restoreSession(): SessionRestoreResult` | 저장 없음은 `SignedOut`, 유효 확인은 `Authenticated`, 일시 오류는 `Retryable`로 기존 자격 정보 유지. 서버가 확정한 만료/폐기만 `Invalidated` |
| 갱신 | `refreshSession(): SessionRestoreResult` | 서버 갱신·토큰 회전 후 새 credential/expiry 원자 저장. 액세스 토큰 시각 만료만으로 갱신 가능 세션을 폐기하지 않음 |
| 로그아웃 | `signOut()` / `clearLocalSession()` | 원격 폐기 연결. ViewModel은 원격 요청을 최대 5초 기다리고 실패해도 로컬 세션을 제거. 로컬 제거 실패 시 차단/재시도 |
| 회원 탈퇴 | `deleteAccount(): AccountDeletionResult` | 현재 인증 credential의 계정을 삭제. 임의 이메일/계정 ID 인자를 받지 않음. 실제 삭제 확인 후에만 `Success`. 미연결 기본 구현은 `NOT_CONFIGURED`이며 성공을 흉내 내지 않음 |

세션 저장은 `auth/SessionStore.kt`, `AndroidSessionStore.kt`, `SessionCipher.kt`다. `no_backup/auth-session.bin`을 AndroidKeyStore AES-256/GCM(새 nonce, 패키지 AAD)으로 암호화하고 AtomicFile로 기록한다. 저장 값은 credential, expiry와 선택적 암호화 QA 이메일 캐시다. 비밀번호는 저장·로깅하지 않는다. 실제 서비스는 credential 종류/갱신 토큰 수명·회전·폐기 계약을 확정하고 필요한 최소 정보만 저장해야 한다. QA 이메일 캐시는 서버 프로필의 권한 근거가 아니다. 이전 QA 세션에 이메일 캐시가 없으면 재로그인 안내를 표시한다.

`ui/LoginViewModel.kt`가 가입·로그인·복원·프로필·로그아웃·탈퇴 상태를 관리한다. `GameApp.kt`는 세션 확인/재시도/파괴적 작업 중 화면을 차단하고 성공 후 첫 이용 완료 여부로 홈 또는 응원팀 선택에 연결한다. 인증 전 알림 Intent는 보관하지만 경기 상세가 실제 표시되기 전에는 열람으로 확정하지 않는다.

탈퇴는 로그아웃과 다르다. 삭제 실패나 응답 유실 때 로컬 로그아웃만으로 삭제 성공을 표시하면 안 된다. 서버는 삭제 멱등성, 타임아웃 후 완료 여부 조회/재시도, 전체 세션 폐기, 재인증 요구, 데이터 보존 범위를 확정해야 한다. 현재 실패 UI는 완료를 확인하지 못했다는 의미다. 성공 후 로컬 정리 재시도는 `retryDeletionCleanup()`이며 삭제 요청을 반복하지 않는다. 응원팀·알림 설정·설치 단위 알림 순번/열람 기록은 로그아웃·탈퇴 후에도 보존한다.

## QA 구현의 한계

QA 신규 계정 목록/비밀번호는 프로세스 메모리의 테스트 데이터다. 가입 후 로그인한 QA 세션은 암호화 저장되어 새 프로세스에서 복원되고 이메일도 표시된다. 갱신은 QA credential을 회전한다. QA 탈퇴는 세션을 먼저 만료 표시하고 현재 프로세스 계정을 제거한 뒤 로컬 세션을 지운다. 실제 서버 삭제가 아니다. 기본 fixture 계정은 프로세스마다 다시 생성되며, 신규 계정의 장기 중복/삭제 이력은 보장하지 않는다. 일반·릴리스는 이 성공 경로를 포함하지 않는다.

## 행동 이벤트 수집 연결

현재는 DEBUG `ClutchBehavior` 로그만 기록한다. 서버 요청·저장 결과는 없다. 공통 인터페이스는 `app/src/main/java/com/clutchalarm/app/behavior/BehaviorEvents.kt#BehaviorEventRecorder.record`, 구현은 `DebugBehaviorEvents.kt`다. 서버 구현은 공통 기록기에 연결해야 하며 UI/알림 판정에서 직접 HTTP 요청하지 않는다.

| 이벤트 | 현재 기준과 연결 위치 |
|---|---|
| `app_open` | 새 프로세스의 첫 화면 표시 1회. `MainActivity.kt` 주입 → `GameApp.kt` 루트 LifecycleResumeEffect → `DebugBehaviorEvents.appOpen`/`ProcessAppOpen`. 로그인·홈 이동/회전/복귀는 추가 기록하지 않음 |
| `first_done` | 최초 저장 뒤 실제 발송 성공한 설치 단위 1번 알림의 신규 열람이고 상세가 실제 로드·표시됐을 때 설치당 1회. `GameViewModel.confirmDetailVisible` → `data/DataStoreNotificationOpenRepository.kt#markOpened`. 2번을 먼저 열어도 1번 첫 열람 가능 |
| `repeat_done` | 2번 이후 알림 신규 열람은 `action=notification_open`, 최초 저장 이후 알림 설정 재저장 성공은 `action=settings_resave`. 열람 저장소와 `GameViewModel.saveSettings`에서 기록. 팀만 저장은 제외 |
| `drop` | 토글을 한 번 이상 조작하고 저장 없이 편집 흐름을 나갈 때 한 번. `GameViewModel.recordDrop`. 카테고리/세부 화면은 한 흐름, 미조작/저장 성공은 제외. 강제 프로세스 종료 감지를 보장하지 않음 |

`data/DataStoreNotificationPublisher.kt#publish`가 실제 발송 성공 순번을 설치 단위로 보존한다. 권한 등으로 미발송된 알림은 순번을 소비하지 않는다. 과거 PROCESS 범위 순번은 설치 1번으로 해석하지 않는다. 목록 직접 진입·동일 ID 재전달·회전은 새 열람이 아니다. 새 인증/계정 전환은 설치 이벤트 기준을 초기화하지 않는다.

현행 `BehaviorEvent` 값: `name`, 선택적 `action`, `notificationId`, `notificationSequence`, `installationId`, `gameId`, `flowId`. JSON 필드명도 이 camelCase다. process UUID는 디버그 로그 접두사이며 이벤트 ID가 아니다. **현재 `event_id`, 영속 outbox, HTTP, 재시도 큐는 없다.** API 확정 후 최초 기록 때 event_id를 한 번 생성·영속화하고 재전송에도 유지해야 한다. 서버의 멱등 키/ACK/중복 집계 계약이 필요하다. 열람 소비 표시와 전송 대기 저장의 원자성/복구도 함께 설계해야 한다. 로그 함수 교체만으로 전달 보장이 생기지 않는다.

수집 명세에서 endpoint, 인증 전 익명 app_open 처리, 설치와 계정 연결, 시간/스키마 버전, 재시도·ACK·보존 기간을 확정해야 한다. 이메일·비밀번호·credential을 이벤트에 추가하지 않는다. 경기·응원팀·알림 설정은 현재 로컬이며 별도 서버 API를 요구하지 않는다.

## 빌드·회귀 검증

다음 세 구성을 실행했고 각각 127개 테스트가 새로 실행되어 실패·오류·스킵 없이 통과했다. Gradle 메모리 부족을 피하기 위해 공통 옵션 `-Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" --max-workers=1`을 사용했다.

```powershell
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat -PbehaviorQa=true :app:assembleRelease :app:testReleaseUnitTest
```

새 `AccountFlowTest` 6개는 프로필 재시도/이전 계정 응답 차단, 탈퇴 취소·중복 요청·실패·재시도, 삭제 확인 후 로컬 정리만 재시도, 미연결 탈퇴 실패, QA 복원·갱신·탈퇴 및 일반 구현 분리를 검증한다. 기존 4개 행동 이벤트, 16개 알림 정책, 기존 7개+새 JSON 1개 로딩/재생, 화면 이동·배속·세션 테스트도 포함된다. 실제 서버 통신과 Android 13 알림 권한 거부는 이번 Android 10 실기기로 검증할 수 없다.

## QA 실기기 결과·재현

Samsung SM-N960N / Android 10(API 29), USB `28c5d648ad3f7ece`가 `device`인 상태에서 별도 `com.clutchalarm.app.behaviorqa` 앱으로 검증했다. QA 데이터만 백업 후 초기화했으며 기존 `com.clutchalarm.app`은 초기화하거나 업데이트하지 않았다. 기존 앱의 설정 파일 SHA-256은 전후 모두 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`였다.

| 실제 실행 순서 | 확인 결과 |
|---|---|
| 새 QA 가입 → 이메일이 채워진 로그인 → 첫 이용 SSG/기본 15개 저장 | 홈 진입 성공. 가입만으로 자동 인증하지 않음 |
| HOME → `am kill` → `pidof` 공백 확인 → 아이콘 새 실행 | PID 18841 종료, PID 21789에서 자동 로그인·홈 진입. 마이페이지에 새로 가입한 이메일 표시 |
| 마이 → 응원팀 KIA 저장 | 마이페이지에 KIA 즉시 표시. 팀만 저장으로 repeat_done 추가 없음 |
| 마이 → 알림 설정 → 경기 시작 토글 조작 → 세부/카테고리 뒤로 → 마이 | 기존 15개 유지, drop 1건 |
| 알림 설정 재진입 → 홈런 OFF → 저장 | 마이페이지 14/16개 즉시 표시, settings_resave 1건 |
| 경기 목록 → 일반 모의 경기 → 임시 부산 팀 → 재생·일시정지 → 알림창 | 경기 시작/출루가 설치 순번 1/2로 실제 발송. 저장된 KIA는 유지 |
| 시스템 경기 시작 알림 → 상세, 시스템 출루 알림 → 상세 | NEW 열람 각 1건, first_done 1건 및 notification_open 1건. 같은 경기 일시정지 상태로 상세 복귀 |
| 마이 → 로그아웃 취소 → 다시 확정 → 프로세스 종료 → 재실행 | 취소 시 유지, 확정 시 로그인. PID 21789 종료 확인 후 PID 22845에서 로그인 화면 |
| 별도 신규 QA 계정 가입·로그인 → 마이 → 탈퇴 취소 → 다시 확정 | 새 계정 이메일 표시, 취소 시 유지, 확정 후 ‘회원 탈퇴가 완료됐어요.’ 로그인 화면. 로그에 CONFIRMED/CLEARED 각각 1건 |
| 탈퇴 후 프로세스 종료 → 재실행 | PID 22845 종료 확인 후 PID 23789에서 SIGNED_OUT·로그인 화면 |
| 기본 QA 계정으로 로그인 → 마이 → LG 선택 후 미저장 뒤로 | 현재 계정 이메일로 바뀜. KIA와 14/16 유지. 로그아웃/탈퇴로 기존 로컬 설정이 지워지지 않음 |

PID 21789의 행동 로그는 **app_open 1, first_done 1, repeat_done 2(settings_resave 1 + notification_open 1), drop 1**이다. 이번 전체 검증에서 프로세스 4개(18841/21789/22845/23789)의 app_open은 각각 1건, 합계 4건이다. 인증 화면·마이·설정·상세 이동, 로그아웃·탈퇴만으로 추가 열람/완료 이벤트가 생기지 않았다. 원시 증거: [이번 실기기 로그](mypage3-device-log.txt).

동일 ID 재전달·회전·목록 직접 진입, 역전/지는 중 숨기기·배속·8개 모의 경기 회귀는 기존 단위 테스트를 다시 실행했다. 이번 폰에서는 일반 경기 재생·일시정지와 서로 다른 시스템 알림 2건을 직접 열었다. 모든 8개 경기를 폰에서 끝까지 재생하거나 회전·동일 Intent를 다시 주입한 것은 아니다. 탈퇴 서버 오류/로컬 정리 실패의 실기기 강제 주입과 실제 서버 삭제·통신은 미검증이며 단위 테스트로 대체한 범위를 구분한다. 탈퇴 직후 동일 자격 증명 재로그인은 UI 자동 입력 좌표 문제로 제출되지 않아 실기기 성공/실패 판정에 포함하지 않았다(계정 제거는 단위 테스트로 확인).

QA는 계정 목록/비밀번호를 프로세스 메모리에만 두며 암호화된 세션으로 새 프로세스를 복원한다. 커스텀 QA 계정 삭제·세션 무효화를 검증할 수 있으나 실제 계정 삭제 서비스가 아니다. 기본 QA fixture는 프로세스마다 다시 생기고 서버의 영구 삭제/전체 기기 토큰 폐기를 보장하지 않는다.

실제로 설치하여 검증한 APK: `build/handoff/ClutchAlarm-MyPage-QA.apk` (Git 제외), SHA-256 `4ad7ebca1d60632086db548916859dd3cc42d63b172cd393ed0a3977c355c457`. 화면 캡처는 작업용 시각화 폴더의 `mypage-stage3.png`에 보관했다. 변경은 기존 인증 1·2단계 미커밋 작업을 보존한 작업 트리이며 이번 단계에서 새 커밋을 생성하지 않았다.
