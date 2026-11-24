# 구단 리소스·전역 테마 적용 및 검증

2026-11-24. 기존 작업 트리를 보존하며 화면·리소스와 순수 색상 계산을 수정했다. 인증 저장소, 재생·알림 판정, 행동 이벤트 구현은 변경하지 않았다. 서버 연결은 추가하지 않았다.

## 적용

- SVG 10개를 VectorDrawable로 변환해 `TeamIdentity.kt`의 `TeamLogoResources`·`TeamCrest`에 연결했다. 팀 선택, 홈, 경기 목록, 점수판, 마이페이지에서 재사용한다. 가상 시나리오 팀은 기존 약칭을 유지한다.
- 원본은 `design-assets/teams/`·`design-assets/branding/`에 보존했다. 로고는 무착색·Fit 표시로 색상·비율·투명도를 유지한다. PNG는 `drawable-nodpi`에 바이트 그대로 복사했다.
- 서비스 로고 `app_logo.png`(1536×1024 RGBA)는 인증 화면, `splash_logo.png`(2172×724 RGBA)는 네이티브 시작·세션 확인 화면, `app_icon.png`(1254×1254 RGB)는 레거시 해상도별·적응형 설치 아이콘에 적용했다. 아이콘 원본에는 알파 채널이 없다.
- `TeamBrand.kt`가 원색·전경색·대비용 강조색을 관리한다. `ClutchTheme.kt`에서 저장 응원팀의 버튼·활성 탭·토글·배속 강조색을 제공한다. 홈·마이페이지 헤더는 원색, 목록·점수판은 각 참가 구단 로고·색상을 사용한다.
- `GameApp.kt`는 저장 완료된 `preferences.preferredTeamId`만 전역 테마에 사용한다. 편집 후보 배지는 해당 구단색이지만 전역 테마는 바꾸지 않는다. 상세 임시 팀도 전역 테마에 영향을 주지 않는다.
- 시작·로그인·회원가입은 브랜드 레드 `#BE1536`, 화면 배경은 밝은 중립색, 오류·회원 탈퇴는 공통 위험색 `#B3261E`를 사용한다.

| 팀 | 지정 원색/헤더 | 대비용 강조색 | 원색 위 글자 |
|---|---|---|---|
| KIWOOM | #570514 | #570514 | 흰색 |
| SAMSUNG | #074CA1 | #074CA1 | 흰색 |
| KT | #000000 | #000000 | 흰색 |
| LG | #C40037 | #C40037 | 흰색 |
| KIA | #EA0029 | #CD0023 | 흰색 |
| DOOSAN | #1A1748 | #1A1748 | 흰색 |
| SSG | #CE0E2D | #CE0E2D | 흰색 |
| LOTTE | #041E42 | #041E42 | 흰색 |
| NC | #315288 | #315288 | 흰색 |
| HANWHA | #FC4E00 | #C23B00 | 검정 |

이미지와 헤더 원색은 변경하지 않았다. 작은 글자·버튼은 흰색·중립색·옅은 팀색에서 최소 4.5:1 대비를 만족하는 강조색을 사용한다. 한화는 원색 위 검정 글자이며 KIA·한화 강조색만 어둡게 보정했다. [색상·대비 계산](team-palette.json).

## 빌드·테스트

JDK 17. 아래 구성을 실행해 모두 성공했다. 환경 메모리 제한에 `-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m`, `--max-workers=1`을 사용했다.

