> 이 보고서는 이전 정책 검증 기록입니다. 현재 3점 기준·복수 코드 구독·16개 토글 정책의 검증은 [새 PDF 정책 보고서](pdf-policy-test-report.md)를 참고하세요.

# 알림 통합 테스트 보고서

가이드라인 v1.0의 14개 알림 유형을 모의 JSON에서 재현하고, 저장소부터 발송기 인터페이스까지 연결해 검증했다. 디버그 빌드에 성공했고 JVM 테스트는 기존 32개와 신규 통합 3개를 포함해 총 35개가 통과했다.

## 검증 방법

- 단위 테스트는 기존 `NotificationRuleEngineTest`의 조건 및 문구 검증 결과다.
- 통합 테스트는 실제 앱에 포함되는 `games/catalog.json`과 7개 경기 JSON을 `MockGameRepository`로 읽는다. `GameViewModel`이 가상 시간으로 `GameSimulationEngine`을 실행하고, `NotificationSession`과 `NotificationRuleEngine`을 거쳐 기록용 `NotificationDispatcher`에 전달한다.
- 표의 실제 제목과 본문은 기록용 발송기가 받은 후보를 JUnit XML의 `system-out`에서 옮겼다. 테스트는 기대한 유형과 제목, 본문 전체가 일치하는지 검사했다.
- 동일 재생 시각의 판독 결과와 부상 의심을 모두 순서대로 전달했다. 일시정지·재개, 초기화 후 새 세션, 경기 전환 뒤 이전 경기 후보 미발송, 공수교대 중복 억제와 이닝 5번째 득점에서의 빅이닝 중복 억제도 검증했다.
- 홈런 득점은 타자와 당시 베이스 주자 수에 맞췄다. 끝내기 안타와 홈런은 9회 말 홈팀이 앞서며 경기 종료로 전환한다. [MLB 홈런 설명](https://www.mlb.com/glossary/standard-stats/home-run), [MLB 끝내기 설명](https://www.mlb.com/glossary/standard-stats/walk-off)

## 알림 유형 14종

| 알림 | 판정 조건 | JSON과 이벤트 | 기대 제목과 본문 요건 | 실제 생성 제목과 본문 | 단위 | 통합 | 실기기 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 경기 시작 | 확인된 첫 투구 | `mock-game.json` / `play-1` | ⚾ 경기 시작 / 첫 투구, 0 : 0 | **⚾ 경기 시작**<br>1회초 · 부산 웨이브스 경기 시작! 첫 투구 · 0 : 0 | 통과 | 통과 | 게시 확인 |
| 공격 시작 | 선호 팀의 공격 시작 확인 | `mock-close.json` / `half-start` | ⚾ 공격 시작 / 부산 7회초, 2 : 3 | **⚾ 공격 시작**<br>7회초 · 부산 웨이브스 공격 시작 · 2 : 3 | 통과 | 통과 | 게시 확인 |
| 공격 종료 | 선호 팀 공격의 세 번째 아웃 | `mock-game.json` / `play-6` | ⚾ 공격 종료 / 수비 전환을 통합, 1 : 0 | **⚾ 공격 종료**<br>1회초 · 부산 웨이브스 공격 종료 · 수비 전환 · 1 : 0 | 통과 | 통과 | 게시 확인(접전 경기) |
| 수비 시작 | 상대 공격 시작 확인 | `mock-close.json` / `half-start` | 🛡️ 수비 시작 / 서울 7회초, 3 : 2 | **🛡️ 수비 시작**<br>7회초 · 서울 팔콘스 수비 시작 · 3 : 2 | 통과 | 통과 | 게시 확인 |
| 수비 종료 | 상대 공격의 세 번째 아웃 | `mock-game.json` / `play-6` | 🛡️ 수비 종료 / 공격 전환을 통합, 0 : 1 | **🛡️ 수비 종료**<br>1회초 · 서울 팔콘스 수비 종료 · 공격 전환 · 0 : 1 | 통과 | 통과 | 게시 확인 |
| 출루 | 선호 팀 타자가 안타로 1루에 생존 | `mock-game.json` / `play-2` | 🏃 출루 / 김도윤, 무사 1루, 0 : 0 | **🏃 출루**<br>1회초 · 부산 웨이브스 김도윤 안타! 무사 주자 1루 · 0 : 0 | 통과 | 통과 | 게시 확인 |
| 득점 | 선호 팀 득점으로 리드 | `mock-game.json` / `play-3` | 🎉 득점 / 이준서 2루타, 1 : 0 리드 | **🎉 득점**<br>1회초 · 부산 웨이브스 이준서 2루타! 리드 · 1 : 0 | 통과 | 통과 | 게시 확인 |
| 동점 | 뒤지던 선호 팀이 득점해 동점 | `mock-close.json` / `tie` | 🔥 동점 / 이준서 적시타, 3 : 3 | **🔥 동점**<br>7회초 · 부산 웨이브스 이준서 적시타! 동점 · 3 : 3 | 통과 | 통과 | 게시 확인 |
| 역전 | 뒤지던 선호 팀이 2점 홈런으로 앞섬 | `mock-close.json` / `comeback` | 🚨 역전 홈런 / 정지훈 2점 홈런, 5 : 4 | **🚨 역전 홈런**<br>8회초 · 부산 웨이브스 정지훈 2점 홈런! 역전 · 5 : 4 | 통과 | 통과 | 게시 확인 |
| 홈런 | 선호 팀 타자의 솔로 홈런 | `mock-home-runs.json` / `solo` | 💥 홈런 / 이준서 솔로 홈런, 3 : 1 | **💥 홈런**<br>4회초 · 부산 웨이브스 이준서 솔로 홈런! 1점 득점 · 3 : 1 | 통과 | 통과 | 게시 확인 |
| 실점 | 상대 2점 홈런으로 역전 허용 | `mock-close.json` / `comeback` | 🚨 역전 허용 / 정지훈 2점 홈런, 4 : 5 | **🚨 역전 허용**<br>8회초 · 부산 웨이브스 정지훈 2점 홈런! 역전 허용 · 4 : 5 | 통과 | 통과 | 일반·동점·역전 허용 게시 확인 |
| 비디오 판독 | 공식 1루 세이프 여부 판독 시작 | `mock-special.json` / `review-start` | 📺 비디오 판독 / 3회초 1루 세이프 여부, 1 : 1 | **📺 비디오 판독**<br>3회초 · 1루 세이프 여부 · 공식 비디오 판독 시작 · 1 : 1 | 통과 | 통과 | 시작·번복 게시 확인 |
| 부상 | 부상 의심으로 경기 중단 | `mock-special.json` / `injury-suspected` | 🚑 부상 / 임건우 부상 의심, 1 : 1 | **🚑 부상**<br>3회초 · 서울 팔콘스 임건우 부상 의심 · 경기 중단 · 1 : 1 | 통과 | 통과 | 의심·의료진 확인·교체 게시 확인 |
| 빅이닝 | 같은 공격 이닝의 4번째 득점 | `mock-big-inning.json` / `fourth-run` | 🔥 빅이닝 / 최현우 적시타와 4득점 통합, 6 : 1 | **🔥 빅이닝**<br>5회초 · 부산 웨이브스 최현우 적시타! 1점 득점 · 부산 웨이브스 4득점 빅이닝 · 6 : 1 | 통과 | 통과 | 게시 및 알림 탭 확인 |

## 추가 복합 사례

| JSON과 이벤트 | 실제 생성 제목과 본문 |
| --- | --- |
| `mock-close.json` / `tie` | **⚠️ 동점 허용**<br>7회초 · 부산 웨이브스 이준서 적시타! 동점 허용 · 3 : 3 |
| `mock-home-runs.json` / `two-run` | **🚨 역전 홈런**<br>4회초 · 부산 웨이브스 김도윤 2점 홈런! 역전 · 2 : 1 |
| `mock-home-runs.json` / `three-run` | **💥 홈런**<br>4회초 · 부산 웨이브스 정지훈 3점 홈런! 3점 득점 · 부산 웨이브스 6득점 빅이닝 · 6 : 1 |
| `mock-home-runs.json` / `grand-slam` | **💥 홈런**<br>4회초 · 부산 웨이브스 장현우 만루 홈런! 4점 득점 · 10 : 1 |
| `mock-big-inning.json` / `fifth-run` | **🎉 득점**<br>5회초 · 부산 웨이브스 정지훈 적시타! 1점 득점 · 7 : 1 |
| `mock-walkoff-hit.json` / `walkoff-hit` | **🚨 끝내기 승리**<br>9회말 · 서울 팔콘스 윤지호 적시타! 끝내기 승리 · 5 : 4 |
| `mock-walkoff-homer.json` / `walkoff-homer` | **🚨 끝내기 홈런**<br>9회말 · 서울 팔콘스 윤지호 2점 홈런! 끝내기 승리 · 5 : 4 |
| `mock-special.json` / `review-result` | **📺 비디오 판독**<br>3회초 · 비디오 판독 결과 · 세이프 판정으로 번복 · 1 : 1 |
| `mock-special.json` / `injury-substitution` | **🚑 부상**<br>3회초 · 서울 팔콘스 임건우 부상으로 교체 · 1 : 1 |

위 복합 사례도 실기기 게시를 확인했다. 판독 번복 사례는 아웃과 주자만 정정한다. 점수가 바뀌는 판독에 관한 알림 정책은 [검토 항목](../docx/notification-policy-review.md)에 남아 있다.

## 빌드 명령

```powershell
.\gradlew.bat --offline --no-daemon --project-cache-dir .gradle/stage7-verify --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' :app:testDebugUnitTest :app:assembleDebug
```

## 스마트폰 검증 방법

USB로 연결된 Samsung SM-N960N(API 29)에 디버그 APK를 설치했다. UI Automator XML에서 시나리오 이름, 팀 선택, 재생 버튼의 노드 좌표를 읽고 ADB로 탭했다. 게시 여부와 제목·본문은 해당 앱의 `dumpsys notification --noredact` 레코드에서 확인했다. 알림 이동은 삼성 알림 묶음을 펼친 뒤 빅이닝 알림을 탭하고 `5회 초`, 원정 2점·홈 1점, 3루 주자 장현우가 있는 빅이닝 경기의 초기 상세 텍스트를 확인했다. 직접 육안 확인과 스크린샷 검토는 수행하지 않았다.

```text
adb devices -l
adb -d shell getprop ro.build.version.sdk
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -W -n com.clutchalarm.app/.MainActivity
adb -d shell uiautomator dump /data/local/tmp/clutch-stage7-ui.xml
adb -d shell cat /data/local/tmp/clutch-stage7-ui.xml
adb -d shell wm size
adb -d shell input swipe <x1> <y1> <x2> <y2> 420
adb -d shell input tap <x> <y>
adb -d shell input keyevent KEYCODE_BACK
adb -d shell dumpsys notification --noredact
adb -d shell cmd statusbar expand-notifications
adb -d shell cmd statusbar collapse
adb -d logcat -d -s NotificationService NotificationManager ClutchNotifications
```

연속 재생 중 Android 알림 로그에 `Package has already posted or enqueued 25 notifications. Not showing more.`가 기록됐다. 당시 앱에는 `시스템 발송 요청 성공`이 남았지만 끝내기 알림은 시스템 게시 목록에서 확인되지 않았다. 이 상태는 발송 API 호출이 반환됐다는 뜻이다. ClutchAlarm 테스트 알림 묶음만 스와이프로 정리한 뒤 두 끝내기 알림이 각각 ID 32와 33으로 게시됐고, 앱 화면은 9회 말 5대4로 재생을 마쳤다. 앱의 자동 정리와 보존 정책은 후속 검토 항목이다.

대표 시스템 레코드는 출루 ID 7, 동점 ID 11, 역전 홈런 ID 16, 솔로·3점·만루 홈런 ID 18·21·25, 빅이닝 ID 4였다. 같은 시각의 판독 결과와 부상 의심은 각각 ID 35와 36으로 게시됐다. 이후 홈팀 관점의 동점·역전 허용은 ID 40과 44로 확인했다.

이번 실기기 검증은 API 29에서 진행했다. Android 13 이상 권한 대화상자와 아이콘·배너·소리는 이번 빌드에서 재검증하지 않았다.

8-1단계에서 적용한 보존 상한과 게시 상태는 [Android 알림 보존과 게시 확인](notification-retention.md)에 기록했다.
