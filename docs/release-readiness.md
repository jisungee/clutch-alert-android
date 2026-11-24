# QA 분리 및 인계 준비 검증

2026-11-24. 브랜치 `codex/handoff-20261127`, 시작 HEAD는 `0c7e0b3f10dda7b00521d391afbe9d212be2b894`였으며 최종 확인 중 외부 HEAD 갱신 `218c1a1face8aff29edbe685de2a6bd5315ace8b`(디자인, 18:00:20+09:00)을 확인했다. 이 작업에서는 commit/push 명령을 실행하지 않았고 해당 커밋을 보존했다. QA 정리 변경은 미커밋·미스테이징 작업 트리로 남겼으며 APK 배포·파일 삭제도 하지 않았다.

## 이번 변경

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | QA 패키지/앱 이름/버전 suffix를 debug로 한정. 일반·모든 release는 운영 인증·UI 소스 사용 |
| `src/behaviorQa/.../ui/BuildUiContent.kt` (신규) | QA 로그인 버튼·fixture 안내·가입/탈퇴/홈 문구 |
| `src/productionAuth/.../ui/BuildUiContent.kt` (신규) | 운영 문구 및 빈 테스트 힌트 |
| `src/main/.../ui/LoginScreen.kt`, `SignUpScreen.kt`, `HomeScreen.kt`, `GameApp.kt` | 문구를 선택된 소스셋 객체에서 공급. 기능·콜백·배치 유지 |
| `src/main/.../MainActivity.kt` | 세션·알림 열람 진단은 DEBUG에서만 출력 |
| `src/main/.../notification/AndroidNotificationDispatcher.kt` | 예외 상세 진단도 DEBUG 한정. 발송 결과/오류 UI는 유지 |
| `src/test/.../ui/DiagnosticBuildTest.kt` | 일반/릴리스 fixture 힌트·QA 이동 문구 부재 회귀 검사 추가 |
| `README.md`, `docs/server-handoff.md` | 최신 기능·계약·로그인부터의 QA 재현·미연결/미검증 범위로 통합 |
| `docs/handoff-files.md`, `handoff-files.txt` | 원본·소스·테스트·도구 포함, 임시/개인 환경 제외 |
| `tools/audit_apk.py` | APK 전체 엔트리의 UTF-8/UTF-16LE QA 표식 검사, release 진단 태그 부재 검사 |
| `tools/prepare_handoff_manifest.py` | 추적·필요 미추적 소스 경로 목록 생성. 스테이징/삭제/복사는 수행하지 않음 |

위 src 상대 경로는 `app/` 기준이며 생략한 패키지는 `java/com/clutchalarm/app/`다. 정확한 경로는 전달 파일 목록을 참조한다. 이전에 작성한 로고·테마·변환 리소스와 회귀 테스트는 점검 중 갱신된 디자인 커밋에 포함되어 있으며 전달 목록에도 유지했다.

`BehaviorEventRecorder`·이벤트 모델·GameViewModel·DataStore 열람/설정/순번·재생/알림 판정 구현은 HEAD 대비 diff가 없음을 확인했다. 제공 원본 이미지 13개 SHA-256도 이전 기록과 동일하다. `ClutchBehavior`는 계속 DEBUG 전용이며 향후 서버 sink를 주입할 인터페이스를 보존했다.

## 검증 명령과 실제 결과

QA 디버그 빌드/테스트 후 별도 APK로 복사한 다음 일반 구성을 실행했다. 일반·릴리스 최종 검증에는 `--rerun-tasks`를 사용해 테스트를 다시 실행했다. 공통 메모리 옵션은 `-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m`, `--max-workers=1`이다.

```powershell
.\gradlew.bat -PbehaviorQa=true :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease :app:testReleaseUnitTest --rerun-tasks
.\gradlew.bat -PbehaviorQa=true :app:assembleRelease :app:testReleaseUnitTest

python tools/audit_apk.py build/handoff/ClutchAlarm-Handoff-QA.apk --expect-qa
python tools/audit_apk.py app/build/outputs/apk/debug/app-debug.apk
python tools/audit_apk.py app/build/outputs/apk/release/app-release-unsigned.apk --release
```

