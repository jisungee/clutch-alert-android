# ClutchAlarm 개발 규칙

- 단일 Android `app` 모듈을 유지한다. Kotlin, Compose Material 3, ViewModel과 StateFlow, 코루틴을 사용하고 의존성은 필요한 단계에 추가한다.
- 변경을 단계별로 나눈다. 기존 코드를 대규모로 변경하기 전에는 변경 계획을 설명한다.
- 화면은 상태를 렌더링하고 사용자 입력을 ViewModel로 전달한다. 저장소 인터페이스를 통해 데이터를 공급하고 재생과 알림 판정은 화면에서 분리한다.
- 알림 규칙의 기준은 `docx/클러치 알람 리스트 및 모의데이터 양식.pdf`와 `docx/notification-policy-review.md`의 확정 정책이다. 문서에서 확정할 수 없는 규칙은 PR의 검토 항목으로 남긴다.
- 알림 판정은 Android 의존성 없는 순수 Kotlin으로 작성하고 JUnit으로 검증한다. 시스템 알림 발송과 중복 방지는 판정과 분리한다.
- MVP는 JSON 모의 데이터를 사용한다. 외부 API, 서버, 계정, Firebase와 후순위 기능은 별도 단계에서 다룬다.
- 변경 후 가능한 범위에서 `:app:assembleDebug`와 `:app:testDebugUnitTest`를 실행하고, 실제 결과와 검증 제약을 기록한다.
- Gradle Wrapper는 공유한다. 로컬 SDK 경로, 서명 키, 빌드 결과는 커밋에서 제외한다.
