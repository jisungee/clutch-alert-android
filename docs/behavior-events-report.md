# 행동 이벤트 구현·검증 (2026-11-24)

이번 단계는 공통 `BehaviorEventRecorder`를 통한 디버그 로그만 구현한다. `DebugBehaviorEvents`는 DEBUG 빌드에서 `ClutchBehavior` 태그에 프로세스 UUID와 JSON을 남긴다. 서버 API, 업로드 큐, 재전송은 없다. 알림 판정 규칙과 화면 구성은 유지한다.

## 이벤트 계약

| 이벤트 | 기록 시점 | 중복·제외 기준 |
| --- | --- | --- |
| `app_open` | 루트 Compose 화면의 최초 RESUME | 프로세스 공통 AtomicBoolean. 알림/런처 새 실행을 포함하고 warm 복귀·회전은 제외 |
| `first_done` | INSTALLATION 순번 1의 상세가 실제 표시되고 기존 열람 저장소가 NEW를 확정 | 열람 ID와 `first_done_recorded`를 같은 DataStore 트랜잭션에 저장. 설치당 1회. 최초 설정 저장만으로 발생하지 않으며 2번을 먼저 열어도 1번 기회 유지 |
| `repeat_done`, `notification_open` | INSTALLATION 순번 2 이상을 처음 열어 상세가 표시됨 | POST_ID별 1회. 목록·복원·중복 Intent 제외 |
| `repeat_done`, `settings_resave` | 최초 알림 설정 저장 이후 알림 설정 재저장 성공 | 변경 없이 저장해도 성공한 재저장이므로 포함. 최초 저장·팀만 변경·저장 실패는 제외 |
| `drop` | 알림 토글을 조작한 편집 흐름을 저장 없이 벗어남 | flowId별 1회. 전체/카테고리/개별/지는 중 숨기기 조작 포함. 값을 원복했어도 조작에 해당. 카테고리↔세부 및 흐름 안 팀 선택은 유지. 미조작·저장 성공 후 종료 제외 |

`drop`의 이탈은 명시적 뒤로/다른 앱 화면 이동과 Activity 종료 경로에서 처리한다. 홈 버튼으로 잠시 백그라운드로 보내면 편집 흐름이 유지되므로 즉시 이탈로 세지 않는다. OS 강제 종료·SIGKILL·전원 종료 순간의 로그 전달은 보장하지 않는다.

## 발송 순번

- 첫 알림 설정 저장 상태와 설치 UUID/성공 순번을 기존 설정 DataStore에 보존한다. 실제 `NotificationManager.notify` 요청 성공 및 DataStore 저장 후 발송 성공으로 반환한다. 권한·채널 차단·보존 상한·발송 예외는 성공 번호를 소비하지 않는다. 저장 실패 시 게시한 알림은 취소하고 ERROR로 처리한다.
- 최초 성공 발송은 1이며 프로세스·경기·재생 초기화와 독립이다. 발송 작업을 직렬화하고 재생 엔진은 suspend 발송 완료를 기다린다. 화면의 ‘이번 재생 알림’ 횟수는 기존처럼 회차별이다.
- POST_ID는 기존 발송 건 고유 ID를 유지한다. INSTALLATION 순번과 POST_ID의 숫자 부분은 서로 다를 수 있다. 재개방 중복 판정은 순번이 아닌 POST_ID로 한다.
- 기존 알림의 `PROCESS` scope를 그대로 읽는다. PROCESS 1을 설치 1로 변환하거나 행동 열람 이벤트로 사용하지 않는다. 저장 이력이 있는 기존 사용자는 새 발송부터 영속 카운터를 시작하며 과거 발송 수를 소급 추정하지 않는다.
- 앱 데이터가 비어 있는 새 설치는 새 계수 환경이다. Android 백업에서 DataStore가 복원되면 식별자와 순번도 복원될 수 있으며 백업 복원 정책은 후속 확인 대상이다. OS 알림 게시와 디스크 저장 사이 비정상 전원 종료까지 원자적 트랜잭션으로 묶는 것은 이번 디버그 검증의 범위 밖이다.

## 변경 파일

| 역할 | 파일 |
| --- | --- |
| 공통 이벤트·프로세스 중복 방지·DEBUG sink | `behavior/BehaviorEvents.kt`, `behavior/DebugBehaviorEvents.kt` |
| 저장 이력·기존 설정 이관·영속 성공 순번 | `data/NotificationMeasurementState.kt`, `data/DataStoreNotificationPreferencesRepository.kt`, `data/DataStoreNotificationPublisher.kt`, `domain/notification/NotificationPreferences.kt` |
| 표시 완료 후 NEW 열람 이벤트 | `data/DataStoreNotificationOpenRepository.kt` |
| INSTALLATION metadata·발송 완료 대기 | `notification/NotificationEntry.kt`, `notification/NotificationDispatcher.kt`, `notification/AndroidNotificationDispatcher.kt`, `domain/simulation/GameSimulationEngine.kt` |
| 화면 최초 진입·설정 저장/이탈 연결 | `MainActivity.kt`, `ui/GameApp.kt`, `ui/GameViewModel.kt`, `ui/SettingsEditor.kt` |
| 독립 QA 패키지 | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` |
| 새 검증 | `BehaviorEventTest`, `BehaviorPersistenceTest`, `BehaviorFlowTest` |

위 경로는 별도 표시가 없으면 `app/src/main/java/com/clutchalarm/app/` 기준이다.

## 빌드·단위 테스트

`./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL**. 이번 구현 후 단위 테스트가 새로 실행됐고 총 **94개 통과**, 실패/오류/스킵 0. 기존 판정·8개 로컬 경기·화면 이동·열람 회귀 테스트를 포함한다.

