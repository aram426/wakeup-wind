#!/bin/sh
# 데모 시나리오를 처음부터 끝까지 한 번에 돌린다(SCRUM-12). 에뮬레이터 pleos가 떠 있고 앱이 설치돼 있어야 한다.
# 사용: tools/demo.sh            (윈도우는 Git Bash에서 sh tools/demo.sh)
#       SKIP=1 tools/demo.sh     30초 주행 대기 대신 Gleo VENT_NOW 브로드캐스트로 바로 환기
#       MIC=1 tools/demo.sh      호스트 마이크를 끄지 않는다(대답하는 장면을 따로 찍을 때)
# 시간은 Engine.kt 상수와 시연 모드(주행·낮잠 1초=1분, 환기·질문·스트레칭 실시간)에서 계산한 값. 리허설에서 확인할 것.
cd "$(dirname "$0")/.." || exit 1
PKG=kr.kkaeumbaram
T0=$(date +%s)
say() { t=$(( $(date +%s) - T0 )); printf '[%02d:%02d] %s\n' $((t/60)) $((t%60)) "$1"; }
wait_s() { say "  ... ${1}초 대기: $2"; sleep "$1"; }
pause() { say "$1"; printf '      Enter를 누르면 계속 > '; read -r _; }
con() { sh tools/console.sh "$@" >/dev/null; }

# 사전 점검
adb get-state >/dev/null 2>&1 || { echo "adb 기기가 없다. 에뮬레이터 pleos를 Terminal에서 먼저 띄울 것."; exit 1; }
adb shell pm path $PKG 2>/dev/null | grep -q package || { echo "앱이 설치돼 있지 않다. ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk"; exit 1; }
adb forward tcp:8095 tcp:8088 >/dev/null || { echo "adb forward 실패 (콘솔 포트 8088)."; exit 1; }
sh tools/console.sh state >/dev/null 2>&1 || { echo "Virtual Console이 응답하지 않는다. 에뮬레이터를 Cold boot로 다시 띄울 것."; exit 1; }

# 무응답 시나리오라 호스트 마이크를 끈다. 켜 두면 주변 소리·TTS 되울림이 대답으로 인식돼 L2로 안 넘어간다(11/11 리허설)
[ -n "$MIC" ] || adb emu avd hostmicoff >/dev/null

# 0. P에서 앱 실행·권한 (주행 중 시스템 팝업이 뜨면 화면이 가려지므로 반드시 P에서 끝낸다)
con gear Park
U=$(adb shell am get-current-user | tr -d '\r')   # 에뮬레이터 앱은 사용자 10으로 돈다. --user 없이 grant하면 0번에만 들어가 팝업이 뜬다
adb shell pm grant --user "$U" $PKG android.permission.RECORD_AUDIO 2>/dev/null
adb shell pm grant --user "$U" $PKG pleos.car.permission.FUSED_LOCATION 2>/dev/null   # 없으면 쉼터 거리가 기본 좌표(서울 양재) 기준
adb emu geo fix 127.0439 37.4598 >/dev/null   # 에뮬레이터 GPS를 서울만남의광장으로(기본값은 미국)
adb shell am start -n $PKG/.MainActivity >/dev/null
pause "앱 실행(기어 P). 화면에 '대기 중' 카드와 오른쪽 시연 패널, 위에 팝업이 없는지 확인."
con hvac power=true;  say "HVAC 전원 ON (팬 속도 제어 전제)"

# 1. 주행 → 30분(시연 30초) 뒤 환기
con gear Drive;       say "기어 D: 주행 시작. '주행 중', 다음 환기까지 카운트다운이 1초에 1분씩 준다"
if [ -n "$SKIP" ]; then
  adb shell am broadcast -a VENT_NOW -n $PKG/.GleoReceiver >/dev/null; say "Gleo VENT_NOW 브로드캐스트로 30분 대기 건너뜀"
else
  wait_s 32 "30분 주행 타이머(시연 60배속)"
fi
say "환기 시작(L1): 외기 전환·팬 6·창문 20%·실내등 ON, '환기할 시간이에요'"
wait_s 62 "환기 60초(실시간)"
say "질문 1: '다음 배송지는 몇 번째예요?' 듣기 중. ★ 아무도 대답하지 않는다"
wait_s 16 "무응답 15초"
say "L2: '대답이 없네요. 더 세게 깨울게요' → 세게 환기(팬 8·창문 3단)"
wait_s 62 "세게 환기 60초"
say "질문 2: '삐 소리가 나면 네라고 말해 주세요'. ★ 역시 무응답"
wait_s 16 "무응답 15초"
say "L3: '더는 깨우지 않을게요. 가까운 쉼터까지만 가요' + 쉼터 3곳 카드(거리 km)"
pause "쉼터 카드 확인. (원하면 '경유지 추가' 눌러 NaviHelper 시연)"

# 2. 정차 → 스트레칭 90초
con gear Park;        say "기어 P: '잠깐 섰네요. 90초만 목이랑 어깨 풀어요' → 스트레칭 카드, 환기 OFF"
wait_s 92 "스트레칭 90초(실시간)"
say "스트레칭 끝: '수고했어요. 출발하면 다시 지켜볼게요' → 대기"

# 3. 충전 → 낮잠 20분(시연 20초) → 기상 환기
con charge on;        say "충전 포트 열림: '충전 시작했어요. 20분만 눈 붙일까요?' → 조명 OFF, 낮잠 카드"
wait_s 22 "낮잠 20분(시연 60배속)"
say "기상: 조명 ON, 세게 환기, '20분 지났어요. 일어나요'. 리포트: 환기 2회, 평균 반응시간 —, 휴식 이행 0/1"
wait_s 62 "기상 환기 60초 뒤 팬·창문 원복"
con charge off
say "끝. 총 소요 $(( ($(date +%s) - T0) / 60 ))분 $(( ($(date +%s) - T0) % 60 ))초 (Enter 대기 시간 포함)"
