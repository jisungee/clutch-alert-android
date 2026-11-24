# 전체 앱 시각 정리·검증

2026-11-24. 최초 KBO.pdf 시안과 앞선 필수 디자인 수정 5개를 기준으로 UI를 정리했다. 계정·설정 저장소, ViewModel, 재생 엔진, 알림 판정/발송, 행동 이벤트 코드는 변경하지 않았다. 기존 인증/첫 이용/알림 진입 흐름과 QA 전용 진단 경계를 유지했다.

## 적용한 화면·공통 스타일

| 영역 | 변경 |
|---|---|
| 테마 | `ClutchTheme.kt`로 인증/세션/메인 화면의 색상·Typography·Shapes 통합. 빨강 #BE1536, 딥레드 #800F2C, 네이비 #172238, 연회색 배경/흰 카드. 기본 ContentColor도 네이비로 통일 |
| 글꼴 | 시스템 SansSerif 사용. 제목 20~30sp/본문 14~16sp/보조 11~12sp와 행간을 명시. 기기 fontScale을 유지하며 강제로 글자 배율을 낮추지 않음. 시안 전용 폰트 파일은 제공되지 않아 특정 서체와의 픽셀 일치를 주장하지 않음 |
| 컨트롤 | `ClutchButton`/`ClutchOutlinedButton`: 최소 52dp, 16dp 모서리. 인증 제출은 기존 최소 56dp를 유지. 내용이 커지면 높이가 늘어남. 공통 카드 20dp, 화면 기본 여백 16dp, 그룹 간격 12~20dp. 이동 chevron과 체크 배지를 Canvas 선 아이콘으로 통일 |
| 배속 | Secondary/SecondaryContainer도 빨강/연분홍으로 정의. 기존 FilterChip의 선택 배속을 연보라 대신 앱 강조색으로 표시. 1/2/5/10배속 액션은 그대로 |
| 팀 식별 | `TeamIdentity.kt`의 공통 `TeamCrest`와 원형 선택 배지. 선택해도 팀 식별자가 사라지지 않음. 기본 3열/120dp, 1.3배 2열/144dp. 로고 미등록 시 팀 약칭, 모의 전용 팀은 야구공 표시 |
| 홈 | 응원팀 식별 → 모의 경기 CTA → 짧은 3단계 안내로 압축. 반복 안내·영문 장식 문구를 줄이고 CTA 접근을 앞당김. 미연결 실시간 버튼이나 순위를 추가하지 않음 |
| 경기 목록 | 20→16dp 카드 패딩, 간격·설명 크기 압축, 중복 구분선 제거. 팀별 식별 표시와 시작 점수·시작 이닝·모의 표시 유지. 경기 카드 전체 탭 이동 유지 |
| 경기 상세 | 앞 단계의 최신 상황 우선 배치 유지. 카드 모서리·타이포그래피·버튼·칩을 공통 테마에 맞춤. KBO 팀 상세에는 팀 식별 표시 사용. 미제공 데이터는 채우지 않음 |
| 마이페이지 | 프로필/메뉴 카드 간격과 메뉴 행 패딩 축소, 저장 팀 식별 표시, 공통 chevron 적용. 긴 이메일은 줄바꿈. 응원팀/알림 변경·로그아웃·탈퇴 액션 유지 |
| 인증 | `AuthBrandHeader.kt` 공통 소형 헤더. 입력/제출 영역을 위한 공간 확보. 기존 `imePadding`/스크롤을 유지하고 Activity에 `adjustResize` 지정. 가입 성공 후 이메일이 채워진 로그인으로 돌아오는 동작 유지 |

## 공식 로고: 자료 대기

공식 로고 파일은 저장소에 없다. 현재 약칭 원형 표시는 공식 엠블럼이 아니며, 로고 적용 완료로 보지 않는다. 필요한 10개 파일과 규격/등록 위치는 [구단 로고 전달 목록](team-logo-assets.md)에 정리했다. `TeamLogoResources.approved`에 사용 허가된 리소스를 등록하면 홈·선택·마이·목록·KBO 상세에서 재사용된다. 외부 로고를 임의 다운로드하거나 생성하지 않았다.

## 빌드·단위 테스트

최종 소스로 아래 구성을 실행해 모두 성공했다. QA 디버그·일반 디버그·릴리스 각각 128개, 실패·오류·스킵 0. 테스트 태스크가 실제 실행됐다(중간 동일 소스 재확인은 UP-TO-DATE였으나 이를 새 실행으로 세지 않음). 스타일을 그대로 따라 쓰는 테스트는 추가하지 않고 기존 설정·인증·행동 이벤트·재생·알림 정책/이동 회귀 테스트를 사용했다.

