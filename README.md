# 클러치알림 Android

최종 클라이언트 인계 버전 **MVP-20261127**, 브랜치 `codex/handoff-20261127`입니다. 실제 경기 기반 재현10개·홈 응원팀 바로가기를 포함한 이 문서의 브랜치 HEAD가 인계 대상입니다. 정확한 커밋 SHA·최종 QA APK·해시는 [최종 버전 안내](docs/mvp-20261127.md)의 검증 영수증으로 대조하세요. 이전 d6204eb 스냅샷/구형 APK와 구분합니다.

야구 모의 경기의 중요한 순간을 Android 로컬 알림으로 알려주는 Compose Material 3 앱입니다. **실제 인증·이벤트 수집 API는 아직 연결하지 않았습니다.** 일반 디버그와 모든 릴리스는 인증 성공을 가장하지 않으며, 앱 체험은 별도 QA 디버그를 사용합니다.

## 현재 기능

- 회원가입 입력 검증·중복 이메일·로딩·오류·재시도, 가입 후 이메일이 채워진 로그인으로 복귀.
- 로그인·암호화 세션 저장/복원, 일시 오류와 확정 만료 구분, 로그아웃 및 마이페이지 계정 이메일 표시.
- 첫 이용: 응원팀 선택 → 알림 선택 → 명시적 저장 → 홈. 이후 실행은 세션 확인 후 완료 여부에 따라 홈/응원팀 선택.
- 10개 구단 로고·저장 응원팀 테마·홈 그라데이션, 홈/경기 목록/상세/마이페이지 이동.
- 일반·QA·릴리스 모두 실제 경기 기반 재현 10개만 포함(합성 8개는 단위 테스트 전용), 재생·일시정지·재개·초기화·1/2/5/10배속.
- 홈의 ‘내 응원팀 경기 보러가기’로 날짜·상대 구단을 확인하고 추천 상세에 바로 진입합니다. 바로가기는 재생이나 알림 열람을 시작하지 않습니다. 저장한 응원팀 변경은 즉시 반영됩니다.
- 응원팀 전용 추천 경기·‘전체 경기 보러가기’ 접근, 날짜·양 팀 로고/구단명·1배속 예상 시간 표시. 목록에 최종 점수를 노출하지 않습니다. 응원팀 참가 경기는 저장된 팀으로 바로 재생합니다.
- 16개 알림 설정, 전체/부분/꺼짐 표시, 저장 전 편집값 분리·미저장 이탈·실패 재시도. 경기의 임시 알림 팀은 저장 응원팀과 분리.
- 로컬 알림의 발송별 ID·설치 단위 성공 순번·상세 진입·중복 열람 방지. 같은 경기 알림 복귀 시 재생 상태 유지.
- 회원 탈퇴 확인·로딩·성공·실패·재시도와 저장소 계약. 실제 삭제는 서버 연결 후 가능.
- `app_open`, `first_done`, `repeat_done`, `drop`은 공통 기록기로 DEBUG 로그 검증. 서버 전송은 없음.

미제공: 실제 경기 API, 전체 이닝별 득점·H/E·라인업·선수 기록·실제 플레이 시각, 0.5배속·공유. 경기·응원팀·알림 설정은 로컬이며 FCM/푸시 토큰 서버 등록은 현재 범위가 아닙니다.

## 개발·빌드

단일 `app` 모듈, Kotlin/Compose Material 3/ViewModel/StateFlow/코루틴/DataStore. JDK 17, Android SDK Platform 36, SDK Platform-Tools가 필요합니다. Gradle 8.13·AGP 8.13.2·Kotlin 2.2.21은 저장소 설정을 사용합니다. 최소 Android API 23, target/compile API 36입니다. SDK 경로는 로컬 `local.properties`에만 둡니다.

Windows PowerShell, 저장소 루트:

```powershell
# 일반 디버그: 서버 미연결이므로 로그인 성공 불가
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest

# 별도 QA 디버그: 화면·로컬 기능 체험
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest

# 일반 릴리스: QA 인증/안내 제외, 현재 unsigned
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest
```

기본 APK 경로는 디버그 `app/build/outputs/apk/debug/app-debug.apk`, 릴리스 `app/build/outputs/apk/release/app-release-unsigned.apk`입니다. 일반/QA 디버그가 같은 출력 경로를 사용하므로 QA 빌드 직후 별도 복사해 보관합니다. macOS/Linux는 `sh ./gradlew`로 실행합니다. 최초 의존성 설치에는 네트워크가 필요합니다.

## QA 실행

