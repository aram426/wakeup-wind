# 깨움바람 (Kkaeumbaram)

택배 기사 졸음 방지 앱. Pleos App Market 공모전 출품작. 30분마다 차가 환기하고 안부를 묻고, 안 깨면 세우게 만든다.

## 빌드

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # JDK 17 필요
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`의 `sdk.dir`를 본인 Android SDK 경로로 맞춘다. Pleos SDK는 `settings.gradle.kts`의 Pleos Maven에서 받는다.

## 구조

| 파일 | 역할 |
| --- | --- |
| `Engine.kt` | 상태 기계. Android 의존 없음. 30분 타이머 → 환기 → 질문·반응시간 → L2 세게 → L3 쉼터, 정차 스트레칭, 충전 낮잠 |
| `EngineTest.kt` | 위 흐름의 JVM 테스트 3개 |
| `Car.kt` | `PleosCar`: Vehicle(HVAC·창문·시트·조명·기어·충전) + NaviHelper(경유지) + FusedLocation. 실패는 로그만, 흐름은 계속 |
| `Voice.kt` | `PleosVoice`(TTS/STT ON_DEVICE) → 실패 시 `AndroidVoice`(기본 엔진) |
| `MainViewModel.kt` | Engine 효과를 Car/Voice로 연결, 1초 틱, 시연 모드 |
| `Ui.kt` | 3단 화면: 상태·리포트 / 메인 카드 / 시연 패널·로그 |
| `GleoReceiver.kt` + `assets/gleo_schema.json` | Gleo 발화 → Action 브로드캐스트 (VENT_NOW, I_AM_FINE, GO_REST, STRONG_MODE) |
| `assets/rest_stops.csv` | 졸음쉼터 좌표. 지금은 수도권 샘플 12곳(근사값). 제출 전 data.go.kr 15028203 CSV로 교체 |

## 시연 모드

오른쪽 패널. 주행·낮잠 phase만 1초=1분으로 빨리 감고, 환기·질문·스트레칭은 실시간. 차량 신호(기어 P/D, 충전 시작)는 버튼으로 흉내 낸다. Pleos 에뮬레이터에서는 Virtual Console의 Gear Selection, Charge Port 카드로 같은 신호를 주입할 수 있다.

## Pleos 에뮬레이터 (11/2 설치 완료)

- 시스템 이미지는 `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a/`, AVD 이름은 `pleos` (`~/.android/avd/pleos.avd`, 2560x1440, RAM 4GB, cold boot). 2.1.0부터 CRN 주입은 필요 없다.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd pleos -no-snapshot`
- Android Studio도 설치되어 있다(`/Applications/Android Studio.app`). Device Manager에 `pleos`가 보인다.

### 실측 결과 (Pleos 에뮬레이터, 11/2)

| 항목 | 결과 |
| --- | --- |
| Vehicle·NaviHelper·FusedLocation 초기화 | 됨 |
| 기어 P/D 구독, 충전 포트 구독 | 됨. Drive → 주행 시작, Park → 스트레칭, 포트 열림 → 낮잠 |
| 외기 전환(recirc off), 팬 6, 시트 통풍 3, 창문 20%, 앰비언트 100 | 됨 (콘솔 state로 확인) |
| 팬 속도 | HVAC 전원이 꺼져 있으면 error code 2. 전원 ON 후 됨. area는 capability로 받아야 함(ROW_1_LEFT 단독은 거부) |
| 실내등 | ON/OFF 되나 가끔 50001 오류 |
| Pleos TTS ON_DEVICE | `ON_DEVICE_ENGINE_ERROR`. 에뮬레이터엔 온디바이스 엔진이 없음 → HYBRID/SERVER + My Project 앱 등록(registerApp) 필요. Android 기본 TTS도 없음 |
| Pleos STT | 권한 `STT_SERVICE` 선언 후 초기화됨, 마이크 실측은 아직 |
| 주행 중(기어 D) 앱 화면 | 기본으로는 시스템이 "주행 중에는 사용할 수 없습니다"로 가림. 매니페스트 `distractionOptimized=true` 메타데이터를 넣으니 화면이 유지됨(제어는 가림 상태에서도 실행됐음). 심사 때 이 플래그 사용이 허용되는지는 확인 필요 |

### Virtual Console을 터미널에서

콘솔은 에뮬레이터 안 8088 포트에서 뜬다. `adb forward tcp:8095 tcp:8088` 후 브라우저로 http://localhost:8095 를 열거나, `tools/console.sh`로 주입한다.

```
adb forward tcp:8095 tcp:8088
tools/console.sh gear Drive      # 주행 시작
tools/console.sh gear Park       # 정차 → 스트레칭
tools/console.sh charge on       # 충전 포트 열림 → 낮잠
tools/console.sh hvac power=true # 팬 속도 제어 전제
tools/console.sh state
```

쓰기 요청은 브라우저 세션 하트비트(`POST /browser/session?client=…`, 3초)가 살아 있어야 받는다. 스크립트가 매번 보낸다.
