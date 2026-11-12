# 깨움바람 (Kkaeumbaram)

택배 기사 졸음 방지 앱. Pleos App Market 공모전 출품작. 30분마다 차가 환기하고 안부를 묻고, 안 깨면 세우게 만든다.

## 빌드

JDK 17과 Android Studio(Android SDK 34)가 필요하다. Android Studio로 폴더를 열면 Gradle 동기화가 자동으로 돌고, 터미널에서는 아래처럼 한다.

맥:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

제출용 APK는 `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (debug 키 서명. APK는 깃에 올리지 않고 GitHub Release에 첨부).

윈도우(PowerShell 또는 명령 프롬프트):
```
gradlew.bat testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

JDK가 안 잡히면 Android Studio에 들어 있는 JDK를 쓰면 된다. 윈도우 기준 `C:\Program Files\Android\Android Studio\jbr`를 `JAVA_HOME` 환경 변수로 지정한다.

`local.properties`는 각자 컴퓨터에만 있는 파일이다(깃에 안 올라감). 처음 받으면 `local.properties.example`을 복사해 `local.properties`로 이름을 바꾸고 값을 채운다.

```
sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk    # 윈도우. 맥은 /Users/<이름>/Library/Android/sdk
pleos.clientId=<My Project 콘솔의 Client ID>
pleos.clientSecret=<My Project 콘솔의 Client Secret>
```

Pleos SDK는 `settings.gradle.kts`의 Pleos Maven에서 받는다.

## 구조

| 파일 | 역할 |
| --- | --- |
| `Engine.kt` | 상태 기계. Android 의존 없음. 30분 타이머 → 환기 → 질문·반응시간 → L2 세게 → L3 쉼터, 정차 스트레칭, 충전 낮잠 |
| `EngineTest.kt` | 위 흐름의 JVM 테스트 3개 |
| `Car.kt` | `PleosCar`: Vehicle(HVAC·창문·시트·조명·기어·충전) + NaviHelper(경유지) + FusedLocation. 실패는 로그만, 흐름은 계속 |
| `Voice.kt` | `PleosVoice`(TTS/STT ON_DEVICE) → 실패 시 `AndroidVoice`(기본 엔진) |
| `MainViewModel.kt` | Engine 효과를 Car/Voice로 연결, 1초 틱, 시연 모드 |
| `Ui.kt` | 3단 화면: 상태·리포트 / 메인 카드 / 시연 패널·로그 |
| `GleoReceiver.kt` + `assets/gleo_schema.json` | Gleo Interface 연동 준비: App Schema와 Action 브로드캐스트 수신부 (VENT_NOW, I_AM_FINE, GO_REST, STRONG_MODE). App Schema 승인 전이라 실제 Gleo 발화로는 호출되지 않고, 시연은 `adb` 브로드캐스트와 시연 패널 버튼으로 같은 Action을 보낸다 |
| `assets/rest_stops.csv` | 졸음쉼터 좌표(`name,lat,lon,road`). data.go.kr 15028203 한국도로공사 졸음쉼터 전국 253곳 |

## 시연 모드

오른쪽 패널. 주행·낮잠 phase만 1초=1분으로 빨리 감고, 환기·질문·스트레칭은 실시간. 차량 신호(기어 P/D, 충전 시작)는 버튼으로 흉내 낸다. Pleos 에뮬레이터에서는 Virtual Console의 Gear Selection, Charge Port 카드로 같은 신호를 주입할 수 있다.

## Pleos 에뮬레이터 (11/2 설치 완료)

- 시스템 이미지는 `~/Library/Android/sdk/system-images/android-34/pleos_car_emulator/arm64-v8a/`, AVD 이름은 `pleos` (`~/.android/avd/pleos.avd`, 2560x1440, RAM 4GB, cold boot). 2.1.0부터 CRN 주입은 필요 없다.
- 실행: `~/Library/Android/sdk/emulator/emulator -avd pleos -no-snapshot`
- Android Studio도 설치되어 있다(`/Applications/Android Studio.app`). Device Manager에 `pleos`가 보인다.
- **윈도우 설치**: Android Studio > Tools > SDK Manager > SDK Update Sites > Add에 아래 주소를 넣고, SDK Platforms에서 "Pleos Connect" API 34 시스템 이미지를 설치한다. 그다음 Device Manager에서 Automotive 기기로 AVD를 만들고 Cold boot, RAM 4GB로 둔다(Quick boot면 차량 신호가 안 들어온다). 맥용(arm64)과는 다른 피드다.
  ```
  https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
  ```
  공식 안내: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- `tools/console.sh`는 윈도우에서 Git Bash로 실행한다(`sh tools/console.sh state`). `python`이 PATH에 있어야 `state`가 동작한다.