```powershell
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
New-Item -ItemType Directory -Force build/handoff | Out-Null
Copy-Item app/build/outputs/apk/debug/app-debug.apk build/handoff/ClutchAlarm-Handoff-QA.apk
adb devices -l
adb install -r build/handoff/ClutchAlarm-Handoff-QA.apk
adb shell am start -n com.clutchalarm.app.behaviorqa/com.clutchalarm.app.MainActivity
```

QA는 `ClutchAlarm QA` / `com.clutchalarm.app.behaviorqa`로 일반 앱과 분리됩니다. 기본 테스트 로그인은 `qa@example.com` / `clutch-qa`입니다. 회원가입으로 만든 계정 목록은 현재 프로세스에만 존재하지만 로그인한 QA 세션은 암호화 저장되어 새 프로세스에서도 복원됩니다. 실제 서버 인증·계정 삭제를 대신하지 않습니다.

1. 로그인 또는 가입 → 로그인 → 응원팀/알림 최초 저장 → 홈으로 진입합니다.
2. 홈의 **내 응원팀 경기 보러가기**로 저장한 구단의 추천 상세를 엽니다. 날짜·상대 구단과 재생 대기를 확인합니다. **전체 경기 보러가기**에서도 실제 경기 기반 10개를 모두 열 수 있습니다.
3. 알림 권한·앱/채널 알림을 켜고 1배속으로 재생합니다. OFF 상태에서도 재생은 가능하지만 알림 발송·열람 이벤트는 발생하지 않습니다.
4. 시스템 알림창의 실제 알림을 눌러 상세로 돌아옵니다. 앱 안의 본문 보기나 목록 진입은 알림 열람이 아닙니다.
5. 마이페이지에서 응원팀·알림 변경의 저장/미저장, 로그아웃을 확인합니다.

새 설치 검사는 기존 QA 데이터를 초기화하므로 일반 실행과 구분해야 합니다. 데이터 보존·이벤트 전체 재현·프로세스 종료 검사는 [통합 인계 문서](docs/server-handoff.md)에 정리했습니다.

## QA 분리·로그 정책

`-PbehaviorQa=true`의 디버그만 `src/behaviorQa` 인증 및 UI 문구를 사용합니다. 일반 디버그와 모든 릴리스는 `src/productionAuth`를 사용합니다. QA 계정·비밀번호·가입/탈퇴/홈 안내는 QA 소스에만 둡니다. QA 플래그로 릴리스를 빌드해도 인증/안내·패키지명·앱 이름은 운영 구성을 유지합니다. 실제 배포 명령에는 혼동을 피하도록 해당 플래그를 사용하지 않습니다.

세션/알림 진입·발송·오류 진단과 `ClutchBehavior`는 DEBUG에서만 출력합니다. 비밀번호·credential은 로그에 남기지 않습니다. QA 후보·게시 확인 UI는 QA 디버그에서만 표시합니다. 릴리스에는 이벤트 전송기가 아직 없으며, 향후 `BehaviorEventRecorder` 구현을 주입해야 합니다.

APK 경계 검사는 Python 3 표준 라이브러리만 사용합니다.

```powershell
python tools/audit_apk.py build/handoff/ClutchAlarm-Handoff-QA.apk --expect-qa
python tools/audit_apk.py app/build/outputs/apk/debug/app-debug.apk
python tools/audit_apk.py app/build/outputs/apk/release/app-release-unsigned.apk --release
```

두 번째 명령은 일반 디버그를 마지막으로 빌드한 상태에서 사용합니다. QA APK는 검사 표식이 존재하는 양성 대조, 일반·릴리스는 표식 0개가 통과 조건입니다. APK 전체 엔트리의 UTF-8/UTF-16LE를 검사하고 릴리스는 진단 로그 태그도 없어야 합니다.

## 구조·데이터·정책

| 경로 (`app/src/main/java/com/clutchalarm/app/` 기준) | 역할 |
|---|---|
| `MainActivity.kt`, `ui/` | 저장소 주입·인증/화면 상태·입력·이동 |
| `auth/` | 인증 계약·Keystore 세션 저장·결과 구분 |
| `data/`, `domain/repository/` | JSON 어댑터·경기·설정·발송 순번·열람 저장 |
| `domain/simulation/` | 시간순 모의 재생 |
| `domain/notification/`, `notification/` | 순수 Kotlin 판정과 Android 발송·진입 분리 |
| `behavior/` | 공통 이벤트 계약·디버그 기록기 |
| `domain/model/TeamBrand.kt`, `HomeGradient.kt` | 공식 원색·컨트롤 팔레트·홈 배경 분리 |

