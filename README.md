# ClutchAlarm

모의 야구 경기 데이터를 바탕으로 중요한 플레이를 알려주는 Android 앱입니다. 현재는 모의 경기 목록과 상세 화면에서 경기를 선택하고 재생할 수 있습니다.

## 개발 환경

- Android Studio에서 저장소 최상위 폴더를 엽니다.
- Gradle JDK는 JDK 17을 사용합니다. Gradle 버전은 저장소의 Wrapper가 관리합니다.
- Android Studio의 **Tools → SDK Manager**에서 Android SDK Platform 36, Android SDK Build-Tools 35.0.0, Android SDK Platform-Tools를 설치합니다.
- **Sync Project with Gradle Files**를 실행합니다. 처음 동기화할 때는 Gradle 및 의존성 다운로드를 위해 인터넷 연결이 필요합니다.
- 설치할 휴대폰은 Android 6.0(API 23) 이상이어야 합니다.

## 실제 스마트폰에서 실행

1. 휴대폰 설정에서 **빌드 번호**를 일곱 번 눌러 개발자 옵션을 활성화하고 **USB 디버깅**을 켭니다.
2. 데이터 전송이 가능한 USB 케이블로 연결한 뒤 휴대폰의 디버깅 승인 창을 확인합니다.
3. Android Studio 상단에서 `app` 실행 구성과 연결한 휴대폰을 선택하고 **Run ▶**을 누릅니다. **모의 경기** 목록에서 경기를 선택한 뒤 **재생**을 누릅니다.
4. 기기가 목록에 없으면 SDK의 `platform-tools` 폴더에서 `adb devices`를 실행합니다. `unauthorized`가 나오면 휴대폰 잠금을 풀고 승인 창을 확인합니다. Windows에서는 기기에 따라 제조사 USB 드라이버가 필요합니다.

[Android Developers 실기기 연결 가이드](https://developer.android.com/studio/run/device.html)

Windows PowerShell에서 빌드와 JVM 단위 테스트를 실행할 수도 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

macOS와 Linux에서는 `sh ./gradlew :app:assembleDebug :app:testDebugUnitTest`를 사용합니다. 빌드가 완료되면 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 코드 구조

`MainActivity`가 `MockGameRepository`를 `GameViewModel`에 주입합니다. `GameApp`은 화면 상태에 따라 목록이나 `GameDetailScreen`을 표시합니다. Kotlin 패키지는 `app/src/main/java/com/clutchalarm/app/` 아래에 있습니다.

| 위치 | 맡을 역할 |
| --- | --- |
| `ui/` | 경기 목록, 상세 화면, `GameViewModel` |
| `domain/model/` | 현재 상태 `GameState`와 플레이 기록 `GameEvent` |
| `domain/repository/`, `data/` | `GameRepository` 계약과 JSON을 읽는 `MockGameRepository` |
| `domain/simulation/` | 이벤트를 시간순으로 재생하는 `GameSimulationEngine` |
| `domain/notification/`, `notification/` | 순수 Kotlin 알림 판정과 중복 방지, Android 알림 발송 |
| `app/src/main/assets/games/`, `app/src/test/` | 모의 경기 JSON, JVM 단위 테스트 |

저장소가 모의 경기를 공급하면 재생 엔진이 시간순으로 상태를 갱신하고, ViewModel이 StateFlow로 화면에 전달합니다.

## 모의 경기 데이터

`MockGameRepository(assets)`는 `app/src/main/assets/games/mock-game.json`을 읽습니다. `listGames()`는 경기의 초기 상태를, `getGame(gameId)`는 초기 상태와 이벤트 목록이 담긴 `GameTimeline`을 반환합니다. JSON에는 초기 상태와 플레이 직후의 상태를 기록합니다. 로더가 초기 상태 또는 직전 플레이의 결과를 다음 플레이의 `before`로 연결합니다. 이벤트의 `offsetMs`는 재생 시작부터 지난 밀리초이며, 같은 시점의 이벤트는 파일 순서를 따릅니다.

모의 경기는 1회 일부를 담습니다. 원정팀이 단타와 2루타로 선취점을 얻고, 2루 주자가 진루하지 않은 채 타구 아웃 세 개로 공격이 끝납니다. 공수교대 뒤 홈팀의 솔로 홈런 두 개로 동점과 역전이 이어집니다. 세 번째 아웃 직후에는 아웃 3개를 기록하고, 공수교대 이벤트에서 카운트와 베이스를 초기화합니다.

## 모의 경기 재생

`GameSimulationEngine(timeline, scope)`는 타임라인의 절대 재생 시각에 각 이벤트의 `after` 상태를 적용합니다. `play()`는 처음부터 시작하고, `pause()`는 현재 시각을 저장하며, `resume()`은 다음 이벤트까지 남은 시간부터 이어갑니다. `reset()`은 재생을 중단하고 초기 상태로 돌아갑니다. 마지막 이벤트를 처리하면 모의 데이터의 실제 경기 상태와 별개로 재생 상태가 `COMPLETED`가 됩니다.

ViewModel은 `viewModelScope` 아래에 상세 화면의 재생 작업을 만들고, Compose는 `collectAsStateWithLifecycle`로 화면 상태를 읽습니다. 상세에서 **목록**이나 시스템 뒤로 가기를 누르면 해당 작업을 취소합니다. 같은 경기를 다시 열면 초기 상태부터 시작합니다.

ViewModel의 단일 `collectEvents` 소비자는 동일한 시각의 플레이도 순서대로 처리합니다. 초기화나 처음부터 재생을 요청하면 엔진이 이전 세션의 대기 이벤트를 폐기합니다. 이후 `NotificationRuleEngine`은 이 소비자에서 각 이벤트의 `before`와 `after`를 입력받을 수 있습니다.

## GitHub 협업

- `main`에서 작업 브랜치를 만들고 PR에는 변경 범위, 확인 방법, 테스트 결과를 적습니다.
- [AGENTS.md](AGENTS.md)에 프로젝트 규칙을 기록합니다. 알림 구현에 앞서 [알림 가이드라인](docx/클러치%20알림%20발송%20가이드라인%20v1.0.docx)을 확인합니다.
- `NotificationRuleEngine`은 알림 후보를 생성합니다. [알림 판정 정책과 검토 항목](docx/notification-policy-review.md)에 지원 데이터와 후속 연결 지점을 기록했습니다.
