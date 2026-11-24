# 구단 테마 가독성 개선

2026-11-24. 이전 로고·테마 적용 후 넓은 고채도 배경을 줄이는 후속 작업이다. 공식 SVG·PNG 및 `brandColor` 원색은 변경하지 않았다.

## 색상 역할

`TeamBrand.kt`와 Compose `TeamPalette`는 동일한 다섯 역할을 제공한다.

| 역할 | 사용 |
|---|---|
| brandColor | 제공받은 공식 상징색 원본 보존 |
| primaryColor | 구단색에 중립 청회색 35%를 혼합한 버튼·활성 탭·배지·토글·배속·강조 텍스트 |
| onPrimaryColor | 강조색 위 흰색 글자·아이콘 |
| primaryContainer | 공식 구단색 6%·흰색 94%의 옅은 헤더·안내 카드·선택 영역 |
| onPrimaryContainer | 옅은 구단색 위 대비가 확보된 강조색 텍스트 |

기본 배경은 `#F7F8FA`, 일반 카드는 흰색이다. 홈 전체의 진한 그라데이션을 제거했고 홈·마이페이지 상단은 옅은 컨테이너로 바꿨다. 시작·로그인·가입의 브랜드 레드 및 공통 오류·탈퇴 위험색은 유지한다. 공식 로고는 무착색 그대로 표시한다.

색상 계산은 Android에 의존하지 않는 순수 Kotlin이다. 강조색이 흰색·기본 배경·중립 카드·구단 컨테이너 중 하나에서 4.5:1 미만이면 명도를 단계적으로 낮춘다. 글자 크기에 관계없이 이 기준을 적용하므로 큰 텍스트 3:1 목표도 충족한다. 비활성 컨트롤·공식 로고 아트 자체의 작은 글자 대비는 이 검증의 대상이 아니다.

| 구단 | brandColor | primaryColor | primaryContainer | 컨테이너 텍스트 대비 |
|---|---|---|---|---|
| 키움 | #570514 | #572534 | #F4F0F0 | 10.80:1 |
| 삼성 | #074CA1 | #23538F | #F0F4F9 | 7.03:1 |
| KT | #000000 | #1E2127 | #EFEFEF | 14.03:1 |
| LG | #C40037 | #9E214A | #FBEFF3 | 6.77:1 |
| KIA | #EA0029 | #B62141 | #FDEFF2 | 5.73:1 |
| 두산 | #1A1748 | #2F3056 | #F1F1F4 | 11.10:1 |
| SSG | #CE0E2D | #A42B44 | #FCF0F2 | 6.28:1 |
| 롯데 | #041E42 | #213552 | #EFF1F3 | 10.94:1 |
| NC | #315288 | #3E577F | #F2F4F7 | 6.63:1 |
| 한화 | #FC4E00 | #B24C23 | #FEF4EF | 4.91:1 |

`onPrimaryColor`는 모두 #FFFFFF, `onPrimaryContainer`는 각 primaryColor다. [전체 값·계산 대비](soft-team-palette.json). 지정한 모든 표면에서 강조 텍스트의 최저 대비는 4.709:1, 버튼의 흰 글자 대비는 최저 5.315:1이다. 일반 본문 #172238·보조 본문 #555E70도 위 표면에서 4.5:1 이상임을 테스트한다. 수치는 sRGB WCAG 상대휘도 계산이며 실기기 사진에서 측정한 값은 아니다.

## 기능 경계·빌드

테마 입력은 계속 저장 완료된 응원팀 ID다. 편집 중 선택·저장 없이 이탈·상세 임시 팀이 전역 테마를 바꾸지 않도록 기존 저장 및 이동 코드를 유지했다. 변경 파일은 `TeamBrand.kt`, `TeamBrandTest.kt`, `ClutchTheme.kt`, `HomeScreen.kt`, `GameApp.kt`, `MyPageScreen.kt`, `TeamIdentity.kt`, `GameSelectionScreen.kt`, `GameDetailScreen.kt`다. 인증·설정 저장소·재생·알림 판정·행동 이벤트 구현은 수정하지 않았다.

- QA: `-PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest` 성공, 테스트 실제 실행.
- 일반: `:app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest` 성공, 각 테스트 실제 실행.
- 구성별 130개, 실패·오류 0. 기존 색상 계약 테스트와 대비 테스트를 새 역할에 맞춰 갱신했다. 기존 128개 비즈니스 회귀 테스트를 포함한다.
- 메모리 옵션: `-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m`, `--max-workers=1`.
- 실기기 설치 APK: `build/handoff/ClutchAlarm-SoftPalette-QA.apk`. 기존 일반 앱에는 설치하지 않는다.

## 실기기 자료

Samsung SM-N960N / Android 10, USB device. QA 앱 데이터 초기화 없이 업데이트한다. 전후 사진은 같은 기기·해상도·동일 구단·동일 글자 크기를 사용한다. 이전 사진은 직전 작업에서 촬영해 보존한 원본이며 수정 전 화면을 재현하기 위해 앱을 다운그레이드하지 않았다.

10구단 모두 기본 1.0배·1.3배 홈 및 마이페이지 촬영을 완료했다. 고채도 네 구단(한화·KIA·SSG·LG)은 설정 화면의 강조 버튼·전체/부분 선택 표시도 추가 확인했다. 기본/확대에서 팀 이름·안내가 잘리지 않고 확대 시 스크롤로 아래 영역에 접근할 수 있다. 강한 구단색 전체 배경은 없어졌고 로고·글자·카드의 경계가 구분된다. 이는 한 대의 기기에서 수행한 시각 검토이며 사용자 가독성 연구나 다른 화면 밝기 환경의 검증은 아니다.

| 실제 검사 | 결과 |
|---|---|
| 10팀 저장 | UI에서 팀 선택·저장, 새 헤더 색이 각 primaryContainer와 일치 |
| 기본·1.3배 | 20개 홈 촬영. 수정 전 20개 원본과 크기를 맞춰 비교 |
| 한화·KIA·SSG·LG | 설정 화면의 저장 버튼·체크 상태·중립 카드 확인, 저장 없이 복귀 |
| 미저장 팀 변경 | 한화 저장 → 삼성 후보 선택 → 뒤로 → 한화 유지 |
| 새 프로세스 | HOME+am kill로 PID가 남아 있어 종료 확인으로 인정하지 않음. 이후 QA am force-stop으로 PID 소멸을 확인하고 재실행해 한화 테마·인증 세션 복원 확인 |
| 최종 복구 | 원래 QA의 LG·알림 14/16·기존 계정 유지, 시스템 글자 크기 원래 0.8로 복구 |

현재 APK SHA-256: `7ddd41d481a541319bc06e8a6911e6c54b69fee3278debb4d32eda2f34903b5d`.
제공 원본 SVG·PNG 13개는 이전 해시와 모두 일치한다. 일반 사용자 앱은 변경하지 않았다. 커밋은 생성하지 않았다.

사진은 별도 `soft-palette/report.html` 및 `soft-palette-verification.zip`으로 전달한다. Android 10 기기 한 대로 확인했으며 다른 Android 버전·기기·TalkBack은 미검증이다. 이번 단계에서는 경기·알림·인증 전체 흐름을 다시 실기기에서 재생하지 않았고, 기존 128개 비즈니스 단위 테스트와 기존 코드 유지 및 세션 복원으로 회귀를 확인했다. 행동 이벤트 기준을 바꾸거나 새 설치의 first_done 흐름을 다시 수행하지 않았다.
