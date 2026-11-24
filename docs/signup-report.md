# 회원가입 1단계 구현·검증 (2026-11-24)

이 문서는 회원가입 1단계(111개 테스트)의 이력이다. 후속 세션 저장·복원·로그아웃 구현과 최신 QA APK, 121개 테스트 및 실제 재실행 결과는 [세션 2단계 보고서](session-report.md)를 참고한다.

이번 단계는 **회원가입 화면 → 가입 완료 → 이메일이 채워진 로그인 → 명시적 로그인**이다. 로그인 유지·세션 복원·마이페이지 로그아웃은 구현하지 않았다. 기존 로그인 성공 후 최초 설정 분기, 로컬 응원팀·알림 설정, 알림 판정과 행동 이벤트 기준을 유지했다.

## 구현

- 로그인에 **회원가입** 버튼을 추가했다. 회원가입은 이메일·비밀번호·비밀번호 확인과 비밀번호 보기/숨기기를 제공한다.
- 이메일 형식, 공백만인 비밀번호 거절, 비밀번호 **8자 이상**, 확인 일치를 검증한다. 8자는 화면 검증용 잠정 기준이며 서버 명세 확정 시 일치시켜야 한다. 비밀번호 자체를 trim하거나 변형하지 않는다.
- 요청 중에는 입력·중복 제출·뒤로 이동을 막고 ‘가입 중…’을 표시한다. 중복 이메일·네트워크·서비스 미연결·기타 오류를 구분한다. 실패 시 편집값을 유지하고 수정·재시도할 수 있다.
- 성공하면 가입에 사용한 이메일(trim 적용)만 로그인에 채우고 두 비밀번호와 가입 편집 상태를 비운다. 성공 안내가 표시되며 **자동 로그인하거나 홈으로 이동하지 않는다**. 사용자가 로그인해야 기존 성공 콜백이 실행된다.
- 저장 없이 돌아가면 가입 입력을 버린다. 가입 자체로 first_done/repeat_done/drop이나 설정 저장을 발생시키지 않는다.

## 일반·QA·릴리스의 경계

기존 `AuthenticationRepository.signIn`과 production 제공자 주입 위치는 유지하고, **`signUp(email, password)`**를 같은 인터페이스에 추가했다. 기본 구현은 `RegistrationFailure.NOT_CONFIGURED`를 반환하므로 기존 로그인 저장소를 연결했다고 가입까지 성공으로 간주하지 않는다. 확인 비밀번호는 로컬 검증용이고 저장소에 전달하지 않는다.

| 빌드 | 가입/인증 |
| --- | --- |
| 일반 debug | 기존 production 제공자. 가입·로그인 모두 미연결 실패, 인증 우회 없음 |
| QA debug (`-PbehaviorQa=true`) | QA 전용 저장소에 가입 후 그 계정으로 로그인 가능 |
| release (QA 옵션 포함) | production 제공자. QA 가입·로그인 성공 코드 제외 |

QA 저장소는 **프로세스 단위 메모리 계정 목록**을 공유한다. 기존 `qa@example.com` / `clutch-qa`를 기본 계정으로 유지하며, 새 계정을 같은 프로세스에서 추가할 수 있다. QA 이메일 키는 trim 후 대소문자를 구분하지 않아 중복을 거절한다. 중복 가입으로 기존 비밀번호를 덮어쓰지 않는다. 이 정규화는 QA 정책이며 서버 정책을 확정한 것이 아니다.

가입 계정·비밀번호·로그인 성공 상태를 파일/DataStore에 저장하지 않는다. **QA 프로세스 종료 후 새 가입 계정은 사라진다.** 로그인 유지와 영구 계정 저장은 다음 단계다. 이 제한을 QA 회원가입 화면에도 표시했다.

## 변경 파일

Java/Kotlin 경로는 `app/src/main/java/com/clutchalarm/app/` 기준이며 다른 경로는 별도 표기했다.

| 파일 | 변경 |
| --- | --- |
| `auth/AuthenticationRepository.kt` | signUp 계약, RegistrationResult·RegistrationFailure 추가. signIn 유지 |
| `app/src/behaviorQa/java/com/clutchalarm/app/auth/AuthenticationProvider.kt` | 프로세스 공유 QA 계정 저장소, 가입·중복 거절·신규 계정 로그인 |
| `ui/SignUpState.kt` (신규) | 가입 편집·검증·로딩·실패 상태 |
| `ui/SignUpScreen.kt` (신규) | 가입 폼과 오류·재시도·복귀 버튼 |
| `ui/LoginViewModel.kt` | 가입 진입·취소·제출, 성공 후 이메일 채우기와 비밀번호 삭제 |
| `ui/LoginScreen.kt` | 회원가입 버튼, 가입 성공 안내 |
| `ui/GameApp.kt` | 로그인/회원가입 화면 이동과 시스템 뒤로 처리 |
| `app/src/test/java/com/clutchalarm/app/ui/SignUpFlowTest.kt` (신규) | 가입·중복·재시도·로그인 연결과 일반/릴리스 차단 테스트 7개 |
| `README.md`, `docs/login-report.md` | 현재 실행 흐름과 이 단계 보고서 연결 |