### 실측 결과 (Pleos 에뮬레이터, 11/2)

| 항목 | 결과 |
| --- | --- |
| Vehicle·NaviHelper·FusedLocation 초기화 | 됨 |
| 기어 P/D 구독, 충전 포트 구독 | 됨. Drive → 주행 시작, Park → 스트레칭, 포트 열림 → 낮잠 |
| 외기 전환(recirc off), 팬 6, 시트 통풍 3, 창문 20%, 앰비언트 100 | 됨 (콘솔 state로 확인) |
| 팬 속도 | HVAC 전원이 꺼져 있으면 error code 2. 전원 ON 후 됨. area는 capability로 받아야 함(ROW_1_LEFT 단독은 거부) |
| 실내등 | 에뮬레이터에서 50001 오류가 잦아 호출 제거. 조명 효과는 앰비언트(100/10)만 사용 |
| Pleos TTS ON_DEVICE | `ON_DEVICE_ENGINE_ERROR`. 에뮬레이터엔 온디바이스 엔진이 없음 → HYBRID/SERVER + My Project 앱 등록(registerApp) 필요. Android 기본 TTS도 없음 |
| Pleos STT | 됨(11/5). "두 번째"가 온디바이스로 인식돼 응답 처리까지 확인. 맥 마이크를 쓰려면 아래 "마이크" 절 참고. SDK가 `onEndedRecognition`을 최종 `onUpdated(isFinal=true)`보다 먼저 보내므로 종료 콜백에서 무응답 처리하면 결과를 놓친다(수정함) |
| 주행 중(기어 D) 앱 화면 | 매니페스트 `distractionOptimized=true` 메타데이터로 유지됨(홈 화면이 `distractionOptimized: true`로 인식). 단, 기어가 D로 바뀌는 순간 앱 위에 권한 팝업 같은 시스템 창이 떠 있으면 그 작업 전체가 "주행 중에는 사용할 수 없습니다"로 가려지고 다시 풀리지 않는다 → 첫 실행·권한 허용은 반드시 P에서 끝낸 뒤 D로. 가려지면 P로 돌리고 앱을 닫았다 다시 연다 |

### 오디오 (11/11 실측, Pleos 오디오 포커스·선행 묵음 가이드 반영)

