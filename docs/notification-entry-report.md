# Android 로컬 알림 열람 식별

## 발송과 진입 계약

새 알림의 PendingIntent에 GAME_ID와 다음 정보를 전달한다.

| 필드 | 의미 |
| --- | --- |
| POST_ID | 발송 건별 ID: 프로세스 UUID + 게시 순번. 판정 후보 ID와 다르며 같은 플레이 재생도 새 발송이면 새 ID |
| POST_SEQUENCE | 최초 설정 저장 후 성공 발송의 설치 단위 영속 순번. 이전 PROCESS 알림은 기존 의미 유지 |
| POST_SEQUENCE_SCOPE | INSTALLATION: 현재 측정 대상 / PROCESS: 이전 또는 측정 시작 전 알림 |
| POST_SEQUENCE_SCOPE_ID | 설치 UUID 또는 이전 프로세스 UUID |

Intent data URI에도 POST_ID를 사용하고 PendingIntent는 IMMUTABLE로 만든다. 게임 ID만 같은 다른 발송 건이 기존 PendingIntent를 재사용하지 않는다. 시스템 게시 결과와 화면의 NotificationRecord에도 같은 delivery 정보를 보관한다. 권한 없음/필터/보존 상한 차단은 ID를 할당하지 않으며 실제 notify 실패에는 번호 공백이 생길 수 있다.

## 열람 판정

- 목록 카드는 LIST 진입, 알림 Intent는 NOTIFICATION 진입이다. 기존 게임 ID만 있는 알림은 LEGACY로 이동만 지원하며 새 열람으로 집계하지 않는다.
- Activity의 onCreate/onNewIntent가 metadata를 ViewModel에 전달한다. 첫 이용 중이라면 요청을 보류한다.
- 게임 로드 성공만으로 열람을 기록하지 않는다. 상세가 Compose에 표시되고 lifecycle이 RESUMED일 때 confirmDetailVisible을 호출한다. 오류·가려진 설정 화면·첫 이용 대기에서는 기록하지 않는다.
- 별도 notification_open_receipts DataStore가 POST_ID를 원자적으로 최초 등록하면 NEW, 기존이면 DUPLICATE다. 성공 전 디스크 오류는 UNCONFIRMED이며 새 열람이라고 주장하지 않는다.
- 같은 Activity에서 같은 알림을 재전달해도 같은 POST_ID라 새 열람이 아니다. 다른 두 알림은 같은 경기라도 각각 NEW가 될 수 있다.
- 회전은 보존된 ViewModel의 요청 확인 상태를 재사용한다. SavedInstanceState에는 원본 진입 metadata와 확인 여부를 보관하여 복원으로 신규 열람을 만들지 않는다. 실제 알림 재탭으로 프로세스가 다시 시작된 경우도 DataStore에서 중복을 확인한다.
- 같은 경기 알림은 진행 중인 재생·배속·현재 위치를 유지한다. 다른 경기면 기존 재생을 종료하고 요청한 경기를 연다.
- 진단용 ClutchNotificationOpen 로그에는 source, game, post, sequence, scope, newRead, status를 남긴다. 후속 구현에서 app_open/first_done/repeat_done/drop의 DEBUG 로그를 연결했다. 서버 전송은 없다. [현재 행동 이벤트 계약·검증](behavior-events-report.md)을 참고한다.
- 중복 방지 ID는 앱 데이터 삭제 전까지 보존한다. 임의의 캐시 상한으로 오래된 알림을 다시 NEW로 만들지 않는다. 실제 보존 기간·이관은 행동 이벤트 정책 확정 때 검토한다.

## 발송 순번 정책 (후속 단계에서 확정)

최초 알림 설정 저장 이후 성공 발송을 INSTALLATION 순번 1부터 계수한다. 프로세스·경기 변경·재생 초기화에도 이어진다. 과거 PROCESS 알림을 설치 1번으로 변환하지 않는다. 실패 발송은 성공 순번을 소비하지 않는다. 위 계약 표는 현재 기준이며 아래 87개 테스트와 실기기 로그는 행동 이벤트 연결 이전 단계의 이력이다.