실제 경기 기반 재현은 `app/src/main/assets/games/catalog.json`으로 공급합니다. `date`, `preparedForTeamId`를 목록용 메타데이터로 관리하고 예상 시간은 누적 delay_sec로 계산합니다. 합성 8개와 test-catalog는 `app/src/test/resources/games/`에만 있습니다. 어느 APK에도 포함하지 않습니다. 기존 회귀 테스트는 테스트 전용 카탈로그로 합성 8개와 실제 10개를 함께 읽고, Android 앱은 모든 구성에서 main의 실제 10개 카탈로그만 사용합니다. 완전 플레이 어댑터는 play_actor를 사용하고, 선별 데이터 어댑터는 current_batter를 방금 플레이한 선수로 해석합니다. 미상 값과 생략 구간은 복원하지 않습니다.

알림의 현재 기준은 `docx/클러치 알람 리스트 및 모의데이터 양식.pdf`와 [확정 정책](docx/notification-policy-review.md)입니다. 빅이닝 3점, 한 플레이 최대 1건, 상대 출루 기본 OFF 독립 설정, 지는 중 숨김은 동점에서도 유지하고 다시 앞서는 플레이부터 해제합니다. 옛 v1.0 및 이전 보고서는 역사 자료입니다.

## 인계·검증 문서

- [최종 통합 QA](docs/final-qa-report.md): 동일 프로세스 1/1/2/1, API36 권한 허용/거부, 기존 설정 보존.
- [최신 통합 서버 인계](docs/server-handoff.md): 인증·이벤트 인터페이스, QA 재현, 서버 확인 질문, 미검증 범위.
- [전달 파일 분류](docs/handoff-files.md)와 [정확한 전달 파일 목록](docs/handoff-files.txt): 원본·소스·테스트 포함, hprof/캐시/옛 APK 제외.
- [이번 QA 분리·검증 결과](docs/release-readiness.md).
- [최종 10구단 홈](docs/home-gradients-final.md): 기본/1.3배 20장·대비·기존 4팀 보존.

개별 기능·과거 테스트 보고서는 당시 기록입니다. 특히 `docs/handoff-20261127.md`는 로그인 도입 전 기록이며 현재 재현 절차는 통합 문서를 따릅니다. 이전 단계 보고서의 미커밋·푸시 미수행 문구는 당시 기록입니다. 현재 인계 버전은 그 후속 변경까지 포함한 이번 최종 HEAD입니다. 실제 서버 구현·배포 서명은 후속 작업입니다.


### 실제 경기 기반 재현 10경기 (2026-11-24)

제공 원본 10개/374개 레코드를 변경 없이 보존해 동일 변환 계층으로 연결했습니다. 이닝 잔여 득점에도 기존 구독 정책을 적용합니다. 두산 31번·한화 35번의 원문 역전 표시는 확정 점수가 동점이므로 알림에서는 동점으로 처리합니다. [전체 검증 보고서](docs/ten-game-validation.md), [경기별·알림 유형별 집계 및 전체 알림](docs/ten-game-notification-results.json), [최종 테스트 집계](docs/ten-game-test-results.json)를 참고하세요. 한화 단독 단계의 보고서는 당시 검증 기록으로 보존합니다.


### 홈 바로가기와 앱 데이터 통일

모든 앱은 동일한 실제 경기 기반 JSON 10개를 사용합니다. 기존 합성 경기는 JVM 테스트 전용으로 이동했습니다. 홈 주요 버튼은 현재 저장된 응원팀의 추천 상세로 이동하며, 전체 목록 버튼과 구단별 그라데이션은 유지합니다. [이번 검증 보고서](docs/home-shortcut-validation.md)와 [일반·QA·릴리스 테스트 결과](docs/home-shortcut-test-results.json)를 참고하세요. 이전 10경기 보고서의 QA 합성 8개 포함 설명은 당시 상태입니다.


### 최종 인계 검증 (2026-11-26)

일회용 Android16 새 설치에서 회원가입부터 홈 바로가기·1/2번 실제 시스템 알림 열람·재저장·미저장 이탈까지 같은 프로세스로 실행해 `app_open1 / first_done1 / repeat_done2 / drop1`을 확인했습니다. 일반/QA/릴리스 각149개 테스트와 APK QA/데이터 경계 검사도 통과했습니다. [최신 통합 QA](docs/final-qa-report.md), [서버 인계 계약](docs/server-handoff.md)을 참고하세요. 실제 서버 구현·배포 서명은 후속 범위입니다.
