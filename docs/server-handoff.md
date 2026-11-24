# 클러치알림 통합 서버 인계

기준일 2026-11-24, 목표 인계일 11/27. 이 문서와 README를 현재 구현 기준으로 사용한다. 옛 기능 보고서는 당시 증거이며 현재 실행 방법을 덮어쓰지 않는다. 최종 클라이언트 버전은 **MVP-20261127**, 브랜치는 `codex/handoff-20261127`이다. 이 문서를 포함한 확정 HEAD를 별도 체크아웃해 빌드하며 SHA·최종 APK·해시는 [최종 버전 안내](mvp-20261127.md)의 검증 영수증으로 식별한다.

## 현재 경계

- 앱은 가입·로그인·세션·마이페이지·설정·모의 경기·로컬 알림 UI를 갖춘다. 실제 인증/수집 API 명세는 미확정이고 HTTP 구현은 없다. 일반·릴리스는 `NOT_CONFIGURED`를 반환하며 성공을 가장하지 않는다.
- 경기 8개, 응원팀·알림·첫 이용 상태, 발송 순번·열람은 로컬이다. 서버 경기/설정 동기화·FCM 토큰 등록은 이번 MVP 범위가 아니다.
- QA 계정 목록과 비밀번호는 프로세스 메모리에만 있다. 로그인한 QA 세션은 Keystore 암호화 저장으로 새 프로세스 복원이 가능하지만 서버 세션 유효성 검증은 아니다. 기본 fixture는 프로세스마다 다시 생기므로 영구 계정 삭제를 검증할 수 없다.
- 최종 디자인은 10개 구단 공식 로고·전역 테마·홈 그라데이션이다. 홈 외 넓은 배경은 중립색/옅은 팀색이다. 최신 시각 결과는 [최종 홈 검증](home-gradients-final.md).

## 인증 연결 위치

공통 경로는 `app/src/main/java/com/clutchalarm/app/`다.

- 계약: `auth/AuthenticationRepository.kt`.
- 실제 서버 구현 공급: `app/src/productionAuth/java/com/clutchalarm/app/auth/AuthenticationProvider.kt#create`.
- 주입: `MainActivity.kt` → `LoginViewModel`.
- UI 상태/호출: `ui/LoginViewModel.kt`, `GameApp.kt`, `LoginScreen.kt`, `SignUpScreen.kt`, `SessionScreen.kt`, `MyPageScreen.kt`.
- 세션 계약/저장: `auth/SessionStore.kt`, `AndroidSessionStore.kt`, `SessionCipher.kt`.

| 기능 | 계약·호출 | 지켜야 할 의미 |
|---|---|---|
| 가입 | `signUp(email,password)` / `LoginViewModel.signUp` | 성공 후 이메일 채운 로그인으로 복귀, 자동 인증 안 함. 중복 `EMAIL_IN_USE`, 연결 실패 `NETWORK` 구분 |
| 로그인 | `signIn(email,password)` / `login` | 서버 검증 및 로컬 세션 저장 성공 후에만 `Success`. 자격 증명/연결/저장 실패 구분 |
| 현재 계정 | `currentAccount()` / `loadAccount` | 인증 subject의 이메일 반환. 입력 이메일을 계정 권한 근거로 사용하지 않음 |
| 세션 복원 | `restoreSession()` / `restoreSession` | 저장 없음 `SignedOut`, 유효 확인 `Authenticated`, 일시 오류 `Retryable`, 서버 확정 만료/폐기 `Invalidated` |
| 갱신 | `refreshSession()` / `restoreSession(refresh=true)` | 토큰 회전·expiry 원자 저장. 자동 HTTP 401 갱신/재시도는 현재 미구현이며 서버 구현에서 연결 필요 |
| 로그아웃 | `signOut()`·`clearLocalSession()` / `confirmLogout` | 원격 폐기 계약 연결. 현재 VM은 최대 5초 대기 후 원격 실패여도 로컬 정리. 로컬 제거 실패는 차단·재시도 |
| 회원 탈퇴 | `deleteAccount()` / `confirmDeletion` | 현재 credential 계정의 실제 삭제 확인 후에만 Success. 실패 시 인증 유지. 성공 후 정리 실패는 `retryDeletionCleanup`으로 로컬 정리만 재시도 |

`SessionRestoreResult`는 `SessionStore.kt`, 가입·인증·계정 결과 enum은 `AuthenticationRepository.kt`에 있다. `StoredSession`은 credential·expiry·선택적 암호화 계정 이메일 캐시를 갖고 비밀번호를 저장하지 않는다. `no_backup/auth-session.bin`은 AndroidKeyStore AES-256/GCM·새 nonce·패키지 AAD·AtomicFile로 보호한다. 실제 access/refresh token 구성에 맞춰 최소 정보 계약을 확정해야 한다. 캐시 이메일은 서버 권한 판단을 대체하지 않는다.