어느 정책을 골라도 POST_ID는 발송 건의 고유 식별자로 유지한다. 열람 중복 판정은 순번 단독이나 게임 ID가 아니라 POST_ID로 수행한다.

## 검증

단위 테스트: 같은 경기 서로 다른 두 POST_ID, 같은 POST_ID 재전달, 회전 콜백, 저장소 재개방 및 동시 등록 1회 성공, 목록 진입 구분, 오류/가려진 상세 미기록, 같은 경기 재생 보존, 확인 저장 실패 재시도 등을 검증한다. 기존 설정·16개 판정·8개 재생 회귀 테스트도 유지한다.

실기기 검증은 연결 후 앱 실행 중 시스템 알림 탭, 프로세스가 없는 상태의 알림 탭, 서로 다른 알림 두 건, 중복 Intent 재전달, 회전을 구분해 기록한다.

## 구현 시 검증 (실기기 연결 전)

- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL. testDebugUnitTest가 새로 실행되었고 총 87개 통과(실패·오류·스킵 0).
- `git diff --check` 통과.
- 실기기: ADB 서버는 정상 기동했지만 `adb devices`에 연결 기기가 없었다. 따라서 이번 변경본의 시스템 알림 실제 탭(앱 실행 중/프로세스 종료 상태), 실제 화면 회전은 아직 미검증이다. Intent를 수동 실행한 것을 시스템 알림 탭 검증으로 간주하지 않는다.
- 기기 연결 후 확인 순서: 실제 모의 경기로 서로 다른 알림 두 건 발송 → 앱 실행 중 각각 시스템 알림 탭 → local log의 서로 다른 POST_ID와 NEW 확인 → 회전으로 NEW 추가 없음 → 같은 metadata 재전달 시 DUPLICATE → 알림을 남긴 채 앱을 백그라운드에서 종료하고 pid 부재 확인 → 시스템 알림 탭으로 콜드 상세 진입. force-stop은 알림을 제거할 수 있으므로 콜드 검증의 종료 방법과 PID 확인 결과를 함께 기록한다.


## 실기기 검증 완료 — 2026-11-24

### 환경과 방법

- `adb devices` 상태 `device`, Android API 29. 현재 `app/build/outputs/apk/debug/app-debug.apk`를 `install -r`로 설치했다.
- APK SHA-256: `83EFC5D1A3561A48CF1E3F1F96ABB7815FF22FE6062F113022010E10C100EAA6`.
- 테스트 전 DataStore 디렉터리는 `total 0`, 회전 설정은 accelerometer_rotation=0 / user_rotation=0이었다. 임시 SSG 설정으로 첫 이용을 완료하고 일반 모의 경기 `mock-001`에서 부산 관점을 선택해 실제 Android 로컬 알림을 발송했다.
- ADB UIAutomator로 시스템 알림창의 실제 개별 알림 행을 탭했다. 사용자가 별도로 누를 필요는 없었다. 아래 시스템 알림 탭 결과는 `am start` 수동 Intent 실행과 구분한다.

### 확인 결과

