# 디자인 필수 수정 5개 적용·검증

2026-11-24. 앞선 인증/마이페이지의 미커밋 변경을 보존하고 아래 UI만 정리했다. 알림 판정, 경기 데이터, 재생 엔진, 설정 저장소, 행동 이벤트 기록 코드는 변경하지 않았다.

## 변경

| 항목 | 적용 | 파일 |
|---|---|---|
| 전체/일부/꺼짐 | 전체·카테고리에 3상태 체크박스와 ‘모두 켜짐/일부 켜짐/모두 꺼짐 (선택/전체)’ 표시. 일부 상태에서 누르면 전체 켜기, 모두 켠 상태에서 누르면 전체 끄기. 개별 토글 및 저장은 기존 함수 사용. 접근성 레이블에 대상과 상태 포함 | `NotificationSettingsScreen.kt` |
| 제목 | 최초 카테고리는 ‘알림 선택’, 이후 편집과 세부는 ‘알림 설정’. 세부의 카테고리 이름은 본문에 한 번 표시. 반복 일반 제목 제거. 상단 진입 버튼도 ‘알림 설정’으로 통일 | 위 파일, `GameApp.kt` |
| 팀 카드 | 기본 3열/128dp, fontScale>1.15이면 2열/156dp. 동일 배율에서 카드 높이 고정. 영문 ID와 이름을 유지하고 체크는 우상단 독립 표시 | `TeamSelectionScreen.kt` |
| 상세 | 점수판 여백·주자 다이아몬드를 압축하고 바로 다음에 최신 경기 상황/타자·투수 배치. 팀 선택·알림 안내·재생 제어를 그 뒤로 이동. 하단에 중복되던 팀 선택/권한 안내 카드 제거. 임시 팀 변경은 상단 팀 칩으로 계속 가능. 미제공 데이터 안내는 제어 아래로 이동 | `GameDetailScreen.kt` |
| QA 진단 | `BuildConfig.QA_DIAGNOSTICS`는 `-PbehaviorQa=true` 디버그만 true. 일반 디버그와 QA 옵션을 준 릴리스 모두 false. ‘알림 판정 상세’ 전체와 발송 요청 성공 진단 안내를 이 플래그로 제한. 사용자용 이번 재생 알림 목록은 유지 | `app/build.gradle.kts`, `ReplayNotificationSections.kt` |

## 빌드·단위 테스트

아래 구성을 최종 소스로 실행해 성공했다. 각 구성의 단위 테스트 128개, 실패·오류·스킵 0. 해당 테스트 태스크가 새로 실행됐다. 추가한 `DiagnosticBuildTest`는 QA 인증 소스 세트와 진단 플래그의 일치 및 릴리스 차단을 검사한다. 기존 127개는 설정 편집/저장·이탈, 행동 이벤트 4개, 세션/알림 진입, 알림 정책 및 7개 기존+새 JSON 경기 회귀 테스트다.

```powershell
# 공통 메모리 옵션: -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" --max-workers=1
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

일반 디버그·릴리스의 생성된 BuildConfig에서도 `QA_DIAGNOSTICS=false`를 확인했다. `git diff --check` 통과. 실제 서버 인증이 없는 일반 앱에서 인증을 우회해 상세를 여는 검증은 하지 않았다. 진단 비노출 경계는 빌드별 테스트와 실제 플래그로 검증했고 QA의 진단 노출은 실기기 화면에서 확인했다.

## 실기기

Samsung SM-N960N / Android 10(API 29), adb `28c5d648ad3f7ece device`. 별도 QA 앱만 업데이트했으며 앱 데이터 초기화 없이 기존 설치로 진행했다. 기본 fontScale=1.0과 1.3에서 촬영하고 원래 0.8로 복구했다. 원본 앱 `com.clutchalarm.app`의 설정 SHA-256은 전후 동일한 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`다.

| 검증 | 실제 결과 |
|---|---|
| 일부 14/16 → 전체 활성화 → 전체 비활성화 | 표시와 체크 상태가 각각 일부/16/0으로 바뀜. 저장 없이 뒤로 시 마이페이지 기존 14/16 유지 |
| 세부 설정 진입/뒤로/재저장 | 중복 일반 제목 제거, 카테고리 이름 1회. 재저장 성공 후 마이 복귀, 14/16 유지 |
| 팀 기본/1.3배 | 3열/2열, 이름과 SAMSUNG 한 줄 표시, 동일 높이. 확대 화면의 아래 팀까지 스크롤 가능, 저장 버튼 접근 가능. 선택 체크가 팀 ID를 대체하지 않음 |
| 상세 기본/1.3배 | 점수판 다음에 최신 상황. 기본 배율의 초기/출루 상황과 1.3배 초기 상황을 상단에서 확인. 제어는 아래 스크롤로 접근 |
| 재생/일시정지/재개/배속 | 실제 조작 성공. 2x 선택 후 경기 진행 및 재생 완료 확인. 1/5/10x의 개별 시간 진행 재현은 이번 실기기에서 하지 않았으며 기존 단위 테스트로 회귀 확인 |
| 실제 시스템 알림 | 일반 모의 경기에서 설치 순번 5~10 발송. 득점 알림(7번) 실제 탭 후 같은 상세 진입. 이미 완료된 경기와 2x 선택 유지. 알림을 눌렀다고 새로 재생되지 않음 |
| 초기화 | 실제 초기화 후 0:0, 재생 대기, 주자 없음, 최신 상황 대기 안내로 복귀 |
| 행동 이벤트 | 최종 QA 프로세스 PID 300: app_open 1, repeat_done 2(settings_resave 1/notification_open 1), drop 1. fontScale 변경과 화면 이동으로 app_open 추가 없음. 목록 직접 진입은 NOT_APPLICABLE |

이번 QA 설치는 이미 first_done을 기록한 상태여서 새 first_done은 0건이다. 새 설치의 first_done/2번 먼저 열기/중복 열람 기준은 기존 회귀 테스트를 유지했으며 이번에 새 설치 실기기 흐름 전체를 다시 실행하지 않았다. 로그는 [design-fixes-device-log.txt](design-fixes-device-log.txt)에 보관한다. 실제 재생·로그 기록 기준을 변경하지 않았다.

## 전후 사진

이전 검토의 원본 캡처와 이번 촬영을 나란히 배치한 HTML 및 원본 PNG/XML은 다음 작업용 폴더에 보관한다.

`C:/Users/pinks/.codex/visualizations/2026/11/23/01a206d2-e0c3-7663-824e-529c39cf13fe/design-fixes/comparison.html`

비교 쌍: 전체 일부 상태, 카테고리 세부 제목, 팀 기본/1.3배, 상세 기본/1.3배, QA 진단. 팀 선택값 등 QA 데이터가 다른 캡처는 레이아웃 비교로만 사용한다. 캡처는 정지 상태 증거이며 모든 동작 테스트를 대신하지 않는다.

검증 APK는 Git 제외 경로 `build/handoff/ClutchAlarm-Design-QA.apk`, SHA-256 `c14082be7059e4624d2599c4a9114552cfaf4184118628a201269a9e5ea09c24`다. 이후 일반 빌드가 `app/build/outputs/apk/debug/app-debug.apk`를 덮어썼으므로 QA 설치에는 별도 인계 APK를 사용한다.

Android 13+ 알림 권한, TalkBack 실제 읽기, 소프트 키보드가 열린 상태, 1.3보다 큰 글자, 태블릿/작은 기기는 미검증이다. 기존 서버 API와 행동 이벤트 전송은 이번 범위가 아니다.