추가 테스트는 실제 임시 DataStore로 기본 이벤트 건수, 발송 실패 시 번호 미소비, 다른 경기/프로세스/저장소 재개방의 순번 연속성, 동시 발송 10개 순번, PROCESS 제외, 2번→1번 열람, 중복·저장 실패·미조작/성공 후 이탈을 확인한다.

## 실기기: 기존 설정을 보존한 별도 설치

- USB `adb devices`: `device`, Android API 29.
- `./gradlew.bat -PbehaviorQa=true :app:assembleDebug` 성공. 패키지 `com.clutchalarm.app.behaviorqa`, 표시 이름 **ClutchAlarm QA**를 새로 설치했다. 기본 빌드 패키지는 종전 그대로다.
- 기존 `com.clutchalarm.app`의 데이터 삭제/재설치/설정 조작 없이 검증했다. 기존 설정 파일 SHA-256은 전후 모두 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`였다. 화면 회전 설정도 원래 값 0/0으로 복구했다. QA 설치·데이터는 추가 확인을 위해 남겼다.

### 기본 흐름: 같은 프로세스

프로세스 `a7a570c7-a5d1-4952-a1f7-911633fb0df0`, PID 23358. 첫 응원팀/알림 저장 → 일반 모의 경기 임시 부산 관점 → 재생 후 일시정지로 정확히 1·2번 발송 → 시스템 알림창의 경기 시작(1번), 출루(2번)를 각각 실제 탭 → 알림 설정 재저장 → 세부 토글 조작 → 카테고리 복귀 → 저장 없이 상세 복귀.

| 이벤트 | 실제 건수 | 근거 |
| --- | ---: | --- |
| app_open | 1 | 11:22:38 |
| first_done | 1 | 11:27:17, 설치 순번 1 NEW |
| repeat_done | 2 | 11:27:19 notification_open(2번), 11:27:23 settings_resave |
| drop | 1 | 11:27:58, 설정 흐름 최종 이탈 시점 |

카테고리로만 복귀했을 때 drop은 0건이었다. 기본 흐름 원문 로그: [behavior-main-flow-device-log.txt](behavior-main-flow-device-log.txt).

### 별도 추가 검증

| 조건 | 실기기 결과 |
| --- | --- |
| 화면 회전/원위치 | rotation=1/0 실제 UI 확인, 추가 app_open/열람 이벤트 0 |
| 목록에서 일반 경기 직접 재진입 | source=LIST, NOT_APPLICABLE, 추가 이벤트 0 |
| 동일 2번 metadata 재전달 | 실제 발송된 POST_ID를 `am start --activity-single-top`으로 재전달, DUPLICATE, 추가 이벤트 0 |
| 프로세스 종료 후 런처 새 실행 | HOME → `am kill` → `pidof` 결과 없음 확인 → MAIN/LAUNCHER Intent. 새 PID 27123, app_open 1건 |
| 새 프로세스에서 초기화된 경기 재생 | 다음 성공 발송은 같은 설치 UUID의 **3번**, POST_ID의 프로세스 부분은 변경됨 |
| 남아 있는 3번 시스템 알림으로 콜드 실행 | HOME → am kill → PID 부재 확인 → 실제 시스템 알림 탭. PID 28847, 상세 표시, app_open 1건 + repeat_done(notification_open) 1건 |
| 프로세스가 바뀐 뒤 과거 2번 재전달/회전 | DUPLICATE, 추가 이벤트 0 |

추가 검증까지 누적하면 app_open **3**, first_done **1**, repeat_done **3**, drop **1**이다. 기본 완료 기준의 동일 프로세스 구간과 구분한다. [추가 검증 원문 로그](behavior-supplement-device-log.txt).

## 추가 실기기 검증: 2번 → 1번 역순 열람 (2026-11-24)

서버 명세가 아직 확정되지 않아 실제 서버 연결은 보류한다. 이번 검증도 기존 `ClutchBehavior` 공통 디버그 로그만 사용했으며 앱 소스·API·전송 코드는 변경하지 않았다.

### 환경과 데이터 보존

- `adb devices -l`: USB 기기 `device`, Samsung **SM-N960N**, **Android 10 / API 29**.
- 설치된 `com.clutchalarm.app.behaviorqa`의 기존 데이터를 앱 종료 후 로컬 tar로 백업하고, **QA 패키지만** `pm clear`하여 새 설치 계수 상태를 만들었다. 기존 사용자 앱 `com.clutchalarm.app`은 초기화하지 않았다.
- 기존 사용자 앱 설정 파일 SHA-256은 검증 전후 모두 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`로 동일하다. QA 앱은 이번 검증 데이터 상태로 남겼다.
- 이번 새 측정 구간은 PID **30777**, 프로세스 `fc26840a-51e0-4fe3-91b3-56add338afec`, 설치 ID `06e02412-161c-4025-b26c-2b9a569a2115`다. 앞선 기본 흐름과 분리하여 집계했다.