| 조건 | 실제 조작·증거 | 결과 |
| --- | --- | --- |
| 앱 화면 실행 중 알림 탭 | PID 14020에서 시스템 경기 시작 알림 탭. POST_ID 끝 `:7` 기록 시 상세는 여전히 재생 중, 1루 김도윤, 1회초. 초기 재생 대기/주자 없음으로 돌아가지 않음 | 통과: 같은 경기 엔진·진행 상태 보존 |
| 서로 다른 발송 건 | 같은 gameId `mock-001`의 POST_ID 끝 `:1`, `:7`, `:13`을 각각 시스템 알림창에서 탭 | 각각 NEW=true, 서로 다른 발송으로 식별 |
| 실제 회전·세로 복원 | 알림 상세에서 user_rotation 0→1→0, UIAutomator root rotation=1 확인, PID 14020 유지 | 새 NEW 로그 0건. 당시 NEW 수 3→3 |
| 목록 직접 진입 | 상세 → 목록 → 일반 경기 카드 직접 탭 | source=LIST, post=none, status=NOT_APPLICABLE, newRead=false. NEW 수 3→3 |
| 최근 앱 카드 닫음 | ClutchAlarm 카드만 위로 스와이프. pidof 결과 없음, dumpsys에서 앱 작업 없음. 남아 있던 공격 종료 알림 실제 탭 | 새 PID 15173, game=mock-001, POST_ID 끝 `:16`, NEW=true. 초기 재생 대기 상세 진입 |
| 명시적 백그라운드 프로세스 종료 | 홈으로 보낸 뒤 am kill. 첫 호출 직후에는 PID 15173이 남아 있어 종료로 판정하지 않음. 백그라운드 전환 후 재시도해 pidof 결과 없음을 확인. 남은 동점 허용 알림 실제 탭 | 새 PID 21376, game=mock-001, POST_ID 끝 `:17`, NEW=true. 초기 재생 대기 상세 진입 |
| 프로세스 종료 후 최근 작업 복원 | 홈 → am kill → PID 없음 확인. 이번에는 알림이 아니라 최근 작업의 ClutchAlarm 카드를 탭 | 새 PID 22706에서 상세 복원. NEW 수 5→5, 추가 알림 열람 기록 없음 |
| 동일 ID 보조 재전달 | 실제로 열었던 `:16`의 동일 metadata를 am start로 PID 21376에 재전달 | DUPLICATE, newRead=false. NEW 수 5 유지. 이는 실제 시스템 알림 재탭 테스트가 아니라 중복 Intent 보조 테스트임 |

현재 기기에서는 최근 앱에서 닫는 동작이 PID 종료까지 일으켰다. 따라서 '최근 앱만 닫았지만 프로세스는 살아 있는 상태'는 별도로 검증했다고 주장하지 않는다. 앱 화면 실행 중 조건과, PID 부재를 확인한 콜드 시작 조건을 구분했다. 콜드 시작은 실행 중 엔진이 없으므로 재생 위치를 이어가지 않고 해당 경기 초기 상세로 연다.

### 실제 로그

POST_ID scope UUID는 `177e2382-b13c-40cc-b4f6-d3288171ae60`이다. 프로세스가 바뀌어도 남아 있는 알림의 원래 발송 UUID와 순번이 그대로 전달되었다. 5개의 서로 다른 실제 발송 건이 NEW, 동일 ID 재전달은 DUPLICATE, 직접 진입 2회는 NOT_APPLICABLE로 기록되었다.

원본 중 앱 진입 태그만 추린 증거는 [실기기 진입 로그](notification-entry-device-log.txt)에 보관했다. UI 상태도 별도로 확인했으며 단순히 Intent를 실행한 것을 시스템 알림 탭으로 대체하지 않았다.

### 정리와 검증 범위

- force-stop은 콜드 알림 탭 검증에 사용하지 않았다. 모든 검증이 끝난 뒤 테스트 알림 정리에만 사용했다. 해당 앱 알림이 사라짐을 확인했고 다른 앱 알림은 지우지 않았다.
- 테스트 전 두 설정 파일이 없었으므로 이번 테스트에서 생성한 notification_preferences.preferences_pb와 notification_open_receipts.preferences_pb만 제거했다. 앱 전체 데이터 초기화는 하지 않았다. 재실행해 첫 이용 팀 미선택 상태 복원을 확인했다.
- 회전 설정도 원래 0/0으로 복원했다.
- 이 단계에서는 소스 변경 없이 현재 APK를 검증하고 보고서·증거 로그만 추가했다. 직전 빌드 성공·단위 테스트 87개 통과 결과는 유지하며, 불필요한 재빌드·테스트 반복은 하지 않았다. git diff --check 통과.
- 남은 범위: 다른 Android 버전·제조사에서의 최근 앱 종료 정책, 오래된 legacy 알림은 이번 물리 검증 대상이 아니다. 일반적인 영구 강제 종료/재부팅 후 알림 유지 여부를 보장하는 테스트도 아니다.
