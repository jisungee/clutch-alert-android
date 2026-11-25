# 한화 선별 경기 적용 및 검증 (2026-11-24)

> 한화 단독 단계의 기록입니다. 현재 10경기 연결 상태는 [전체 검증 보고서](ten-game-validation.md)를 참고하세요.

## 결과

기존 MVP 커밋 d6204eb 이후의 미커밋 변경이다. 커밋·푸시는 수행하지 않았다. 앱 목록은 기존 8개 시나리오를 유지하고 한화–키움 한 경기를 추가한 9개다. 한화 데이터 49개 레코드를 모두 재생해 원정 키움 9 : 홈 한화 10, 11회 말 종료를 확인했다.

원본 `HANWHA_2026-03-28.json`을 바이트 변경 없이 앱 assets에 복사했다. SHA-256은 `dd15dd49551cd4bad3127244e404c2b005838512ea3fc3ef50f0c71d69f242dc`이며 제공 원본과 동일하다. 다른 원본 파일은 수정하지 않았다.

## 적용 기준

- coverage가 `selected_plays_plus_official_half_inning_totals`일 때만 SelectedPlayGameAdapter를 사용한다. 기존 완전 플레이 형식은 SnapshotGameAdapter, legacy 형식은 기존 경로를 사용한다.
- 현재 선수는 이 형식에 한해 방금 플레이한 선수다. 상세에는 ‘플레이 타자’로 표시한다. 알림은 원문 설명을 함께 사용하고, 합계 보정에서는 선수·타점을 만들지 않는다. 폭투는 ‘강백호 타석 폭투로 페라자 득점’ 원문을 유지한다.
- delay_sec를 오버플로 검사와 함께 누적한다. 한화는 0~240,000ms이며 직전 after가 다음 before다. 생략된 플레이·득점 시점·역전 이력을 생성하지 않는다.
- 이닝 잔여 득점도 RUN_SCORED로 판정해 득점/실점/빅이닝 구독과 기존 한 플레이 1건·중복 방지를 적용한다. 현재 반이닝의 3점 도달에만 빅이닝이 매칭된다.
- B/S/O null, 주자 상태 known=false, 선수 null, 시각 null은 미상이다. 0아웃·빈 베이스·가짜 시각·이전 선수로 채우지 않는다. 주자 미상은 회색 베이스와 ‘주자 미상’으로 표시한다.
- 판독의 명시적인 유지/번복 표현만 분류한다. 알 수 없는 결과는 결과 미상 알림으로 처리한다. 수치 상태는 원본 그대로이며 번복으로 확인된 판독만 점수 감소를 허용한다.
- 끝내기는 원문, 9회 이후 말 공격 홈팀 리드 득점, 바로 다음 동일 점수/이닝의 GAME_END 및 final_score 일치를 함께 확인한다. 한화 48번이 해당한다.
- 한화 35번의 원문 ‘역전 3점 홈런’/sub_types와 확정 점수(4:7→7:7)가 다르다. 원본 및 상세 원문은 보존하고 알림 본문은 ‘심우준 3점 홈런…동점’으로 처리한다. 기존 우선순위에 따라 제목/유형은 HOME_RUN이다. 숨김 ON이면 이 동점 플레이도 숨긴다.
- 뒤처짐 이력은 관측된 점수만 반영한다. 20번부터 뒤처짐, 35·46번 동점에서도 유지, 48번 앞섬에서 해제한다. 종료까지 앞서지 못하는 경우 기존 종료 숨김 규칙을 유지한다.
- 기존 BehaviorEventRecorder, app_open/first_done/repeat_done/drop 발생 지점·기준과 알림 ID/설치 순번 코드는 수정하지 않았다.

## 빌드 및 단위 테스트

JDK17/SDK36. 공통 옵션은 `--rerun-tasks --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"`이다.

