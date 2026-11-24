# ② 로그인 화면과 인증 연결 준비 (2026-11-24)

이 문서는 로그인 단독 단계(104개 테스트)의 이력이다. 후속 [회원가입 1단계](signup-report.md), [세션 저장·복원·로그아웃 2단계](session-report.md)에 최신 QA APK와 121개 테스트 및 실기기 결과를 기록했다.

이메일·비밀번호, 비밀번호 보기/숨기기, 로그인 버튼, 로딩과 실패·재시도 상태를 추가했다. 시안 ②의 입력 흐름을 기존 홈의 빨간 그라데이션·야구 마크·Material 3 스타일로 표현했다. 실제 인증 API는 아직 연결하지 않았다.

## 화면과 상태

- 앱 시작은 로그인 화면이다. 이메일 형식과 비어 있지 않은 비밀번호를 만족하면 제출 가능하다. 클라이언트 입력 검증은 서버의 인증을 대신하지 않는다.
- `LoginViewModel`이 `StateFlow<LoginState>`를 제공하고 `AuthenticationRepository.signIn(email, password)`을 호출한다. UI는 상태 표시와 입력 전달만 한다.
- 요청 중에는 입력·중복 제출을 막고 ‘로그인 중…’과 진행 표시를 보여준다. 인증 거절/연결 실패/서비스 미연결/알 수 없는 실패를 구분한다. 실패 시 입력을 유지해 다시 시도할 수 있다.
- 인증 성공 후 처음 설정을 완료하지 않은 사용자는 기존 **응원팀 선택 → 알림 선택 → 설정 저장 → 홈**으로 연결된다. 완료한 사용자는 저장된 값을 유지하고 홈으로 연결된다. 로그인만으로 설정 완료나 first_done을 기록하지 않는다.
- 로그인 화면이 표시되는 동안 기존 홈·설정·상세 UI는 렌더링하지 않는다. 시스템 알림으로 요청한 상세는 로그인 뒤 표시한다. 처음 설정도 미완료라면 기존처럼 최초 저장 후 열린다. 상세가 로그인 뒤 실제 표시되기 전에는 열람 확인 Effect가 실행되지 않는다.
- 일반 앱 실행의 기본 성공 목적지는 홈이며, 시스템 알림 진입 요청이 있으면 해당 상세 경로를 보존한다.
- 비밀번호와 인증 상태는 Activity의 ViewModel 메모리에만 존재한다. 성공 시 입력을 지운다. 회전에서는 ViewModel이 유지되지만 Activity 종료나 프로세스 종료 후에는 다시 로그인한다. DataStore·Bundle·행동 로그에 자격 증명을 저장하지 않는다.
- 로그인 상태 유지 체크박스는 세션 정책 확정 전까지 제공하지 않는다. 회원가입·로그아웃·계정 삭제나 인증 없는 둘러보기는 추가하지 않았다.

## QA 성공 경로 분리

| 빌드 | 인증 제공자 | 성공 가능 여부 |
| --- | --- | --- |
| 일반 debug | `src/productionAuth` | 현재는 NOT_CONFIGURED 실패만 반환 |
| 일반 release | `src/productionAuth` | 현재는 NOT_CONFIGURED 실패만 반환 |
| `-PbehaviorQa=true` debug | `src/behaviorQa` | 아래 QA 계정만 검증용 성공 |
| `-PbehaviorQa=true` release | `src/productionAuth` | QA 옵션이 있어도 검증 성공 코드 제외 |

성공을 만들어내는 QA 저장소 구현은 main 소스에 없다. Gradle debug 소스 선택으로 QA 빌드에만 포함한다. 릴리스는 항상 productionAuth를 사용한다. 일반 빌드는 실제 API가 연결되기 전까지 **로그인을 통과해 홈으로 들어갈 수 없다**.

QA 계정은 `qa@example.com` / `clutch-qa`이며 실계정이 아니다. 다른 값은 인증 거절을 반환한다. 600ms 지연으로 로딩을 검증할 수 있다. QA 화면에는 검증용임을 명시하고, 홈의 **QA 로그인 화면으로** 버튼으로 같은 프로세스에서 재진입할 수 있다. 이 버튼은 실제 로그아웃 구현이 아니며 일반 빌드에는 표시하지 않는다.

## 변경 파일과 서버 담당자 연결 지점

| 파일/함수 | 역할과 후속 연결 |
| --- | --- |
| `auth/AuthenticationRepository.kt` → `signIn` | 확정된 인증 방식·요청·응답·오류 코드를 구현할 인터페이스. Success는 서버가 인증을 확인하고 필요한 세션 처리를 마친 뒤에만 반환 |
| `src/productionAuth/.../AuthenticationProvider.kt` → `create` | 현재 UnavailableAuthenticationRepository를 실제 구현으로 교체할 주입 지점 |
| `src/behaviorQa/.../AuthenticationProvider.kt` | QA 전용 성공·실패. 실제 서버 인증으로 사용하지 않음 |
| `ui/LoginViewModel.kt` → `login` | 입력·로딩·재시도·성공 상태. 서버 오류를 AuthenticationFailure로 매핑 |
| `ui/LoginScreen.kt` | 로그인 UI와 비밀번호 마스킹. 인증 정책에 따라 이메일/비밀번호 입력 방식 재확인 |
| `ui/GameApp.kt` | 인증 전 화면 게이트. 공통 app_open Effect는 게이트 밖에 유지 |
| `MainActivity.kt` | 인증 저장소·ViewModel 주입 및 성공 콜백 연결 |
| `ui/GameViewModel.kt` → `onLoginCompleted` | 저장된 첫 이용 상태에 따른 분기와 알림 상세 목적지 보존 |
| `ui/HomeScreen.kt` | QA 전용 로그인 재진입 버튼 |
| `app/build.gradle.kts` | QA/일반/릴리스 인증 소스 분리 |