앱 시작 시 세션 확인 로딩을 보여주고, 성공하면 첫 이용 완료자는 홈·미완료자는 응원팀 선택으로 이동한다. 인증 전 알림 진입은 대기시키고 인증/설정 후 해당 상세를 연다. 상세가 실제 표시되기 전에는 알림 열람 이벤트를 확정하지 않는다.

로그아웃·탈퇴는 계정 세션만 정리한다. 응원팀·알림 설정·설치 단위 순번·열람/first_done 기록은 유지한다. 삭제 요청 응답 유실을 로컬 로그아웃으로 성공 처리하면 안 된다.

## 행동 이벤트 연결

계약은 `behavior/BehaviorEvents.kt#BehaviorEventRecorder.record(BehaviorEvent)`다. `MainActivity.kt`가 GameViewModel과 열람 저장소에 주입한다. 현재 구현 `DebugBehaviorEvents.kt`는 DEBUG 로그만 출력하고 릴리스는 전송/기록하지 않는다. 서버 sink를 연결할 때도 공통 인터페이스와 아래 발생 조건을 보존한다.

| 이벤트 | 조건 | 코드 위치 |
|---|---|---|
| app_open | 새 프로세스에서 앱 화면 최초 표시 1회. 아이콘·알림 새 실행 포함, 회전·복귀·로그인 이동 제외 | `GameApp` 루트 LifecycleResumeEffect → `ProcessAppOpen.onScreenVisible` |
| first_done | 최초 저장 이후 성공 발송한 설치 순번1 알림의 신규 열람·상세 실제 표시 시 설치당 1회. 2번 먼저 열어도 기회 유지 | `GameViewModel.confirmDetailVisible` → `DataStoreNotificationOpenRepository.markOpened` |
| repeat_done | 설치 순번2 이상 신규 열람 `action=notification_open`, 최초 저장 후 알림 설정 재저장 성공 `action=settings_resave` | 열람 저장소·`GameViewModel.saveSettings`. 팀만 저장 제외 |
| drop | 알림 토글을 한 번 이상 조작한 편집 흐름을 저장 없이 벗어날 때 흐름당 1회 | `GameViewModel.recordDrop`. 카테고리/세부 이동은 같은 흐름, 미조작·저장 성공 제외 |

`data/DataStoreNotificationPublisher.kt#publish`가 실제 성공 발송을 설치 단위 1번부터 센다. 앱 재실행·경기 변경·재생 초기화에도 이어진다. 권한 거부 등 미발송은 순번을 소비하지 않고, 예전 PROCESS 범위 순번을 설치 순번1로 간주하지 않는다. `notification/NotificationEntryIntent.kt`가 gameId·POST_ID·순번·scope를 전달한다. 목록 직접 진입·중복 ID·회전은 새 알림 열람이 아니며, 같은 경기 알림 복귀는 현재 재생 상태를 유지한다.

`BehaviorEvent`의 현행 필드는 `name`, 선택적 `action`, `notificationId`, `notificationSequence`, `installationId`, `gameId`, `flowId`다. JSON은 camelCase이며 서버 DTO가 확정된 것은 아니다. 로그 접두사의 process UUID와 알림 POST_ID, 경기 JSON의 event_id는 **행동 이벤트 전송용 event_id가 아니다**.

현재 미구현:

- 수집 endpoint·HTTP DTO·인증 헤더·실제 서버 저장 대조. 현재 Manifest에 INTERNET 권한도 없으므로 네트워크 연결 단계에서 추가해야 한다.
- 행동 이벤트 event_id·발생 시각·스키마 버전·영속 outbox·ACK·재시도·백오프·오프라인 복구·서버 멱등 집계.
- 열람 소비 표시/설정 저장과 전송 대기 저장의 원자성 및 프로세스 종료 복구. 현재 열람 저장이 먼저 완료되고 recorder가 호출되므로 단순 HTTP 치환으로 전달 보장을 얻을 수 없다.

최초 이벤트 기록 시 ID를 한 번 생성·영속화하고 재전송에도 같은 ID를 유지해야 한다. UI/알림 판정에서 직접 네트워크를 호출하지 않고 영속 저장 후 별도 전송기를 둔다. 네트워크 장애가 설정 저장·재생을 막지 않게 해야 한다. 이메일·비밀번호·credential을 이벤트에 추가하지 않는다. 강제 프로세스 종료 시 drop 감지는 보장하지 않는다.

## QA·일반·릴리스 경계와 로그

`app/build.gradle.kts`의 소스셋 선택:

| 구성 | 인증·BuildUiContent | 진단 UI | 진단 로그 |
|---|---|---|---|
| 일반 debug | `src/productionAuth/java` | OFF | DEBUG에만 |
| `-PbehaviorQa=true` debug | `src/behaviorQa/java` | ON | DEBUG에만 |
| 모든 release | `src/productionAuth/java` | OFF | 출력 안 함 |

QA 이메일·비밀번호·가입/탈퇴/홈 안내는 behaviorQa의 `auth/AuthenticationProvider.kt`, `ui/BuildUiContent.kt`에만 있다. productionAuth의 같은 UI 객체는 운영 문구/빈 힌트만 제공한다. 모든 릴리스는 QA 인증 성공 구현을 포함하지 않는다. QA 패키지 suffix·버전 suffix·앱 이름도 debug에서만 적용하며 QA 플래그를 켠 release조차 운영 identity를 유지한다. 실제 배포 명령에는 혼동을 피하도록 플래그를 사용하지 않는다.

`ClutchSession`, `ClutchNotificationOpen`, `ClutchNotificationPost`, `ClutchNotifications`의 진단 및 예외 상세는 DEBUG에서만 출력한다. 현재 릴리스에 별도 원격 오류 수집은 없다. 오류 결과·재시도 UI와 `BehaviorEventRecorder` 호출 조건은 유지한다. 비밀번호·credential은 어느 빌드에서도 로그에 남기지 않는다. 문서의 fixture는 실제 운영 계정이 아니다.

## 최신 QA 재현 절차

JDK17·SDK36·ADB 필요. PowerShell 저장소 루트 기준이다. 새 QA 빌드 직후 복사하며 일반 빌드로 출력이 덮어써지지 않게 한다.

```powershell
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
New-Item -ItemType Directory -Force build/handoff | Out-Null
Copy-Item app/build/outputs/apk/debug/app-debug.apk build/handoff/ClutchAlarm-Handoff-QA.apk
python tools/audit_apk.py build/handoff/ClutchAlarm-Handoff-QA.apk --expect-qa
adb devices -l
adb install -r build/handoff/ClutchAlarm-Handoff-QA.apk
adb shell am start -n com.clutchalarm.app.behaviorqa/com.clutchalarm.app.MainActivity
adb shell pidof com.clutchalarm.app.behaviorqa
```

새 설치/최초 순번 시험은 **데이터 초기화 허용된 QA 앱이나 별도 테스트 기기**에서만 한다. 일반 앱은 초기화하지 않는다. 기존 QA 설정을 보존해야 하면 초기화하지 말고 새 테스트 환경을 준비한다. 이미 사용한 설치에서는 first_done을 다시 만들 수 없다. 초기화가 허용된 QA 환경의 명령은 `adb shell pm clear com.clutchalarm.app.behaviorqa`이며 앱 시작 전에 실행한다. 로그는 이번 PID만 필터링하고 과거 프로세스와 합산하지 않는다.

```powershell
adb logcat --pid <QA_PID> -v threadtime 'ClutchBehavior:D' 'ClutchSession:I' 'ClutchNotificationPost:D' 'ClutchNotificationOpen:I' '*:S'
```

1. 새 설치의 로그인 화면에서 **회원가입** → 새 이메일·8자 이상 비밀번호·확인 → 가입. 이메일 채워진 로그인으로 복귀한다. 또는 기본 `qa@example.com` / `clutch-qa`로 로그인한다.
2. 첫 이용 미완료자는 **SSG 랜더스 → 다음 → 알림 선택 → 선택한 설정 저장하기 → 홈**. 완료자는 바로 홈이다. 최초 저장만으로 first_done은 생기지 않는다.
3. **모의 경기 보러가기 → 모의 경기 · 일반** → 해당 경기에 저장 팀이 없으므로 임시 **부산 웨이브스** 선택. 저장 응원팀은 유지한다.
4. 시스템 알림 ON 및 API33+ 권한 허용 확인. **1x → 모의 경기 재생하기**. 첫 알림이 뜨면 다음 발송 전 일시정지한다. 설치 순번1을 로그로 확인한다.
5. 실제 시스템 알림창에서 해당 알림을 누른다. 상세 실제 표시·NEW·first_done 1건 확인. 목록 선택이나 앱 내 본문 보기로 대체하지 않는다.
6. 재개 후 순번2 발송·일시정지 → 실제 알림 탭. repeat_done(notification_open) 1건, 같은 경기의 재생 위치·배속 유지 확인.
7. 뒤로 → 마이 → **알림 설정** → 저장 성공. repeat_done(settings_resave) 1건. 다시 들어가 토글 조작 후 저장 없이 마이까지 나가 drop 1건. 세부→카테고리만 이동하면 아직 drop이 아니다.
8. 같은 프로세스 합계 **app_open 1 / first_done 1 / repeat_done 2 / drop 1**인지 확인한다. 기본 구독을 꺼두지 않았는지도 확인한다.
9. 같은 알림 ID 재전달·회전·목록 직접 상세는 추가 열람 0건. 새 설치 시험을 별도로 만들어 2번→1번 순으로 열어도 각각 repeat_done/first_done 한 번인지 확인한다.
10. HOME 후 `adb shell am kill com.clutchalarm.app.behaviorqa`, `pidof` 공백을 확인한 뒤 재실행해 자동 로그인·홈·다음 성공 순번 연속성을 확인한다. PID가 남으면 종료로 인정하지 않는다. 필요 시 QA `am force-stop`을 사용하되 이는 최근 앱 닫기와 다른 조건으로 보고한다. 강제중지는 기존 알림 보존 시험을 대신하지 않는다.
11. 마이에서 팀/알림 저장·미저장 이탈, 로그아웃 취소/확정 → 새 프로세스 로그인 화면을 확인한다. 탈퇴는 별도 QA 계정으로 취소·확정 UI를 시험한다. 실제 삭제·전체 세션 폐기는 서버 연결 후 검증한다.