| 구성 | 명령 (공통 옵션 추가) | 결과 |
|---|---|---|
| QA debug | `.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest` | 성공, 139개, 실패/오류/스킵 0 |
| 일반 debug 및 release | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest` | 성공, 각 139개, 실패/오류/스킵 0 |

최종 QA는 20초/45개 작업, 일반+release는 1분 40초/102개 작업이 실제 실행됐다. 테스트를 새로 실행했다. 기존 134개에 선별 데이터 검증 5개를 추가했다. 전체 9경기의 열기·완주·초기화 통합 테스트, 기존 알림 문구/토글/중복·인증·설정·행동 이벤트 회귀가 통과했다. 초기 테스트 중 카탈로그 중복 및 새 경기 시간/기대값 오류는 수정한 뒤 전체 재실행했다.

추가로 앱에서 컴파일한 같은 어댑터를 JVM에서 실행해 제공된 10개 원본 모두 변환 성공과 각 final_score/FINISHED 일치를 확인했다(374개 레코드). 나머지 9경기는 앱 카탈로그에 추가하거나 실기기로 재생하지 않았다. 검사 도구의 초기 라이브러리 버전 불일치는 동일 1.9.0 런타임 사용 후 해결했으며 앱 빌드의 문제는 아니었다.

## 실기기 검증

Samsung SM-N960N, Android 10/API29, adb 상태 device. 기존 `com.clutchalarm.app.behaviorqa`에 업데이트 설치했으며 앱 데이터 삭제·신규 가입·설정 재저장을 하지 않았다. 자동 로그인된 기존 설치에서 홈→경기 목록→한화 상세, 이번 경기 임시 한화 선택, 10x 재생을 진행했다.

- 49개 이벤트 완주, 11회 말 키움 9:한화 10, 재생 완료 표시 확인.
- B/S/O와 주자·선수·시각 미상 표시를 시작/종료 화면에서 확인.
- 전역 응원팀 키움과 전역 테마가 유지되고, 경기 알림 팀만 한화로 선택된 화면 확인.
- ‘이번 재생 알림 (48)’과 시스템 발송 성공 이력 확인. PID2250의 ClutchNotificationPost 48건, 설치 순번 21~68 연속, post ID 중복 없음. 원본 3번 상대 출루는 기본 OFF로 제외된다.
- 마지막 끝내기 플레이 본문에 ‘강백호 우중간 안타, 대주자 최유빈 끝내기 득점’과 한화 3득점 빅이닝, 10:9가 표시됨을 확인했다. 기존 제목 선택 정책에 따라 해당 제목은 ‘역전’이다.
- 이 PID의 ClutchBehavior는 app_open 1건, 알림 열람/설정 변경 관련 이벤트 0건이다. 이번 재생에서는 시스템 알림을 누르거나 설정을 저장하지 않았으므로 first_done/repeat_done/drop을 발생시키지 않았다. 이벤트 전체 완료 흐름의 재실기기 검증을 했다고 주장하지 않는다.

시스템 알림 48건은 재생 전체의 발송 성공 합계이며 동시에 알림창에 48건을 유지한다는 뜻이 아니다. 기존 보존/정리 정책을 그대로 사용한다. 전 항목의 개별 본문을 실기기에서 모두 펼쳐 확인한 것은 아니다. 아래 목록은 JVM의 원본 ID별 후보와 실기기 발송 총수·이력을 대조한 결과다.

## 발생 알림 전체 목록

관점 HANWHA, 지는 중 숨김 OFF, 상대 출루 OFF, 나머지 구독 ON. 49개 후보 중 48개 발송, 상대 출루 1개 제외. 전체 ON이면 49개, 빅이닝만 ON이면 20/35/48번 3개다. 숨김 ON이면 20~47번은 모두 숨기고 48번에서 재개한다(JVM 검증).

JSON 목록: [selected-play-notifications.json](selected-play-notifications.json). 이 파일의 event_id는 경기 레코드 ID이며 행동 이벤트 전송 ID가 아니다.

| 원본 ID | 유형 | 알림 내용 | 기본 설정 발송 |
|---:|---|---|---|
| 1 | `GAME_STARTED` | 1회초 · KIWOOM 대 HANWHA 경기 시작 (선발 알칸타라 대 헤르난데스) · 0 : 0 | 성공 |
| 2 | `PITCHER_STAT` | 1회초 · 헤르난데스, 이주형 삼진 처리 · 0 : 0 | 성공 |
| 3 | `OPPONENT_ON_BASE` | 1회초 · 안치홍 좌익수 방향 2루타 출루 · 0 : 0 | 상대 출루 OFF로 제외 |
| 4 | `OFFENSE_STARTED` | 1회말 · 1회말 시작 · 0 : 0 | 성공 |
| 5 | `REACHED_BASE` | 1회말 · 페라자 중전 안타 출루 · 0 : 0 | 성공 |
| 6 | `REACHED_BASE` | 1회말 · 문현빈 우익수 방향 2루타 출루 · 0 : 0 | 성공 |
| 7 | `RUN_SCORED` | 1회말 · 강백호 타석 폭투로 페라자 득점 · 한화 이글스! 리드 · 1 : 0 | 성공 |
| 8 | `DEFENSE_STARTED` | 2회초 · 2회초 시작 · 1 : 0 | 성공 |
| 9 | `OFFENSE_STARTED` | 2회말 · 2회말 시작 · 1 : 0 | 성공 |
| 10 | `DEFENSE_STARTED` | 3회초 · 3회초 시작 · 1 : 0 | 성공 |
| 11 | `OFFENSE_STARTED` | 3회말 · 3회말 시작 · 1 : 0 | 성공 |
| 12 | `VIDEO_REVIEW` | 3회말 · HANWHA 요청 비디오 판독: 심우준, 스윙 유지 · 1 : 0 | 성공 |
| 13 | `REACHED_BASE` | 3회말 · 오재원 좌전 안타 출루 · 1 : 0 | 성공 |
| 14 | `REACHED_BASE` | 3회말 · 페라자 우익선상 안타 출루 · 1 : 0 | 성공 |
| 15 | `RUN_SCORED` | 3회말 · 문현빈 2루수 땅볼 때 오재원 득점 · 한화 이글스! 1점 득점 · 2 : 0 | 성공 |
| 16 | `DEFENSE_STARTED` | 4회초 · 4회초 시작 · 2 : 0 | 성공 |
| 17 | `OFFENSE_STARTED` | 4회말 · 4회말 시작 · 2 : 0 | 성공 |
| 18 | `HOME_RUN` | 4회말 · 채은성 솔로 홈런 (110m) · 한화 이글스 솔로 홈런! 1점 득점 · 3 : 0 | 성공 |
| 19 | `DEFENSE_STARTED` | 5회초 · 5회초 시작 · 3 : 0 | 성공 |
| 20 | `RUN_ALLOWED` | 5회초 · 5회초 KIWOOM 4득점 (공식 이닝 합계 기준) · 키움 히어로즈! 역전 허용 · 키움 히어로즈 4득점 빅이닝 · 3 : 4 | 성공 |
| 21 | `OFFENSE_STARTED` | 5회말 · 5회말 시작 · 3 : 4 | 성공 |
| 22 | `DEFENSE_STARTED` | 6회초 · 6회초 시작 · 3 : 4 | 성공 |
| 23 | `OFFENSE_STARTED` | 6회말 · 6회말 시작 · 3 : 4 | 성공 |
| 24 | `DEFENSE_STARTED` | 7회초 · 7회초 시작 · 3 : 4 | 성공 |
| 25 | `RUN_ALLOWED` | 7회초 · 7회초 KIWOOM 1득점 (공식 이닝 합계 기준) · 키움 히어로즈! 1실점 허용 · 3 : 5 | 성공 |
| 26 | `OFFENSE_STARTED` | 7회말 · 7회말 시작 · 3 : 5 | 성공 |
| 27 | `RUN_SCORED` | 7회말 · 7회말 HANWHA 1득점 (공식 이닝 합계 기준) · 한화 이글스! 1점 득점 · 4 : 5 | 성공 |
| 28 | `DEFENSE_STARTED` | 8회초 · 8회초 시작 · 4 : 5 | 성공 |
| 29 | `RUN_ALLOWED` | 8회초 · 8회초 KIWOOM 2득점 (공식 이닝 합계 기준) · 키움 히어로즈! 2실점 허용 · 4 : 7 | 성공 |
| 30 | `OFFENSE_STARTED` | 8회말 · 8회말 시작 · 4 : 7 | 성공 |
| 31 | `REACHED_BASE` | 8회말 · 채은성 볼넷 출루 · 4 : 7 | 성공 |
| 32 | `REACHED_BASE` | 8회말 · 황영묵 볼넷 출루 · 4 : 7 | 성공 |
| 33 | `VIDEO_REVIEW` | 8회말 · KIWOOM 요청 비디오 판독: 황영묵, 노스윙 유지 · 4 : 7 | 성공 |
| 34 | `PITCHER_CHANGE` | 8회말 · 김재웅에서 배동현(으)로 투수 교체 · 4 : 7 | 성공 |
| 35 | `HOME_RUN` | 8회말 · 심우준 3점 홈런 (115m) · 한화 이글스 3점 홈런! 동점 · 한화 이글스 3득점 빅이닝 · 7 : 7 | 성공 |
| 36 | `DEFENSE_STARTED` | 9회초 · 9회초 시작 · 7 : 7 | 성공 |
| 37 | `OFFENSE_STARTED` | 9회말 · 9회말 시작 · 7 : 7 | 성공 |
| 38 | `DEFENSE_STARTED` | 10회초 · 10회초 시작 · 7 : 7 | 성공 |
| 39 | `OFFENSE_STARTED` | 10회말 · 10회말 시작 · 7 : 7 | 성공 |
| 40 | `DEFENSE_STARTED` | 11회초 · 11회초 시작 · 7 : 7 | 성공 |
| 41 | `RUN_ALLOWED` | 11회초 · 11회초 KIWOOM 2득점 (공식 이닝 합계 기준) · 키움 히어로즈! 2실점 허용 · 7 : 9 | 성공 |
| 42 | `OFFENSE_STARTED` | 11회말 · 11회말 시작 · 7 : 9 | 성공 |
| 43 | `PITCHER_CHANGE` | 11회말 · 박윤성에서 가나쿠보(으)로 투수 교체 · 7 : 9 | 성공 |
| 44 | `REACHED_BASE` | 11회말 · 심우준 우중간 안타 출루 · 7 : 9 | 성공 |
| 45 | `RUN_SCORED` | 11회말 · 문현빈 좌중간 2루타, 심우준 득점 · 한화 이글스! 1점 득점 · 8 : 9 | 성공 |
| 46 | `TIED` | 11회말 · 노시환 좌전 안타, 문현빈 득점 (판독 후 확정) · 한화 이글스! 동점 · 9 : 9 | 성공 |
| 47 | `VIDEO_REVIEW` | 11회말 · KIWOOM 요청 비디오 판독: 노시환, 세이프 유지 · 9 : 9 | 성공 |
| 48 | `COMEBACK` | 11회말 · 강백호 우중간 안타, 대주자 최유빈 끝내기 득점 · 한화 이글스! 끝내기 승리 · 한화 이글스 3득점 빅이닝 · 10 : 9 | 성공 |
| 49 | `GAME_ENDED` | 11회말 · 경기 종료: KIWOOM 9 - 10 HANWHA, HANWHA 승리 · 10 : 9 | 성공 |

## 변경 파일과 남은 범위

주요 소스: data/SelectedPlayGameAdapter.kt(신규), MockGameRepository.kt, domain/model/GameState.kt·GameEvent.kt, NotificationRuleEngine.kt, ui/GameDetailScreen.kt·ReplayNotificationSections.kt. 리소스: 한화 원본 asset, catalog.json. 테스트: SelectedPlayGameAdapterTest.kt, MockScenarioIntegrationTest.kt. 설명: README, server-handoff, notification-policy-review 및 이 보고서/알림 목록.

다른 9경기의 실제 화면·알림 전체 검증, 다른 제조사/API 및 확대 글자 재촬영, 새 원본에 나타나는 추가 판독 표현과 과거 반이닝 점수 정정은 별도 확인 대상이다. 현재 알려진 유지/번복 표현 밖의 문장은 상태를 추정하지 않는다. 선수 ID는 기존과 같이 팀+이름 기반이며 안정적인 원본 ID가 추가되면 교체해야 한다. 실제 서버 연결과 배포 서명은 이번 범위에 포함하지 않았다.

실기기 스크린샷은 외부 시각화 폴더의 selected-play-before.png / selected-play-completed.png / selected-play-history.png이며 Git에는 포함하지 않는다. APK·로그·임시 검사 Java 파일은 무시된 build 또는 *.log이며 커밋하지 않는다. 이전 MVP 최종 APK를 이번 변경판으로 간주하지 않는다.