- 말하기 전에 오디오 포커스를 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`(음악은 끊지 않고 줄이기만)으로 요청하고, 말 묶음이 끝나면 반납한다. 포커스를 잡은 뒤 150ms 쉬고 첫 문장을 말한다(첫 음절 잘림 방지).
- 앱은 차량 볼륨을 바꾸지 못한다. `AudioManager.setStreamVolume`을 호출해도 차량 볼륨 그룹 값이 그대로다(`dumpsys car_service`로 확인). 그래서 L2는 볼륨이 아니라 더 센 환기로 깨우고, 안내 문장도 볼륨을 약속하지 않는다.
- 질문이 끝나면(타임아웃·정차·버튼 응답) STT를 바로 닫는다.

### 마이크 (맥 → 에뮬레이터 STT)

1. 에뮬레이터는 반드시 **Terminal 앱에서 직접** 띄운다. Claude Code나 백그라운드에서 띄우면 macOS 마이크 권한 팝업이 안 떠서 무음만 들어간다. 시스템 설정 > 개인정보 보호 및 보안 > 마이크에 "터미널"이 켜져 있어야 한다.
2. 호스트 마이크 전달은 기본 꺼짐. 부팅 후 `adb emu avd hostmicon` 한 번 실행(또는 에뮬레이터 ⋯ > Microphone > "Virtual microphone uses host audio input").
3. 앱에 `RECORD_AUDIO` 권한이 있어야 한다(쉼터 거리 계산용 위치는 `pleos.car.permission.FUSED_LOCATION`, 런타임 권한이라 첫 실행 팝업 또는 `pm grant`). 에뮬레이터 앱은 사용자 10으로 돌기 때문에 adb로 줄 때는 `pm grant --user 10 …`. 첫 실행 팝업에서 허용하거나 `adb shell pm grant --user 10 kr.kkaeumbaram android.permission.RECORD_AUDIO`.

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

## 데모 스크립트

`tools/demo.sh`가 위 시나리오를 처음부터 끝까지 순서대로 주입한다(윈도우는 Git Bash에서 `sh tools/demo.sh`). 에뮬레이터 `pleos`를 Terminal에서 띄우고 앱을 설치한 뒤 실행하면 adb 기기·앱 설치·콘솔 응답을 점검하고, 단계마다 `[mm:ss]`와 심사위원이 봐야 할 화면을 출력한다. 사람이 확인해야 하는 곳(P에서 첫 실행·권한, 쉼터 카드)은 Enter를 기다린다. `SKIP=1 tools/demo.sh`면 30초 주행 대기 대신 Gleo `VENT_NOW` 브로드캐스트로 바로 환기한다.

```
adb emu avd hostmicon     # 마이크 쓸 때만
tools/demo.sh
```

아래 시각은 Engine.kt 상수(환기 60초, 무응답 15초, 스트레칭 90초, 낮잠 20분, 간격 30분)와 시연 모드 60배속으로 계산한 값이다. 기어 D 주입 시점을 00:00으로 잡았고, 1초 틱 때문에 단계마다 1~2초 늦을 수 있다. 리허설에서 실측해 고칠 것.

| 단계 | 트리거 | 화면·차량에서 보이는 것 | 시각(계산값) |
| --- | --- | --- | --- |
| 0 | `gear Park`, `pm grant RECORD_AUDIO`, `am start`, Enter | 대기 중 카드, 시연 패널, 팝업 없음 | 준비 |
| 0 | `hvac power=true` | (콘솔 state에서 power_on) | 준비 |
| 1 | `gear Drive` | 주행 중, 다음 환기까지 카운트다운(1초=1분) | 00:00 |
| 2 | 타이머(30분→30초) 또는 `SKIP=1`이면 즉시 | L1 환기: 외기·팬 6·창문 20%·앰비언트 ON, "환기할 시간이에요" | 00:30 |
| 3 | 환기 60초 경과 | 환기 원복, 질문 "다음 배송지는 몇 번째예요?", 듣기 표시. 대답 안 함 | 01:30 |
| 4 | 무응답 15초 | L2: "대답이 없네요. 더 세게 깨울게요", 세게 환기(팬 8·창문 3단) | 01:45 |
| 5 | 환기 60초 경과 | 질문 "삐 소리가 나면 네라고 말해 주세요". 대답 안 함 | 02:45 |
| 6 | 무응답 15초 | L3: "더는 깨우지 않을게요", 쉼터 3곳 카드(거리 km), Enter 대기 | 03:00 |
| 7 | `gear Park` | "잠깐 섰네요. 90초만", 스트레칭 카드, 환기 OFF | 03:00+ |
| 8 | 스트레칭 90초 경과 | "수고했어요", 대기 중 | 04:30 |
| 9 | `charge on` | "충전 시작했어요. 20분만 눈 붙일까요?", 조명 OFF, 낮잠 카드 | 04:30+ |
| 10 | 낮잠 20분→20초 | 조명 ON, 세게 환기, "20분 지났어요. 일어나요". 리포트: 환기 2회, 평균 반응시간 —(무응답이라 표본 없음), 최장 연속 30:00, 휴식 이행 0/1 | 04:50 |
| 11 | 기상 환기 60초 경과 | 팬·창문 원복 | 05:50 |

총 소요 약 6분(계산값) + Enter 두 번의 대기 시간. 11/11 리허설 실측: 단계마다 계산값보다 1~3초 늦고, Enter 대기 18초 포함 6분 22초. L1 → L2 → L3 쉼터 카드(기어 D에서 화면 유지) → 스트레칭 → 낮잠 → 기상 환기 원복까지 끊김 없이 돌았다.

스크립트는 시작할 때 호스트 마이크를 끈다(`adb emu avd hostmicoff`). 켜 두면 주변 소리나 TTS 되울림이 대답으로 인식돼 무응답 시나리오가 L2로 넘어가지 않는다. 대답하는 장면을 찍을 때는 `MIC=1 tools/demo.sh`.