QA 신규 계정으로 로그인한 세션은 복원되지만 계정 목록은 새 프로세스에서 초기화된다. 로그아웃 후 그 계정으로 다시 로그인하려면 같은 프로세스에서 다시 가입하거나 기본 fixture를 사용한다. 이는 실제 계정 서비스 동작이 아니다.

## 빌드·APK 검증과 증거

[이번 정리 결과](release-readiness.md)에 실제 명령·구성별 테스트 수·APK 해시·문자열 검사를 기록한다. 현재 누적 테스트는 구성별 134개이며 테스트가 새로 실행됐는지 결과와 구분한다. 릴리스는 unsigned이며 배포 키·스토어 배포 검증은 포함하지 않는다.

과거 실제 실기기 증거:

- [마이페이지 3단계 로그](mypage3-device-log.txt): 가입·복원·설정·실제 시스템 알림과 한 프로세스 1/1/2/1 확인.
- [행동 이벤트 보고서](behavior-events-report.md): 2번→1번 및 동일 ID 중복 방지.
- [세션 보고서](session-report.md), [알림 진입 보고서](notification-entry-report.md): 인증 전 알림 대기·새 실행·재생 유지.
- [최종 홈](home-gradients-final.md): 10팀 기본/1.3배 20장, 승인한 4팀 보존. 이번 QA 분리는 디자인·로고·팔레트를 변경하지 않는다.

과거 인계 APK·커밋은 현재 기준이 아니다. 최신 전체 흐름은 [최종 통합 QA](final-qa-report.md)에서 확인했다. API36 에뮬레이터의 1/1/2/1·복원·계정·권한 허용/거부와 API29 실기기의 업데이트 보존 결과를 포함한다. 실제 서버 검증을 대신하지 않는다.

## 서버 담당자 확인 질문·남은 검증

1. 인증 endpoint/HTTP 메서드/DTO/헤더·에러 코드, 이메일 인증·비밀번호 정책은 무엇인가?
2. access/refresh token 구성·수명·회전·폐기·동시 갱신·오프라인 복원 정책은 무엇인가?
3. 로그아웃은 현재 기기/전체 세션 중 어느 범위인가? 탈퇴 재인증·멱등성·응답 유실 후 조회·데이터 보존 범위는 무엇인가?
4. 익명 app_open과 로그인 계정 연결, 설치 기준 ID, 이벤트 시각·스키마·event_id·ACK·중복 집계·보존/재시도 정책은 무엇인가?
5. 실제 원본 경기 JSON의 초기 상태·모든 플레이 연속성·선수 ID·판독 정정 대상·delay 단위·B/S 제공은 어떻게 보장하는가?

Android16/API36 에뮬레이터에서 Android12+ 시스템 스플래시·Android13+ 권한 허용/거부/재허용을 실제 검증했다. 정확한 API31/33 기기·다른 제조사·TalkBack은 미검증이며 Android10 알림 OFF로 이를 대체하지 않는다. 실제 API·오프라인 전송·중복 집계·서버 삭제, 디스크 부족/Keystore 장애의 실기기 주입, 서버 연결 후 전체 회귀는 남아 있다.

[전달 파일 분류](handoff-files.md)·[정확한 목록](handoff-files.txt)을 기준으로 소스와 원본을 전달하고, hprof·로컬 SDK 경로·키·캐시·구형 APK·기기 데이터는 제외한다. 확정 커밋의 별도 체크아웃에서 일반·QA·릴리스 및 전체 테스트를 재실행하고 검증 영수증을 APK와 함께 전달한다.