| 구성 | 최종 실행 결과 | 테스트 실행 여부 |
|---|---|---|
| QA debug | BUILD SUCCESSFUL 20초 | 134개 새 실행, 실패·오류·스킵 0 |
| 일반 debug + release | BUILD SUCCESSFUL 1분42초, 102개 작업 실제 실행 | 각각 134개 새 실행, 실패·오류·스킵 0 |
| QA 플래그 + release | BUILD SUCCESSFUL 1초 | 테스트 UP-TO-DATE. 운영 릴리스와 같은 입력·동일 APK SHA-256 확인 |

테스트 수는 기존 133개에 빌드 UI 경계 검사 1개를 추가한 134개다. [최종 일반 디버그/릴리스 테스트 집계](release-readiness-tests.json). Android 기기 테스트를 실행한 것으로 간주하지 않는다.

| APK | SHA-256 | 검사 |
|---|---|---|
| QA 후보 | `4b87c67cd5f7fe1254d55c634748b49b05ad84dfd891fc896263898a64745b12` | [검사 결과](apk-audit-qa.json) |
| 일반 debug | `130d4625318fb7ec4bf410b2f90c6c30580576447543d38f287bfeb1e63d0d10` | [검사 결과](apk-audit-normal-debug.json) |
| 일반 release (unsigned) | `802d2d48ad16f7a45565b6bad5135a64107deb3849c6df2241f086ebbc416117` | [검사 결과](apk-audit-release.json) |
| QA 플래그 release (unsigned) | `802d2d48ad16f7a45565b6bad5135a64107deb3849c6df2241f086ebbc416117` | [검사 결과](apk-audit-qa-flag-release.json) |

APK 전체 엔트리 검사에서 QA 후보는 13개 표식 모두 존재, 일반 디버그·모든 릴리스는 13개 모두 부재였다. 릴리스 진단 태그 5개도 모두 부재였다. QA 플래그 릴리스는 패키지·앱 이름·버전까지 운영 구성으로 유지됐고 일반 릴리스와 APK 해시도 같다.

전체 작업 재실행 중 `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` 심벌을 strip하지 못해 원본으로 패키징한다는 경고가 있었다. 빌드·테스트 실패는 아니며 배포 크기/심벌 정리는 서명·배포 준비 단계에서 별도로 확인할 수 있다.

APK 검사는 알려진 QA 계정·비밀번호·세션 prefix·인증 클래스·버튼/가입/탈퇴/홈 안내·QA 앱 이름/패키지·알림 후보/게시 안내 표식 13개를 검사한다. QA APK는 모두 존재해야 양성 대조를 통과하며 일반/릴리스는 모두 없어야 한다. release는 진단 태그 5개도 없어야 한다. 일반 debug는 진단 로그를 유지하므로 태그 존재를 실패로 보지 않는다. 이는 일반 비밀 정보 탐지기나 서명 검사가 아니다.

## 전달 경계·남은 작업

- [분류](handoff-files.md)·[정확한 파일 목록](handoff-files.txt)에는 최종 구현에 필요한 미추적 소스·원본·문서·도구까지 포함한다. 이번 단계에서는 git add/commit을 하지 않았다.
- hprof 3개 약 2.07GiB, 구형 APK·모든 빌드 캐시·로컬 SDK 경로·키·기기 데이터는 제외한다. 디스크에서 삭제하지 않는다. 최신 QA APK 한 개만 소스 밖 별도 실행 자료로 전달할 수 있다.
- API 명세·실제 인증/이벤트 수집·event_id/outbox/재시도·서버 멱등성·INTERNET 권한 추가·배포 서명은 다음 단계다. 연결 지점과 서버 질문은 [통합 인계](server-handoff.md).
- 이번에는 실제 기기 전체 사용자 흐름을 새로 실행하지 않았다. 이전 실기기 로그는 원래 날짜·APK 기준의 증거로 유지한다. Android13+ 권한 거부·Android12+ 시스템 스플래시·다른 기기·TalkBack·서버 연결 후 회귀는 미검증이다.
- 최종 인계 커밋 후 깨끗한 체크아웃에서 재현 빌드·확정 SHA·APK 해시를 다시 묶어야 한다.