## 빌드·단위 테스트

일반 debug, QA debug, QA 옵션을 켠 release 빌드 모두 **BUILD SUCCESSFUL**. 각 테스트 묶음 **111개 통과**(실패·오류·스킵 0), 테스트는 새로 실행됐다. 기존 로그인·최초 설정 분기·16개 알림 판정·로컬 경기·열람 회귀 테스트를 포함한다.

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest
```

추가 테스트는 잘못된 입력의 요청 차단, 가입 성공 시 이메일 채움·비밀번호 삭제·자동 로그인 방지, 로딩 중 중복/뒤로 차단, 중복 오류 후 입력 유지·수정 재시도, 네트워크 실패, 취소 시 비밀번호 삭제, 일반 가입 미연결 실패, QA 신규 계정 로그인·중복 덮어쓰기 방지·기존 로그인, 릴리스 QA 제공자 제외를 확인한다.

## 실기기 확인

Samsung SM-N960N, Android 10/API 29, USB `device`. QA 앱만 백업 후 업데이트했고 **QA 데이터도 초기화하지 않았다**. 현재 로컬 응원팀은 KIA다.

| 실제 조작 | 결과 |
| --- | --- |
| 로그인 → 회원가입 | 폼 표시. 이메일·두 비밀번호·보기/숨기기·가입/복귀 버튼 확인 |
| 확인 비밀번호 불일치 입력 | ‘비밀번호가 일치하지 않습니다’ 오류 표시 |
| 일치하도록 수정 후 `signup-stage1@example.com` 가입 | 성공 안내와 해당 이메일이 채워진 로그인으로 복귀. 비밀번호 빈 값, 자동 로그인 없음 |
| 이메일은 건드리지 않고 비밀번호만 입력해 로그인 | **가입한 계정으로 홈 진입**, 기존 KIA 표시 |
| 같은 이메일로 다시 가입 | **중복 이메일 오류**, 세 입력값 유지 |
| 오류 상태에서 이메일만 `signup-retry@example.com`으로 수정·재시도 | **가입 성공**, 새 이메일이 채워진 로그인으로 복귀 |
| 재시도 계정으로 로그인 | 정상 홈 진입 |
| QA 로그인 화면 복귀 → 기존 `qa@example.com` / `clutch-qa` 로그인 | **기존 로그인 흐름 정상**, KIA 유지 |

위 두 검증용 신규 계정은 `Stage1-qa9`를 사용했다. 실계정이 아니다. 같은 프로세스에서 재현할 때는 중복되지 않는 이메일을 사용하거나 QA 앱만 종료 후 다시 실행하여 계정 목록을 초기화한다. 앱 설정은 해당 종료로 초기화되지 않는다.

설정 보존 SHA-256 전후 동일:

- 기존 사용자 앱: `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`
- QA 앱: `ae0ee4e0e2e7ff7008daab74e96dabe6c25383f7857aafed6fbd645d9ebb64cf`

공통 행동 로그는 PID 9623, 프로세스 `85caa7cb-6ed1-4bd6-b4d8-4be869782942`에서 **app_open 1건**, first_done/repeat_done/drop 0건이었다. [원문 로그](signup-device-log.txt)는 이 이벤트 집계 근거이며, 가입 처리 자체를 서버에 전송한 로그가 아니다. 가입 성공·오류·로그인 연결은 실제 UI로 확인했다.

검증 APK: `build/handoff/ClutchAlarm-Signup-QA.apk`, SHA-256 `28F3B5F6015F4C2FCBBDB623ACA934BC722FA0CEE6A0030D818ACE672526406D`. 이전 로그인/인계 APK는 수정하지 않았다.

## 서버 연결·다음 단계

- production 제공자에서 실제 인증 저장소를 주입하고 `signIn`, `signUp`을 각각 구현한다. 가입 완료를 인증 완료로 취급하지 않는다.
- 가입 URL/요청·응답·중복 이메일 오류 매핑, 비밀번호 길이/허용 문자/최대 길이, 이메일 정규화·소유 확인 정책은 팀 명세로 확정해야 한다. 임의 API를 생성하거나 연결하지 않았다.
- 응원팀·알림·최초 설정 완료는 기존 기기 로컬 값이다. 가입 계정으로 덮어쓰지 않는다. 계정별 연결 정책은 후속 협의 대상이다.
- **로그인 유지와 마이페이지 로그아웃은 후속 단계**이며 이번에는 세션 저장·복원·토큰·로그아웃을 구현하지 않았다.
- 실제 서버/네트워크 가입, 일반/릴리스 APK의 실제 기기 서비스 미연결 오류, 로딩 중 회전/프로세스 종료는 실기기 재현이 남아 있다. 네트워크 실패와 일반/릴리스의 성공 차단은 단위 테스트로 확인했다.
