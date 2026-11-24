# ⑩ 마이페이지 구현·검증 (2026-11-24)

하단 탭을 홈·경기·마이로 확장했다. 시안 ⑩의 빨간 상단, 프로필 카드, 응원팀 변경·알림 설정 메뉴와 선택된 마이 탭을 반영했다. 이메일 대신 ‘클러치알림 체험 중’을 표시하며 로그인·회원가입·로그아웃·계정 삭제와 계정 정보 메뉴는 제공하지 않는다. 미구현 순위 탭이나 연결할 정책 문서가 없는 메뉴는 추가하지 않았다.

## 저장과 이동

- 화면은 `notificationSettings.preferences`의 **저장된 값**만 표시한다. 현재 응원팀 이름과 설정된 알림 수(16개 토글 기준)를 보여준다. 팀 미선택인 기존 사용자에게는 ‘선택한 팀 없음’으로 표시한다.
- 응원팀 변경은 기존 `showTeamSelection`, `chooseTeam`, `saveSettings` 및 TeamSelectionScreen을 그대로 사용한다. 알림 설정은 기존 카테고리/세부 편집 화면과 저장 함수를 재사용한다. 저장소와 알림 판정은 변경하지 않았다.
- 설정은 마이페이지 위에 열리므로 저장 성공·취소 모두 마이페이지로 복귀한다. 성공하면 저장된 StateFlow 값으로 즉시 다시 표시하며, 취소하면 편집값을 버리고 기존 값이 유지된다. 실패 시 기존 화면의 편집값·재시도 동작을 유지한다.
- 마이페이지의 시스템 뒤로는 홈으로 이동한다. 마이페이지 위에서 시스템 알림 상세가 열리면 상세 뒤로는 마이페이지로 복귀한다. Activity의 기존 MainScreen 저장/복원으로 MY_PAGE도 처리한다. 앱을 새로 여는 기본 화면은 기존대로 홈이다.

## 변경 파일

| 파일 | 변경 |
| --- | --- |
| `app/src/main/java/com/clutchalarm/app/ui/MyPageScreen.kt` | 저장된 응원팀·알림 수, 체험 프로필, 두 메뉴, 프로필 아이콘 |
| `app/src/main/java/com/clutchalarm/app/ui/GameApp.kt` | 마이 탭, 화면 렌더링, 상단/탭 색상과 상세 복귀 문구 |
| `app/src/main/java/com/clutchalarm/app/ui/GameViewModel.kt` | MY_PAGE 경로와 showMyPage, 뒤로 이동 |
| `app/src/test/java/com/clutchalarm/app/ui/BehaviorFlowTest.kt` | 마이페이지 저장·취소·재로딩·알림 상세 복귀·행동 이벤트 회귀 테스트 2개 |

## 행동 이벤트

기존 공통 기록기를 그대로 사용한다. 마이페이지/탭 클릭 자체에 새로운 이벤트는 없다.

| 조작 | 결과 |
| --- | --- |
| 응원팀만 변경/저장 | settings_resave나 drop 아님 |
| 알림 설정에 들어갔다가 조작 없이 복귀 | drop 없음 |
| 알림 토글 조작 후 세부→카테고리 이동 | 동일 편집 흐름, drop 없음 |
| 그 흐름에서 저장 없이 마이페이지 복귀 | flowId별 drop 1건, 저장된 값 유지 |
| 최초 저장 이후 알림 설정 저장 성공 | repeat_done, action=settings_resave 1건 |
| 저장 성공 후 편집 흐름 종료 | drop 없음 |

first_done과 notification_open 기준·설치 단위 순번은 변경하지 않았다. 서버 연결은 계속 보류 상태다.

## 빌드·단위 테스트

최종 `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`: **BUILD SUCCESSFUL**, **96개 통과**, 실패/오류/스킵 0. `testDebugUnitTest`는 UP-TO-DATE가 아니라 **새로 실행**됐다.

신규 테스트는 마이페이지에서 저장 전 팀이 바뀌지 않음, 저장·취소 후 복귀 위치, 저장 후 재로딩, 마이페이지 위 알림 상세의 복귀, 미조작 이탈 제외, 카테고리 이동 중 drop 제외, 최종 이탈 drop 1건, 재저장 repeat_done 1건 및 저장 뒤 drop 제외를 확인한다. 기존 16개 판정·8개 로컬 경기·홈→목록→상세·기존 열람 테스트를 포함한다.

## 실기기 검증

Samsung SM-N960N, Android 10(API 29), USB `device`. `-PbehaviorQa=true`로 별도 QA 앱만 업데이트했고 기존 사용자 앱은 건드리지 않았다. 기존 앱 설정 파일 SHA-256은 전후 모두 `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`로 동일하다.

| 실제 조작 | 관측 |
| --- | --- |
| 홈 하단 마이 탭 | 마이페이지, SSG 랜더스, 15/16개 사용 표시 |
| 응원팀 변경 → KIA 선택 → 시스템 뒤로 | 마이페이지로 복귀, SSG 유지 |
| 다시 KIA 선택 → 응원팀 저장 | 마이페이지로 복귀, KIA 즉시 표시; 행동 이벤트 추가 없음 |
| 알림 설정 → 흐름 세부의 경기 시작 토글 OFF → 뒤로 | 카테고리만 복귀, drop 없음 |
| 다시 뒤로, 저장 없이 마이페이지 복귀 | 15/16 유지, drop 1건 |
| 다시 경기 시작 OFF → 설정 저장 | 마이페이지에 14/16 즉시 표시, repeat_done(settings_resave) 1건, 추가 drop 없음 |
| 최종 색상/아이콘 QA 업데이트 후 다시 마이 진입 | KIA·14/16 유지, 빨간 선택 탭과 레이아웃을 스크린샷으로 확인 |

이벤트 검사 구간: PID 31160, 프로세스 `f6ce393d-9084-45f5-b834-40a28acee6a1`. app_open 1건, drop 1건(11:48:54), repeat_done(settings_resave) 1건(11:49:02), first_done 0건. 최종 외형 QA 업데이트는 별도 프로세스이므로 이 구간 집계와 분리했다. [원문 로그](mypage-device-log.txt).

실제 저장 실패/디스크 부족, 글자 확대, 마이페이지의 회전·프로세스 복원 및 마이페이지 위 **시스템 알림 탭**은 이번 실기기에서 별도 재현하지 않았다. 해당 알림 복귀 경로는 단위 테스트로 확인했다. Android 13 이상 권한 거부는 앞선 보고서와 같이 미검증이다.