```powershell
# 공통 옵션: -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" --max-workers=1
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

`DiagnosticBuildTest`도 통과해 일반·릴리스에서 QA 진단이 비활성임을 유지한다. 실제 서버 인증이 없는 일반 앱에 QA 인증 우회를 추가하지 않았다.

## 실기기 검증

Samsung SM-N960N, Android 10/API 29, ADB 상태 device. 1080×2220, density 420, Samsung HoneyBoard 실제 소프트 키보드 사용. 별도 `com.clutchalarm.app.behaviorqa`만 업데이트했다. 기존 사용자 앱 `com.clutchalarm.app`은 재설치·초기화하지 않았으며 설정 SHA-256은 기존 기준과 같은 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`였다. QA의 저장된 응원팀·14개 알림도 유지했다.

| 실제 확인 | 결과 |
|---|---|
| 기본 1.0/확대 1.3 주요 화면 | 홈·경기 목록·상세·마이·팀 선택·로그인·가입 촬영. 팀 이름/체크 배지/저장 버튼 접근, 상세 최신 상황 표시 확인. 긴 테스트 이메일은 확대 마이에서 줄바꿈 |
| 가입 키보드 1.0 | 이메일/비밀번호/확인 입력 후 확인란을 다시 포커스. 키보드 열린 채 앱 영역을 스크롤해 가입하기를 누름 → 성공 → 입력 이메일이 채워진 로그인 |
| 로그인 키보드 1.0 | 비밀번호 입력 후 키보드 열린 채 제출 버튼을 스크롤해 누름 → 홈 |
| 가입/로그인 키보드 1.3 | 별도 QA 계정으로 같은 흐름 실제 성공. 키보드가 화면 아래에 그려진 사진과 제출 버튼이 키보드 위에 보이는 사진 확보. 확인란 포커스 시 위쪽 이메일 일부가 스크롤 밖으로 나오는 것은 스크롤 영역이며 입력을 잃지 않음 |
| QA 세션 복원 | QA 업데이트 후 새 프로세스에서 직전 가입 계정의 세션을 복원하여 홈 진입 |
| 모의 경기 | 목록에서 일반 경기 → 2x 선택 → 임시 부산 팀 → 재생/일시정지. 실제 알림 순번 11~13 발송. 득점(13번) 탭 → 같은 경기/일시정지·2x 유지. 목록 직접 진입은 새 열람 아님 |
| 설정 | 전체 켜기 조작 후 저장 없이 이탈 → 기존 14/16 유지. 다시 들어가 저장 성공 → 마이 14/16 유지 |
| 로그아웃 | 확인 후 로그인으로 이동, 이후 재로그인으로 홈 복귀. 기존 팀·설정 보존 |

최종 QA 프로세스 PID 3677에서 `app_open` 1, `repeat_done` 2(`notification_open` 1, `settings_resave` 1), `drop` 1을 확인했다. 글자 크기 변경·가입·로그인·로그아웃·화면 이동으로 app_open이 추가되지 않았다. 이미 first_done이 기록된 QA 설치를 유지했으므로 추가 first_done은 0건이며, 새 설치 first_done 기준·중복 열람 방지는 기존 회귀 테스트로 확인했다. 로그: [visual-polish-device-log.txt](visual-polish-device-log.txt).

표시 배율은 검증 후 원래 0.8로 복구했다. 키보드 종류/표시 설정은 변경하지 않았다. 공식 로고 수급, Android 13+ 권한 화면, 다른 IME/작은 기기/태블릿, 1.3보다 큰 배율과 TalkBack 실사용은 추가 확인 대상이다. 모든 시나리오를 이번 실기기에서 끝까지 다시 재생한 것은 아니며 기존 7개+새 JSON의 로드/재생과 전체 배속·알림 규칙은 단위 테스트 범위다.

## 파일·산출물

- 새 UI 파일: `ClutchTheme.kt`, `TeamIdentity.kt`, `AuthBrandHeader.kt`
- 적용: `GameApp.kt`, `HomeScreen.kt`, `GameSelectionScreen.kt`, `GameDetailScreen.kt`, `MyPageScreen.kt`, `TeamSelectionScreen.kt`, `NotificationSettingsScreen.kt`, `LoginScreen.kt`, `SignUpScreen.kt`, `SessionScreen.kt`, `AndroidManifest.xml`
- 공통 버튼·색상 변경에 기존 QA 전용 진단 경계와 이벤트 콜백 유지. ViewModel·auth/data/domain/behavior/notification 코드에 이번 변경 없음
- 전후 비교 HTML/원본 PNG/XML: `C:/Users/pinks/.codex/visualizations/2026/11/23/01a206d2-e0c3-7663-824e-529c39cf13fe/visual-polish/comparison.html`
- Git 제외 QA APK: `build/handoff/ClutchAlarm-Polish-QA.apk`, SHA-256 `06228c40931d5c26cbeb2a54eacd519c6594f050834364ab1432d86304b38a98`

전후 비교는 같은 배율의 캡처를 짝지었다. QA 계정명·선택 팀 등 데이터가 다른 경우 레이아웃/스타일 비교로만 사용한다. 시스템 알림창의 다른 앱 알림이 포함된 원본은 배포용 비교 묶음에서 제외한다. 새 커밋은 만들지 않았다.
