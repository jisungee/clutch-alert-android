# 클러치알림 Android

최종 클라이언트 인계 버전 **MVP-20261127**, 브랜치 `codex/handoff-20261127`. 이 문서를 포함한 브랜치 HEAD가 확정 커밋이며, 별도 체크아웃 빌드의 SHA·테스트·APK 해시는 [최종 버전 안내](docs/mvp-20261127.md)의 검증 영수증 경로를 따른다.

야구 모의 경기의 중요한 순간을 Android 로컬 알림으로 알려주는 Compose Material 3 앱입니다. **실제 인증·이벤트 수집 API는 아직 연결하지 않았습니다.** 일반 디버그와 모든 릴리스는 인증 성공을 가장하지 않으며, 앱 체험은 별도 QA 디버그를 사용합니다.

## 현재 기능

- 회원가입 입력 검증·중복 이메일·로딩·오류·재시도, 가입 후 이메일이 채워진 로그인으로 복귀.
- 로그인·암호화 세션 저장/복원, 일시 오류와 확정 만료 구분, 로그아웃 및 마이페이지 계정 이메일 표시.
- 첫 이용: 응원팀 선택 → 알림 선택 → 명시적 저장 → 홈. 이후 실행은 세션 확인 후 완료 여부에 따라 홈/응원팀 선택.
- 10개 구단 로고·저장 응원팀 테마·홈 그라데이션, 홈/경기 목록/상세/마이페이지 이동.
- 로컬 시나리오 8개(기존 7개＋PDF 양식 기반 합성 1개), 재생·일시정지·재개·초기화·1/2/5/10배속.
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
2. **모의 경기 보러가기 → 모의 경기 · 일반**을 열고, 저장 팀이 해당 경기에 없으면 **이번 모의 경기 알림 관점 (임시)**을 선택합니다.
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

경기는 `app/src/main/assets/games/catalog.json`으로 공급합니다. PDF 기반 경기는 팀 원본이 아닌 1이닝 단축 합성 데이터입니다. `delay_sec`를 누적하고, 직전 확정 상태를 다음 before로 사용하며, 알림 행위자는 `play_actor`로 분리합니다. B/S 누락은 `—`, 판독은 구조화 결과로 처리하며 설명 문장으로 점수를 보정하지 않습니다. 원본 JSON 수령 후 상태 연속성·선수 ID·판독 대상·시간 단위를 재확인해야 합니다.

알림의 현재 기준은 `docx/클러치 알람 리스트 및 모의데이터 양식.pdf`와 [확정 정책](docx/notification-policy-review.md)입니다. 빅이닝 3점, 한 플레이 최대 1건, 상대 출루 기본 OFF 독립 설정, 지는 중 숨김은 동점에서도 유지하고 다시 앞서는 플레이부터 해제합니다. 옛 v1.0 및 이전 보고서는 역사 자료입니다.

## 인계·검증 문서

- [최종 통합 QA](docs/final-qa-report.md): 동일 프로세스 1/1/2/1, API36 권한 허용/거부, 기존 설정 보존.
- [최신 통합 서버 인계](docs/server-handoff.md): 인증·이벤트 인터페이스, QA 재현, 서버 확인 질문, 미검증 범위.
- [전달 파일 분류](docs/handoff-files.md)와 [정확한 전달 파일 목록](docs/handoff-files.txt): 원본·소스·테스트 포함, hprof/캐시/옛 APK 제외.
- [이번 QA 분리·검증 결과](docs/release-readiness.md).
- [최종 10구단 홈](docs/home-gradients-final.md): 기본/1.3배 20장·대비·기존 4팀 보존.

개별 기능·과거 테스트 보고서는 당시 기록입니다. 특히 `docs/handoff-20261127.md`는 로그인 도입 전 기록이며 현재 재현 절차는 통합 문서를 따릅니다. 이전 단계 보고서의 미커밋·푸시 미수행 문구는 당시 기록입니다. 현재 인계 대상은 MVP-20261127 확정 브랜치이며 실제 서버 구현·배포 서명은 후속 작업입니다.
