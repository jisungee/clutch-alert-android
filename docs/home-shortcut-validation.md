# 홈 응원팀 바로가기 및 합성 경기 제거 검증

기준일 2026-11-24. 기존 확정 커밋 d6204eb 이후의 미커밋 작업이다. 커밋·푸시는 하지 않았다. 이전 MVP APK와 이번 변경판을 구분한다.

## 구현

- 모든 Android 앱(일반/QA/릴리스)은 main/assets/games/catalog.json의 실제 경기 기반 JSON 10개만 사용한다. QA 전용 합성 목록·asset 소스 경로·synthetic 표시 분기를 제거했다.
- 합성 8개는 내용을 보존해 src/test/resources/games로 옮겼다. 테스트 전용 test-catalog.json은 합성8+실제10을 참조하며 JVM 회귀에서만 명시적으로 주입한다. Android AssetManager 생성자는 항상 실제 10개 카탈로그를 사용한다. 어떤 APK에도 test-catalog나 합성 JSON을 넣지 않는다.
- 홈의 흰색 주요 카드 버튼을 ‘내 응원팀 경기 보러가기’로 변경하고 날짜·상대 구단을 함께 표시했다. ‘전체 경기 보러가기’는 기존 경기 선택 화면으로 이동한다. 구단별 그라데이션, 공식 로고, 반투명 안내 카드, 색상 팔레트 파일은 보존했다.
- 목록 상태의 homeShortcut(teamId)는 추천 gameId/date/opponent를 공급한다. 클릭 시 GameViewModel.openRecommendedGame이 최신 저장 응원팀을 다시 조회해 selectGame의 LIST 경로로 상세를 연다. 자동 play 호출이나 notification-open 판정은 없다. 뒤로 가면 홈이다.
- 홈 추천/테마는 편집 중 draft가 아니라 저장 성공한 preferences를 사용한다. 미저장 이탈은 이전 추천을 유지하며 저장 시 즉시 변경한다. 로딩/추천 없음에서는 주요 버튼을 비활성화하고 전체 목록 경로를 유지한다.
- 기존 16개 알림 구독, 합계 득점, 미상 상태, 뒤처짐 이력, 중복 방지, 인증·세션·행동 이벤트 코드는 변경하지 않았다.

## 10개 구단 바로가기 대상

단위 테스트와 실기기에서 각각 저장→홈 표시→바로가기 상세 ID→재생 대기→뒤로 가기를 확인했다.

| 저장 응원팀 | 날짜 | 상대 팀 | 상세 gameId | 실기기 상태 |
|---|---|---|---|---|
| 두산 | 2026-03-29 | NC | `20260329_NC_DOOSAN_01` | IDLE / LIST |
| 한화 | 2026-03-28 | 키움 | `20260328_HANWHA_KIWOOM_01` | IDLE / LIST |
| KIA | 2026-04-05 | NC | `20260405_KIA_NC_01` | IDLE / LIST |
| 키움 | 2026-04-01 | SSG | `20260401_SSG_KIWOOM_01` | IDLE / LIST |
| KT | 2026-03-28 | LG | `20260328_LG_KT_01` | IDLE / LIST |
| LG | 2026-04-01 | KIA | `20260401_LG_KIA_01` | IDLE / LIST |
| 롯데 | 2026-03-28 | 삼성 | `20260328_SAMSUNG_LOTTE_01` | IDLE / LIST |
| NC | 2026-03-28 | 두산 | `20260328_NC_DOOSAN_01` | IDLE / LIST |
| SSG | 2026-03-28 | KIA | `20260328_SSG_KIA_01` | IDLE / LIST |
| 삼성 | 2026-04-01 | 두산 | `20260401_SAMSUNG_DOOSAN_01` | IDLE / LIST |


주요 버튼은 경기 결과를 표시하지 않는다. 날짜와 상대는 catalog와 해당 경기 양 팀에서 계산하며 최종 점수는 전달하지 않는다.

## 빌드·단위 테스트