패키지 접두어는 `app/src/main/java/com/clutchalarm/app/`이며 다른 소스 경로는 표에 명시했다.

서버 담당자와 확정할 항목:

1. 이메일/비밀번호 인증인지 OAuth 등 다른 방식인지, URL·메서드·필수 필드·성공/오류 응답.
2. 계정 식별자와 토큰 종류·만료·갱신·취소, 안전한 세션 저장과 앱 재실행 시 유효성 확인 방식. 현재 메모리 인증을 영구 로그인으로 오해하지 않는다.
3. 인증 오류/네트워크 오류/서비스 장애를 UI 오류로 매핑하는 기준과 타임아웃·취소 처리.
4. 기기 로컬 응원팀·알림·첫 이용 완료를 계정에 연결할지 여부, 계정 전환 시 설정 처리. 이번에는 계정 정보로 덮어쓰거나 초기화하지 않는다.
5. 서버·네트워크 권한과 실제 통신 클라이언트는 계약 확정 후 추가한다. 이번 단계에는 endpoint, 토큰 저장, 수집 API, 새 로그인 행동 이벤트가 없다.

## 빌드와 단위 테스트

일반 `:app:assembleDebug :app:testDebugUnitTest`, QA debug 빌드·테스트, QA 옵션을 켠 release 빌드·테스트 모두 성공했다. 최종 일반 debug와 release 테스트는 각각 **104개 통과**, 실패·오류·스킵 0이며 새로 실행됐다. QA debug의 104개도 통과했다.

최초 일반 명령 `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`도 성공했다. 이후 debug/release를 함께 검증하던 기본 512MiB Gradle 데몬에서 GC thrashing이 발생해 해당 실행을 중단했다. 로컬 설정 파일을 바꾸지 않고 다음 옵션으로 각각 다시 빌드·테스트해 통과했다.

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 -PbehaviorQa=true :app:assembleRelease :app:testReleaseUnitTest
```

추가 테스트 8개는 잘못된 입력 차단, 로딩·중복 제출 차단·실패 후 재시도, 비밀번호 성공 후 삭제, 네트워크 실패, 일반 인증 미연결의 QA 자격 증명 거절, 일반 빌드 QA 복귀 차단, 로그인/홈 반복의 app_open 1회, 선택된 제공자 및 릴리스 QA 제공자 제외, 첫 이용 분기·설정 보존을 포함한다. 기존 판정·재생·설정·열람 회귀 테스트도 유지했다.

## 실기기

Samsung SM-N960N, Android 10/API 29, USB `device`. QA 앱만 업데이트했다. 기존 사용자 앱 `com.clutchalarm.app`의 설정 해시는 검증 전후 모두 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`로 동일하다.

| 구간 | 실제 확인 |
| --- | --- |
| 기존 설정 완료 QA 사용자 | 로그인 화면 표시 → 잘못된 비밀번호 실패 → 올바른 QA 입력으로 홈. 저장된 SSG 표시 |
| 같은 프로세스 QA 로그인/홈 반복 | QA 로그인 화면으로 → 다시 성공 → 홈. app_open **1건**, 다른 행동 이벤트 0건 |
| 기존 QA 설정 보존 | 반복 전후 설정 파일 해시 `49ce0c2bd954baa22d8f0bd70d026341b23fdda9c861568f6bc1a8ccfa86ea89` 동일 |
| 새 설치 QA 데이터 | QA만 초기화 → 로그인 → 성공 → 응원팀 선택 → SSG 선택 → 알림 선택 → 저장 → 홈. app_open **1건**, first_done/repeat_done/drop 0건 |
| 화면 시각 검토 | 로그인 입력·마스킹·오류·홈 스타일 헤더를 실제 스크린샷으로 확인 |

기존 사용자 구간은 PID 3989, 프로세스 `d721ee3f-86e8-4c3a-8cbb-0533acf449fb`이며 [원문 로그](login-returning-user-device-log.txt)에 app_open 1건이다. 새 설치 구간은 PID 5086, 프로세스 `e8c0b219-2ec1-4f83-aa8e-03f6b3d3d67e`이며 [별도 로그](login-first-use-device-log.txt)에 app_open 1건이다. 두 구간은 QA 데이터 초기화로 프로세스가 바뀌었으므로 합쳐 1건으로 세지 않는다.

QA 기존 데이터는 초기화 전에 별도 로컬 백업했다. 현재 QA 앱에는 새 설치 검증 후 SSG·기본 알림 설정이 남아 있다. 원래 사용자 앱은 초기화하거나 새 APK로 교체하지 않았다.

검증한 QA APK: `build/handoff/ClutchAlarm-Login-QA.apk`, SHA-256 `7528259F2024DB7F598446F076FF51EDEA7959B0EBD03E424C4567DD10AEF0A5`. 이전 `ClutchAlarm-QA-20261127.apk`와 구분한다.

실제 서버 인증·세션 유지·토큰 만료, 일반 APK의 실제 폰 로그인 거절, 로그인 중 회전/프로세스 복원, 인증 전 시스템 알림을 통한 콜드 진입은 이번 실기기에서 별도 재현하지 않았다. 서버 미연결 일반/릴리스의 성공 불가와 상태 로직은 단위 테스트로 확인했다. Android 13+ 알림 권한 거부 미검증 상태도 유지한다.