| 구성 | Gradle 명령 | 테스트 |
|---|---|---|
| 일반 디버그 | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` | 130, 실패·오류 0 |
| QA 디버그 | `.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest` | 130, 실패·오류 0 |
| 일반 릴리스 | `.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest` | 130, 실패·오류 0 |
| QA 플래그+릴리스 | `.\gradlew.bat -PbehaviorQa=true :app:assembleRelease :app:testReleaseUnitTest` | 130, 실패·오류 0 |

구성 전환에서 테스트가 실제 실행됐으며 마지막 일반 디버그 재확인만 UP-TO-DATE였다. 기존 128개에 `TeamBrandTest` 색상·대비 테스트 2개를 추가했다. 인증·설정·행동 이벤트·알림 판정·모의 데이터 회귀를 포함한다. `git diff --check` 통과.

QA APK: `build/handoff/ClutchAlarm-TeamAssets-QA.apk`.
SHA-256: `1dada8d9fbc43694b49340ac7c19a4bdfd72dd856ca9f1ebdff63d2cad48c3f9`.
일반 앱에는 설치하지 않았다. 릴리스 빌드 성공은 배포 서명·실제 서버 인증 검증을 의미하지 않는다.

## 실기기

USB device, Samsung SM-N960N Android 10/API 29, 1080×2220, density 420. 분리된 `com.clutchalarm.app.behaviorqa`에만 업데이트하고 데이터 초기화 없이 검증했다.

| 항목 | 실제 확인 |
|---|---|
| 10구단 | UI에서 각 팀 선택·저장 후 홈 촬영. 헤더 픽셀이 지정 RGB와 모두 일치. 로고·선택 배지·버튼·활성 탭 확인 |
| 글자 크기 | 10구단 홈 font_scale 1.0·1.3 촬영. 팀 선택 그리드 상·하단, 점수판, 가입·로그인 키보드도 확대 확인. 스크롤·저장·제출 가능 |
| 저장·미저장 | 한화 저장 후 삼성 선택만 하고 뒤로 → 한화 유지. 후보 배지와 전역 테마 분리 |
| 재실행 | HOME 후 am kill, 기존 PID 15460 소멸 확인 → 새 프로세스에서 세션·한화 테마 복원 |
| 임시 팀 | SSG/KIA 경기에서 SSG 임시 선택해도 저장 응원팀·전역 강조색 한화 유지. 홈 복귀도 한화 |
| 경기 UI | SSG/KIA 목록·점수판 개별 로고·색상, 볼·스트라이크 미상 표시 유지 |
| 재생·알림 | mock-001 재생·2배속·일시정지, 설치 순번14~16 실제 게시. 16번 시스템 알림 탭 → 동일 상세, 일시정지·2배속 유지 |
| 인증 | 신규 QA 가입 → 이메일 채워진 로그인 → 가입 계정 로그인. 1.3배 키보드 열린 상태에서 제출. 로그아웃·기존 QA 계정 로그인 |
| 위험색 | 한화에서도 탈퇴 항목·확인 버튼 공통 위험색. 확인창 취소, 실제 삭제하지 않음 |
| 설치·시작 | Android 앱 정보의 설치 아이콘, 네이티브 시작 이미지, Compose 세션 확인 이미지 |
| 복구 | QA 원래 LG·알림 14/16·qa@example.com으로 복귀. 시스템 font_scale 원래 0.8 복구 |

일반 앱 설정 파일 해시는 전후 동일: `c6d450368d1d02a847beea6f0213667790fbd5bf512779d6b7068bf604783ae4`. QA 발송·열람 이력과 테스트 계정은 검증 결과로 남아 있다. 설치 전체 순번은 초기화하지 않았다.

## 행동 이벤트 회귀

[실제 필터 로그](team-assets-device-log.txt) PID 18800: app_open 1건, repeat_done 2건(settings_resave 1, notification_open 1), drop 1건, first_done 0건. 기존 설치로 이미 사용한 최초 알림을 다시 만들지 않았다.

토글 변경 후 미저장 이탈에서 drop, 알림 설정 재저장에서 repeat_done, 순번16 신규 열람에서 repeat_done을 확인했다. 목록 진입은 NOT_APPLICABLE. 팀만 변경한 10회 저장·로그인/로그아웃 이동은 추가 app_open/repeat_done/drop을 만들지 않았다. first_done·중복 방지는 기존 단위 테스트로 회귀 검증했다. 새 설치 기본 흐름 전체를 다시 수행한 것은 아니다.

## 변환 및 남은 확인

10개 SVG 원본(librsvg)과 VectorDrawable(VdPreview)을 1024px 렌더링해 시각 비교했다. PNG 대체가 필요한 손상은 관찰하지 않았다. 롯데는 stroke scaling approximate 경고가 있어 원본 교체 시 재비교한다. 평균 채널 차이는 255 기준 0.050~0.975이며 렌더러 안티앨리어싱 차이가 포함된다. 픽셀 일치를 의미하지 않는다. `tools/prepare_assets.py`·`tools/ConvertSvg.java`로 로컬 Gradle 변환기를 사용하며 앱 의존성은 추가하지 않았다. [원본 해시](asset-source-hashes.json).

Android 12 이상 시스템 스플래시 마스크, Android 13 이상 알림 권한, 다른 기기·IME·TalkBack은 미검증이다. 기존 8개 시나리오 모두를 실기기에서 끝까지 다시 재생하지는 않았으며 데이터·판정 회귀는 단위 테스트로 확인했다.

별도 전달 `team-assets/report.html`·`team-assets-verification.zip`에 구단별 기본/확대 스크린샷·변환 비교를 묶었다. 개인 배경화면 전환 프레임과 다른 앱 알림창은 제외했다. 커밋은 생성하지 않았다.