JDK17/SDK36, --max-workers=1, -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" 사용.

| 구성 | 실행 명령(공통 메모리 옵션 생략) | 결과 |
|---|---|---|
| QA | `.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest --rerun-tasks` | 성공45초, 45개 작업 실제 실행, 149개 테스트 실패/오류/스킵0 |
| 일반+릴리스 | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest --rerun-tasks` | 성공2분, 102개 작업 실제 실행, 구성별149개 테스트 실패/오류/스킵0 |

[테스트 집계](home-shortcut-test-results.json). 이전 145개에 HomeShortcutTest 4개를 추가했다. 10구단 대상·날짜·상대, 바로가기 이후 250초 경과에도 IDLE/발송0/열람0, 미저장/저장/모델 복원, 명시적 재생·알림 복귀·동일ID 중복, 추천 없음의 무동작을 검증했다. 기존 8개 합성 시나리오와 실제 10경기/374개 이벤트 완주·최종 점수·352개 기본 구독 판정, 행동 이벤트 4종 회귀도 전체 테스트에서 통과했다. 이번에 10경기 전체를 실기기로 다시 완주한 것은 아니며 이전 [10경기 실기기 결과](ten-game-validation.md)와 구분한다.

## APK 데이터 검사

세 APK를 ZIP으로 열어 assets/games의 내용과 원본 bytes를 확인했다. 모든 구성에서 원본10+catalog.json, 합성0/test-catalog0/qa-catalog0이다. 제공 원본10 모두 APK 내부까지 바이트 일치한다. 합성 8개도 HEAD의 원래 내용과 비교해 보존을 확인했다(체크아웃 줄바꿈 정규화 비교).

| 구성 | 실제 경기 / 합성 경기 | SHA-256 |
|---|---|---|
| qa | 10 / 0 | `eee6c7eb1a3d87aade1bc2fb160f25f1584d565b2ef5534c4bafc95df63208a8` |
| debug | 10 / 0 | `ddabdc86fce278288a78619a063fb8c4fb9a9dc407db2f2bd14387298938f56d` |
| release | 10 / 0 | `5f26031c645c4f5a3dd3be5eb7d19ba2c8e7bf9d109ed51ab29c6c14ab43da8d` |


QA APK: build/home-shortcut-qa/ClutchAlarm-home-shortcut-QA.apk. 릴리스는 unsigned다. 실제 인증 API나 배포 서명을 추가하지 않았다.

## 실기기

Samsung SM-N960N / Android10(API29), adb device. 기존 QA 패키지 업데이트 설치로 계정·세션·알림 설정을 유지했다. 시작 시 저장 응원팀은 삼성이다. 10구단을 각각 저장해 위 표의 대상/날짜/상대를 화면과 ClutchNotificationOpen의 gameId로 대조했으며, 모두 재생 대기로 열렸다. 마지막에 삼성으로 되돌려 저장했다. 알림 토글은 변경하거나 재저장하지 않았다.

1. 삼성 홈에서 주요 버튼의 2026.04.01·두산 상대를 확인했다. 바로가기로 0:0·재생 대기·최신 상황 미시작 상태가 열리고 뒤로 가면 홈으로 돌아왔다.
2. 한화를 선택하고 저장 없이 뒤로 나가면 삼성 추천이 유지됐다. 각 구단 저장 후에는 홈 날짜/상대/그라데이션과 실제 대상이 즉시 바뀌었다.
3. QA force-stop 후 pidof 공백을 확인하고 앱 아이콘 경로로 재실행했다. 자동 로그인 후 삼성 홈, 같은 추천 날짜·상대가 복원됐다.
4. 바로가기로 상세를 연 뒤 ‘모의 경기 재생하기’를 명시적으로 눌렀을 때만 진행과 실제 시스템 발송이 시작됐다. 1x 재생·일시정지·재개를 확인했다.
5. 실행 중인 앱에서 시스템 알림421을 실제로 눌러 같은 삼성 상세로 복귀했다. 최신 경기 상황/점수/일시정지/1x 표시가 유지됐고 NEW 열람이 기록됐다.
6. 추가 시스템 알림을 남기고 HOME 후 프로세스 종료를 시도했다. 첫 am kill은 PID가 남아 cold 검증으로 인정하지 않았다. 그 상태의423번 탭은 두 번째 warm 열람이다. 이후 종료를 다시 수행해 pidof 공백을 실제 확인하고 남아 있던422번 알림으로 새 프로세스31050이 시작되어 자동 로그인 후 삼성 상세가 열림을 확인했다. 최근 앱 닫기나 force-stop으로 알림을 없앤 조건으로 대체하지 않았다. 검증 스크립트는 am kill 후에도 PID가 남으면 QA UID의 kill -9로 해당 QA 프로세스만 종료하는 보완 경로를 사용한다.
7. 새 프로세스의 상세는 재생 대기 상태였다. 이전 프로세스의 재생 위치 복원을 주장하지 않는다. 뒤로 홈→전체 경기 보러가기→실제10개 목록→뒤로 홈을 확인했다. 기기는 삼성 홈으로 마무리했다.

## 행동 이벤트 실측

아래는 서로 다른 프로세스의 로그를 분리한 결과다. 이미 사용한 설치이며 순번1 알림 시험은 아니다. first_done이0인 것은 정상이다.

| 검증 단계 | app_open | first_done | repeat_done(notification_open) | drop | 발송 | NEW 열람 |
|---|---:|---:|---:|---:|---:|---:|
| 10구단 저장·바로가기·뒤로 / 재생 없음 | 1 | 0 | 0 | 0 | 0 | 0 |
| 아이콘 재실행 후 명시적 재생·warm 알림 탭 | 1 | 0 | 2 | 0 | 3 | 2 |
| PID 부재 확인 후422번 알림 새 실행 | 1 | 0 | 1 | 0 | 0 | 1 |

발송 순번은421~423으로 이어졌다.421/423/422를 각각 처음 연 것이며 같은 ID를 반복 집계한 결과가 아니다. 응원팀만 저장한 흐름은 settings_resave나 drop으로 기록되지 않았다. 새 설치의 first_done 전체 흐름과 회전/동일 ID 재전달은 이번 실기기에서 재실행하지 않았으며 기존 회귀 테스트와 이전 보고서 기준을 유지한다. [기기 대상/로그 요약](home-shortcut-device-results.json).

## 변경 파일과 남은 확인

- app/build.gradle.kts, data/MockGameRepository.kt: 앱/테스트 카탈로그 경계.
- domain/repository/GameRepository.kt, ui/GameViewModel.kt: 합성 표시 메타데이터 제거와 홈 추천 상태/직접 진입 액션.
- ui/HomeScreen.kt·GameApp.kt·GameSelectionScreen.kt: 주요 바로가기/전체 목록과 합성 UI 제거.
- src/test/resources/games: 보존한 합성8+test-catalog. 기존 회귀 테스트는 이 카탈로그를 명시적으로 사용한다.
- HomeShortcutTest.kt(신규), RealGameCatalogTest 및 기존 fixture 로더 조정.
- README·server-handoff·전달 목록과 이 보고서/집계.

최신 스크린샷은 외부 시각화 폴더 home-shortcut/gallery.html에 보관한다. 화면 전환 애니메이션 도중의 최종 홈 프레임 대신 안정화한 final-home-stable.png를 사용한다. APK·원시 기기 로그·도구 스크립트는 무시된 build에 두고 Git에 넣지 않는다.

이번 기기는 Android10 한 대다. Android13+ 권한과 새 홈의1.3배 글자 크기 실기기 재검증, 신규 설치 전체 행동 이벤트 흐름, 서버 연동은 이번에 재실행하지 않았다. 기존 미연결 인증/수집 API와 영속 이벤트 큐/재시도 범위도 그대로다. 현재 수정으로 인한 인계 차단 문제는 발견하지 않았다.