### 실행과 실제 로그

SSG 선택 → 최초 알림 설정 저장 → 일반 모의 경기의 임시 부산 관점 선택 → 1배속 재생 후 일시정지. 설치 순번 **1번 경기 시작**, **2번 출루**를 실제 발송했다. 시스템 알림창에서 **2번 출루를 먼저 탭**한 다음, 남아 있는 **1번 경기 시작을 탭**했다. 두 번 모두 상세 화면과 일시정지 상태·1루 주자 표시를 확인했다.

| 관측 시점 | first_done 누적 | repeat_done(notification_open) 누적 | 실제 판정 |
| --- | ---: | ---: | --- |
| 최초 저장 및 두 알림 발송 후 | 0 | 0 | 발송만으로 열람 이벤트 없음 |
| 11:38:48, 2번 시스템 알림 탭 후 | 0 | 1 | sequence=2, NEW |
| 11:39:07, 뒤늦게 1번 시스템 알림 탭 후 | 1 | 1 | sequence=1, NEW |
| 11:39:35~37, 같은 두 ID 재전달 후 | 1 | 1 | 각각 DUPLICATE, 추가 이벤트 0 |

2번을 먼저 열어도 `first_done` 기회가 소진되지 않았으며, 이후 1번의 첫 열람에 정확히 한 번 기록됐다. 이번 구간 전체 이벤트는 `app_open` **1**, `first_done` **1**, `repeat_done` **1**(action=`notification_open`), `drop` **0**이다. 재저장은 수행하지 않아 settings_resave는 0건이다.

최초 열람 두 건은 **실제 시스템 알림 탭**이다. 재열람은 알림이 자동 제거되므로 실제 발송된 POST_ID·순번·scope metadata를 `am start --activity-single-top`으로 재전달한 별도 중복 검사다.

로그 증거:

- [2번 먼저 연 직후의 로그](behavior-reverse-after-second-device-log.txt): first_done 0, repeat_done 1.
- [1번까지 연 직후의 로그](behavior-reverse-after-first-device-log.txt): first_done 1, repeat_done 1.
- [동일 ID 두 건 재전달까지 포함한 로그](behavior-reverse-read-device-log.txt): 두 건 모두 DUPLICATE, 건수 불변.

추가 검증 후 `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`도 **BUILD SUCCESSFUL**이었다. 앱 소스 변경이 없어 이번 실행은 45개 작업 모두 UP-TO-DATE이며, 단위 테스트를 새로 실행한 결과는 아니다. 앞서 94개 테스트를 실제 실행해 통과한 결과와 구분한다.

### Android 13 이상 권한 거부: 미검증

현재 연결된 기기는 API 29라 `POST_NOTIFICATIONS` 런타임 권한 거부 조건을 실행할 수 없다. 사용자가 **현재 기기만 사용하고 미검증으로 남기기**를 선택했다. 따라서 Android 13 이상에서 거부 시 first_done/repeat_done(notification_open)이 0건이라는 **실기기 결과는 아직 없다**. Android 10의 앱/채널 차단을 해당 권한 거부 시험의 대체 결과로 보고하지 않는다.

추후 API 33 이상 QA 앱에서: 새 설치 → 최초 설정 저장 → 권한 요청 거부 → 모의 경기 재생 → 발송이 PERMISSION_DENIED이고 신규 시스템 알림·알림 열람 이벤트가 없는지 확인한다. 이후 허용한 첫 성공 발송이 설치 순번 1인지도 함께 확인한다. 기존에 이미 발송된 알림이나 `settings_resave` 액션은 이 시험의 집계에서 분리한다.

## 남은 확인

- 2번 → 1번 역순 열람과 동일 ID 중복 방지는 위 추가 실기기 검증에서 확인했다. 기존 PROCESS 알림 제외, 다른 경기의 계수 연속성, 저장/발송 예외는 단위 테스트로 확인했고 별도 실기기 재현은 남아 있다.
- Android 13+ 권한 거부는 사용자 선택에 따라 미검증으로 남겼다. 채널 차단 및 실제 디스크 부족 실패도 이번 API 29 기기에서 재현하지 않았다.
- 서버 수집 연결·전송 성공/재시도·로그 유실 복구는 다음 단계다. 현재 로그는 영속 전송 큐가 아니다.
