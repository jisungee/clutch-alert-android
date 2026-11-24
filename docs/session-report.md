# 로그인 세션 저장·복원·로그아웃 2단계 (2026-11-24)

세션 확인 화면, 암호화 저장, 재실행 복원, 마이페이지 로그아웃 확인창을 추가했다. 계정 정보 표시와 마이페이지 디자인은 변경하지 않았다. 실제 인증 API·서버는 미연결이다.

## 저장과 복원

- `StoredSession`에는 **불투명한 인증용 credential과 만료 시각만** 담는다. 이메일·비밀번호·가입 응답 전체를 넣지 않는다. 객체의 toString도 credential을 가린다.
- `AndroidSessionStore`는 Android Keystore의 AES-256 키와 AES-GCM을 사용한다. 저장마다 새 nonce, 버전 바이트, 앱 패키지를 포함한 AAD로 암호문을 인증한다. [Android Keystore 공식 설명](https://developer.android.com/privacy-and-security/keystore).
- 저장 위치는 앱의 `no_backup/auth-session.bin`이다. 키를 파일로 내보내지 않으며 세션 파일은 앱 백업 대상 밖에 둔다. 쓰기는 [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)과 프로세스 내 잠금으로 처리한다. 비밀번호/토큰을 로그에 남기지 않는다.
- QA 로그인은 암호화 세션 저장이 끝나야 Success를 반환한다. 저장 실패는 로그인 정보 저장 오류로 표시하고 인증 완료로 처리하지 않는다.
- 시작 시 `CHECKING` 화면으로 가리고 세션을 확인한다. 확인 중에는 홈·상세·로그인 입력을 렌더링하지 않는다. 성공하면 기존 로컬 최초 설정 미완료자는 응원팀 선택, 완료자는 홈으로 간다. 명시적 시스템 알림 요청은 해당 상세 목적지를 유지한다.
- 일시적 실패는 `Retryable`로 구분해 저장된 credential을 유지하고 **세션 확인 다시 시도**를 제공한다. 인증되었다고 가정하거나 자동 삭제하지 않는다.
- `Invalidated(EXPIRED/REVOKED)`는 서버가 확인한 세션 거절을 표현하는 계약이다. 이때만 세션을 지우고 로그인으로 보낸다. 기기 데이터 손상/키 무효화는 `LOCAL_DATA_INVALID`로 별도 안내한다. 삭제 자체가 실패하면 보호 화면을 열지 않고 재시도 상태를 표시한다.

## QA와 일반·릴리스

| 빌드 | 동작 |
| --- | --- |
| QA debug | 신규 가입/기존 QA 계정 로그인 후 `qa-session:` 임의 UUID와 24시간 만료를 암호화 저장. 새 프로세스가 이 기록을 읽어 복원 |
| 일반 debug / 모든 release | production 제공자. 실제 서비스 미연결이므로 가입/로그인은 실패. 저장된 credential이 있어도 검증 없이 복원하지 않고 NOT_CONFIGURED 재시도 상태 |

QA 토큰은 **테스트용 로컬 인증 표시**다. 서버 검증/서명/서버 폐기를 수행하는 실제 토큰이 아니다. QA 전용 소스는 기존처럼 일반·릴리스에서 제외한다. QA 옵션을 켠 release에서도 QA 세션 복원을 허용하지 않는 테스트를 통과했다.

QA 가입 계정 목록은 여전히 프로세스 메모리다. 새 프로세스에서 신규 가입자의 세션을 복원하는 데 이메일·비밀번호나 이 계정 목록을 필요로 하지 않는다. 로그아웃 후 계정 목록이 사라진 새 프로세스에서 다시 직접 로그인하는 검증은 기본 QA 계정을 사용하거나 다시 가입한다. 실계정 저장 정책을 구현한 것이 아니다.

QA의 `refreshSession()`은 유효한 세션의 임의 credential을 교체하고 유효기간을 늘려 저장한다. QA의 로컬 24시간 만료는 테스트 규칙이며 실제 서버의 세션 만료 판정이 아니다.

## 로그아웃과 알림 경로

- 마이페이지 **로그아웃 → 로그아웃하기** 확인창을 제공한다. 취소하면 세션을 유지한다. 기존 두 설정 메뉴와 프로필 디자인은 유지한다.
- 확정 즉시 인증 UI를 가리고 재생·상세·설정 경로를 정리한다. 원격 signOut이 실패하거나 5초 내 완료되지 않아도 finally에서 로컬 세션 제거를 시도한다. 제거는 coroutine 취소 중에도 수행하며 완료 후 로그인 화면으로 간다.
- 로컬 제거 실패를 로그아웃 완료로 표시하지 않는다. `LOGOUT_FAILED` 화면에서 보호 UI 없이 다시 삭제를 시도할 수 있다. 실제 서버의 원격 폐기 성공은 이 단계에서 보장하지 않는다.
- 응원팀·알림·최초 설정 완료·설치 순번·열람 ID 저장소는 지우지 않는다. 이전 Activity 상태의 상세/마이페이지는 세션 없이 복원되지 않는다.
- 세션 확인 중 또는 로그인 전의 알림 요청은 보류한다. 세션 복원/로그인과 필요한 최초 설정을 끝내면 그 상세를 연다. 실제 Loaded 상세가 RESUMED일 때만 기존 열람 판정을 호출한다.
- 이미 확인된 저장 화면을 일반 재실행 때 다시 여는 것과 새 시스템 알림 요청을 구분한다. 로그아웃 시 이전 대기 목적지도 제거한다. 이후 실제 알림을 다시 누르면 새로운 명시적 진입 요청으로 처리한다.

## 연결 지점과 변경 파일

Kotlin 기본 경로는 `app/src/main/java/com/clutchalarm/app/`다.

| 파일/함수 | 역할·서버 담당자 작업 |
| --- | --- |
| `auth/AuthenticationRepository.kt` | `signIn`, `signUp` 유지. `restoreSession`, `refreshSession`, `signOut`, `clearLocalSession` 계약 추가 |
| `auth/SessionStore.kt` | 저장 정보 최소 계약, 복원 성공/없음/재시도/확정 무효 구분 |
| `auth/SessionCipher.kt`, `auth/AndroidSessionStore.kt` | 암호화와 원자적 저장/삭제. 실제 제공자도 이 저장소를 주입받을 수 있음 |
| `src/productionAuth/.../AuthenticationProvider.kt` | 실제 인증 구현 주입. 현재 UnavailableAuthenticationRepository로 인증 성공 우회 없음 |
| `src/behaviorQa/.../AuthenticationProvider.kt` | QA 저장·복원·갱신·삭제, process-local 가입 목록과 세션 분리 |
| `ui/LoginViewModel.kt`, `ui/SessionScreen.kt`, `ui/LoginScreen.kt` | 확인 중/재시도/무효/로그아웃 상태와 안내. credential을 화면 상태에 넣지 않음 |
| `MainActivity.kt` | 암호화 저장소 주입, 인증/인증 없음/로그아웃 콜백. ClutchSession 진단 로그에 상태만 기록 |
| `ui/GameViewModel.kt` | 인증 전 알림 보류, 인증 후 목적지 재개, 로그아웃 경로 삭제와 기존 설정 보존 |
| `ui/GameApp.kt`, `ui/MyPageScreen.kt` | 화면 게이트와 로그아웃 버튼·확인창 |
| `SessionFlowTest`, `SessionCipherTest`, `BehaviorFlowTest` | 보안 저장과 복원/삭제/경로/열람 회귀 테스트 |

실제 인증 연결 시:

1. `signIn`: 서버 인증 후 복원용 credential을 암호화 저장한 뒤에만 Success를 반환한다. 비밀번호는 저장하지 않는다.
2. `restoreSession`: 저장된 credential을 읽고 서버 검증 또는 필요한 refresh를 수행한다. 액세스 토큰의 로컬 만료 시각만으로 영구 세션을 삭제하지 않는다.
3. `refreshSession`: 서버가 갱신을 승인하면 새 credential을 원자적으로 저장한다. 일시적 연결/서비스 장애는 Retryable, 확정된 만료·폐기 응답만 Invalidated로 매핑한다.
4. `signOut`: 필요하면 이전 credential로 서버 폐기를 요청한다. 호출자는 실패/시간 초과에도 `clearLocalSession`을 수행한다. 오프라인 원격 폐기·토큰 무효화 정책은 팀 계약으로 확정한다.
5. credential 종류·회전·만료 의미, 서버 오류 코드, 계정별 설정 연결은 아직 미확정이다. 세션 데이터 포맷이나 오류 응답을 임의 API 명세로 취급하지 않는다.

## 빌드·테스트

일반 debug, QA debug, QA 옵션을 켠 release 모두 **BUILD SUCCESSFUL**. 각 테스트 묶음 **121개 통과**, 실패·오류·스킵 0이며 새로 실행됐다. 기존 로그인·가입·16개 판정·재생·저장·열람 회귀 테스트를 유지했다.

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --max-workers=1 -PbehaviorQa=true :app:assembleRelease :app:testReleaseUnitTest
```

추가 테스트 10개는 확인 중 입력 차단, 연결 실패 시 유지·재시도, 만료·폐기·로컬 손상 시 삭제, 로그아웃 취소·원격 실패 중 로컬 삭제, 삭제 실패 재시도, QA만 복원·갱신 가능, AES-GCM nonce 변경·변조/AAD 불일치 거절, 인증 전 알림 대기와 과거 화면 제거를 검증했다.

## 실기기 결과 (2026-11-24)

Samsung SM-N960N, Android 10/API 29, USB `device`. QA 데이터를 백업하고 QA 앱만 업데이트·초기화했다. 기존 사용자 앱은 삭제하거나 재설치하지 않았다.

| 구간/PID | 실제 관측 |
| --- | --- |
| 10740 | `session-stage2@example.com` 신규 가입·로그인, SSG·기본 알림 첫 저장, 홈. 암호화 세션 파일 125바이트·버전 1. 비밀번호·이메일·평문 QA 토큰 미포함 |
| 12656 | HOME → am kill → pidof 결과 없음 확인 → MAIN/LAUNCHER 실행. 새 프로세스에서 세션 복원 후 홈 표시, 로그인 입력 없음 |
| 15724 | 알림 1·2번 발송 후 프로세스 부재 확인. **실제 1번 시스템 알림 탭**. restore AUTHENTICATED(13:29:13.156) 뒤 상세 표시와 first_done(13:29:13.708) |
| 15724 | 마이페이지 로그아웃 취소는 세션 유지. 확정하면 로그인 화면·파일 삭제. Back 후 재진입도 로그인 화면 |
| 17007 | 로그아웃 후 프로세스 부재 확인 → 재실행. SIGNED_OUT·로그인 화면 |
| 17314 | 세션 없이 프로세스 부재 확인 → **남은 실제 2번 시스템 알림 탭**. 로그인 화면에 대기하며 열람 이벤트 0. 기본 QA 계정 로그인 후 해당 상세가 표시될 때만 repeat_done(notification_open) |
| 17824 | 알림 상세를 본 뒤 일반 재실행도 홈으로 복원. 다시 로그아웃하면 로컬 세션 삭제 |
| 17966 | 최종 프로세스 재실행은 SIGNED_OUT·로그인 화면. 검증 종료 상태 |

합계는 **새 프로세스 7개에 app_open 각 1건(총 7), first_done 1, repeat_done(notification_open) 1, drop 0**이다. 같은 프로세스의 Back·로그인·화면 이동으로 app_open이 늘어나지 않았다.

알림은 경기 `mock-001`, POST_ID `7c70aef9-b716-4170-8425-e6081f46bb61:1`과 `:2`, 설치 순번 1·2다. 서버 전송은 없다.

- [전체 프로세스 원문 로그](session-device-log.txt)
- [2번 알림 탭 후 로그인 전 원문 로그](session-before-login-notification-device-log.txt): 확인 중·SIGNED_OUT·app_open만 있고 열람 기록은 없다.

저장한 설정의 SHA-256은 로그아웃 전후 `b7b91318d3f6c95ffc8846a8dd6cf4e5f2934db2625927149304479b7f713848`로 동일하다. 기존 사용자 앱 설정도 전후 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4` 그대로다.

검증 APK는 `build/handoff/ClutchAlarm-Session-QA.apk`, SHA-256 `6C7BD8EFF3EE86743AFCA2A153B9BCBEDEBF9433DEF8D2FB89B1E725B73E59EC`다. 이전 APK는 덮어쓰지 않았다.

## QA 재현 및 남은 확인

1. 별도 QA APK를 설치하고 QA 데이터만 초기화한다. 신규 가입 → 로그인 → 응원팀·알림 첫 저장을 마친다.
2. HOME으로 앱을 백그라운드로 보낸 뒤 `adb shell am kill com.clutchalarm.app.behaviorqa`, `adb shell pidof com.clutchalarm.app.behaviorqa` 결과가 비었는지 확인한다. force-stop이나 최근 앱 스와이프와 혼동하지 않는다.
3. 재실행하면 확인 화면 후 홈인지 확인한다. 알림을 남겨둔 같은 조건에서는 실제 시스템 알림을 눌러 상세로 이어지는지 확인한다.
4. 마이 → 로그아웃 → 취소/확정을 각각 확인한다. 확정 후 로그인 화면, 재실행 후에도 로그인, 기존 설정 유지인지 확인한다.
5. 로그아웃 상태에서 남은 시스템 알림을 누르면 로그인 전 열람 이벤트가 없어야 한다. 기본 QA 계정 `qa@example.com` / `clutch-qa`로 로그인한 뒤 해당 상세가 열릴 때만 기록돼야 한다.

실제 서버의 장애·만료·폐기·갱신은 API 미연결로 실기기에서 재현하지 않았다. 이 분기는 가짜 저장소를 사용한 단위 테스트로 확인했다. 기기 디스크/키 무효화 실패, 세션 처리 중 회전, Android 13+ 알림 권한 거부도 추가 실기기 확인 대상이다. 홈의 **QA 로그인 화면으로**는 화면 재진입용 진단 기능이며 세션 삭제를 의미하지 않는다. 실제 로그아웃은 마이페이지 확인창을 사용한다.
