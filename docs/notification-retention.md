# Android 알림 보존과 게시 확인

## 시스템 알림 보존

- ClutchAlarm이 게시한 모의 경기 알림은 새 알림을 게시하기 전에 활성 목록을 읽고 총 12개 안에서 관리한다. 12개는 Samsung API 29 기기의 25개 제한을 피해 여유를 확보하기 위해 선택한 MVP 기준이다. Android 공통 한도나 보장된 제조사 한도는 아니다.
- 끝내기, 역전과 역전 허용, 홈런은 중요 알림으로 표시한다. 일반 알림부터 게시 시각이 오래된 순서로 정리한다. 보존 슬롯이 모두 중요 알림이면 새 일반 알림을 생략한다. 새 중요 알림은 필요할 때 가장 오래된 중요 알림을 대체할 수 있다. 업그레이드 전에 중요 알림이 이미 12개 이상이라면 사용자가 알림을 정리하거나 새 중요 알림이 들어올 때까지 기존 목록이 12개를 넘을 수 있다.
- 관리 대상은 ClutchAlarm 패키지가 게시한 모의 경기 채널의 제거 가능한 알림이다. 그룹 요약과 서비스 알림은 제외한다. API 23~25에서는 채널 대신 모의 경기 알림 아이콘으로 구분한다. 기존 버전의 UUID 태그를 인식해 업데이트 직후에도 정리하고, 중요도는 이전 제목·본문을 기준으로 보수적으로 분류한다.
- 게시와 취소는 비동기이므로 짧게 처리 중인 요청을 함께 계산한다. 활성 목록 조회나 취소 요청이 실패하면 추가 게시를 진행하지 않고 요청 실패로 남긴다. 새 프로세스에서도 시스템의 기존 활성 목록을 사용한다. 게시 식별자는 프로세스별 UUID 태그와 증가하는 정수 ID의 조합이다.
- 게시된 시스템 알림은 재생 완료·초기화·재시작과 별도로 관리한다. 시스템에서 정리한 알림은 재생 세션의 중복 방지와 별개다. 앱 화면에는 현재 재생 회차의 기록을 표시한다. 초기화와 처음부터 재생 시 화면 기록을 비우고, 이전 회차의 늦은 게시 확인 결과는 폐기한다. 사용자는 Android 알림창에서 ClutchAlarm 알림을 수동으로 정리할 수 있다.

## 기록의 의미

| 표시 | 기록 시점 |
| --- | --- |
| 후보 생성 | 규칙 엔진이 발송 후보를 만들었다. |
| 권한 거부 / 시스템 알림 꺼짐 | 권한 또는 앱·채널 상태 때문에 게시 요청을 하지 못했다. |
| 시스템 발송 요청 성공 | `notify()`가 정상 반환했다. |
| 발송 요청 실패 | 활성 목록 조회, 취소 요청 또는 발송 요청에서 예외가 발생했다. |
| 시스템 게시 확인 · 활성 목록 | 요청 뒤 최대 세 차례 조회 중 같은 태그와 ID가 앱의 활성 목록에 나타났다. |
| 시스템 게시 미확인 | 제한된 확인 구간에 같은 식별자를 관찰하지 못했다. 이미 눌렀거나 지운 알림, 조회 오류, 제조사 제한 등이 이 상태로 남을 수 있다. |
| 기존 알림 정리됨 | 취소를 요청한 식별자가 이후 활성 목록에 없음을 관찰했다. |

`getActiveNotifications()`는 API 23부터 지원되므로 현재 앱의 최소 버전에서 사용할 수 있다. API 26부터는 알림 채널이 적용되며, API 33부터는 기존 런타임 권한 검사가 적용된다. 활성 목록은 조회 시점의 상태다. OS 구현에 따라 처리 중인 항목이 포함될 수 있고, 사용자 화면에 배너가 표시됐는지나 소리·진동을 수신했는지는 이 API로 확인할 수 없다. [Android NotificationManager API](https://developer.android.com/reference/android/app/NotificationManager#getActiveNotifications()), [Android 14 알림 서비스 구현](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/notification/NotificationManagerService.java)

## 검증 방법

디버그 빌드와 JVM 테스트 41개를 통과했다. 신규 테스트 6개는 활성 목록 복구, 일반·중요 알림 우선순위, 짧은 연속 발송, 확인 지연·조회 실패, 세션을 넘겨 보존되는 앱 기록을 검사한다.

Samsung SM-N960N(API 29)에 디버그 APK를 설치했다. 끝내기 홈런을 먼저 게시하고 판독과 부상 경기를 반복 재생했다. 시스템 알림 레코드에서 알림 개수와 태그·ID를 확인했다. 화면 버튼과 알림은 UI Automator 노드 좌표로 탭했다. 직접 육안 확인과 스크린샷 검토는 하지 않았다.

- 시스템 로그에서 프로세스 종료 전 16건, 재시작 후 15건으로 서로 다른 태그·ID의 게시 31건을 확인했다. 각 재생 종료 시 앱의 개별 알림 수는 6, 11, 12, 12, 12, 12개였다. OS가 생성한 묶음 요약은 이 수에서 제외했다.
- 첫 프로세스에서 ID 2~5인 오래된 일반 알림이 정리됐고, 끝내기 홈런 ID 1은 두 번째 프로세스에서도 남았다. 앱 기록에서 발송 요청 성공, 활성 목록 확인, 기존 알림 정리됨이 각각 표시됐다.
- 재시작 후 남은 부상 알림을 탭해 판독과 부상 경기의 3회 초 상세가 열리는 것을 확인했다.

```text
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -W -n com.clutchalarm.app/.MainActivity
adb -d shell dumpsys notification --noredact
adb -d shell uiautomator dump /data/local/tmp/clutch-stage81-ui.xml
adb -d shell cat /data/local/tmp/clutch-stage81-ui.xml
adb -d shell input tap <x> <y>
adb -d shell input keyevent KEYCODE_HOME
adb -d shell am kill com.clutchalarm.app
adb -d shell pidof com.clutchalarm.app
adb -d shell cmd statusbar expand-notifications
adb -d logcat -d -s NotificationService
```

API 37 에뮬레이터에서는 권한 대화상자에서 거부한 뒤에도 경기 재생이 진행됐고 앱 기록에 `후보 생성 · 권한 거부`가 남았다. ADB로 권한을 다시 허용한 뒤 시스템 게시를 확인했다.
