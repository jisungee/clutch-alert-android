# 인계 대상 파일과 제외 기준

기준일 2026-11-24. [handoff-files.txt](handoff-files.txt)는 현재 Git 추적 파일과 필요한 미추적 파일을 합친 **소스 인계 목록**이다. 초기 점검의 디자인 커밋 218c1a1 이후 QA 분리·통합 QA 증거·최신 문서까지 MVP-20261127 확정 커밋에 함께 포함한다. 최신 원본은 이 문서를 포함한 브랜치 HEAD이며 최종 검증 영수증과 대조한다. 임시 산출물은 계속 제외한다.

## Git에 포함할 파일

| 범위 | 포함 이유 |
|---|---|
| 루트 Gradle 설정·Wrapper·AGENTS·README·Git 설정 | 빌드·개발 규칙 재현 |
| `app/src/main/` Kotlin·Manifest·리소스·모의 JSON | 실제 앱 UI·기능·로컬 알림·이벤트 계약 |
| `app/src/productionAuth/` | 일반 디버그·릴리스의 운영 연결 지점과 운영 UI 문구 |
| `app/src/behaviorQa/` | 소스 인계에는 포함. QA 디버그만 컴파일하고 일반/릴리스 APK에서는 제외 |
| `app/src/test/` | 인증·세션·설정·재생·알림·이벤트·디자인·빌드 경계 회귀 |
| `design-assets/teams/` SVG 10개·`design-assets/branding/` PNG 3개 | 제공 원본 보존. 앱은 변환된 VectorDrawable·리소스를 사용 |
| `app/src/main/res/` 벡터·PNG·mipmap·시작 테마 | 빌드 때 변환 도구를 다시 실행하지 않아도 리소스 재현 |
| `docs/` 최신 문서·과거 검증 기록·텍스트 로그·대비 JSON | 현재 계약 및 이전 실기기 증거. 과거 보고서는 최신 절차와 구분 |
| `docx/` 정책 PDF 및 확정 정책 | 알림 판정 기준. 구형 자료는 역사 자료 |
| `tools/ConvertSvg.java`, `prepare_assets.py` | 이미지 변환 재현. JDK17·Pillow·로컬 AGP Gradle 캐시 필요, 앱 런타임 의존성 아님 |
| `tools/audit_apk.py`, `prepare_handoff_manifest.py` | APK QA 경계 검사·전달 목록 재생성. Python3 표준 라이브러리 |

목록 재생성:

```powershell
python tools/prepare_handoff_manifest.py
```

이 목록은 파일 경로 목록이며 최종 커밋/해시 증명이 아니다. 최종 커밋을 만든 뒤 깨끗한 체크아웃 빌드와 APK 해시로 다시 확인한다. 신규 파일이 추가되면 목록도 재생성한다.

## 소스·폴더 전달에서 제외

- `.git/`, `.gradle/`, `.kotlin/`, `.idea/`, 모든 `build/`, `.cxx/`, `.externalNativeBuild/`, `captures/`, `__pycache__/`.
- `java_pid*.hprof` 3개(약 2.07GiB), `.class`, `.pyc`, 임시 `.log`.
- 모든 `.apk`, `.aab`, 구형 `build/handoff/` 검증 APK와 APK 출력 metadata·프로필.
- `local.properties`, 서명 키/비밀번호·keystore 설정, google-services.json, 기기 앱 데이터·세션 파일·DataStore 백업.
- 사용자 PC 시각화 폴더의 작업용 스크립트·개인 배경화면 전환 프레임. 승인된 스크린샷 갤러리는 필요 시 별도 증거 자료로 전달한다.

현재 hprof·구형 APK·캐시를 디스크에서 삭제하지 않았다. `.gitignore`와 전달 목록의 이중 제외를 사용하므로 저장소 폴더 전체를 무차별 ZIP하지 않는다. `docs/*device-log.txt`는 의도적인 검증 증거이며 임시 빌드 로그와 구분한다.

## 별도 QA 실행 산출물

소스 목록에 APK를 넣지 않는다. 최종 확정 커밋에서 재빌드한 `build/handoff/ClutchAlarm-MVP-20261127-QA.apk` 한 개를 `.sha256` 및 `ClutchAlarm-MVP-20261127-receipt.json`과 함께 별도로 전달한다. [최종 버전 안내](mvp-20261127.md)를 따른다. 구형 APK를 혼합하지 않는다. 일반 디버그와 QA 디버그는 원래 출력 파일명이 같으므로 빌드 직후 복사본을 사용한다. 릴리스는 unsigned이므로 배포용 설치 파일로 전달하지 않는다.
