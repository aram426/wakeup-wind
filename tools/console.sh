#!/bin/sh
# Pleos Connect Virtual Console을 터미널에서 조작한다 (에뮬레이터 신호 주입).
# 준비: adb forward tcp:8095 tcp:8088   (콘솔은 에뮬레이터 안 8088 포트에서 뜬다)
# 사용: tools/console.sh gear Drive | gear Park | charge on|off | soc 25 | speed 80 | hvac power=true | state
# 쓰기 요청은 브라우저 세션 하트비트(3초)가 살아 있어야 받아 주므로 매 호출마다 먼저 보낸다.
C=${CONSOLE:-http://localhost:8095}
beat() { curl -s -m 3 -X POST "$C/browser/session?client=cli" >/dev/null; }
case "$1" in
  gear)   beat; curl -s -m 5 -X POST "$C/vehicle/gear?gear=$2" | head -c 120; echo ;;
  charge) beat; v=false; [ "$2" = on ] && v=true
          curl -s -m 5 -X POST "$C/vehicle/ev-battery?field=charge_port_open&value=$v" | head -c 120; echo
          curl -s -m 5 -X POST "$C/vehicle/ev-battery?field=charge_switch&value=$v" | head -c 120; echo ;;
  soc)    beat; curl -s -m 5 -X POST "$C/vehicle/ev-battery?field=soc&value=$2" | head -c 120; echo ;;
  speed)  beat; curl -s -m 5 -X POST "$C/vehicle/controls?speed=$2" | head -c 120; echo ;;
  hvac)   beat; curl -s -m 5 -X POST "$C/vehicle/hvac?$2" | head -c 200; echo ;;
  state)  curl -s -m 5 "$C/vehicle/state" | ${PY:-$(command -v python3 || echo python)} -c "import sys,json; d=json.load(sys.stdin); print('gear', (d['gear']['actual'] or {}).get('value')); print('hvac power', d['hvac']['power_on'], 'fan', d['hvac']['fan_speed'], 'recirc', d['hvac']['recirc_on']); print('windows', d['body']['windows']); print('ambient', d['body']['ambient_light']); print('ev', {k: d['ev_battery'].get(k) for k in ('state_of_charge','charging_door_opened','charging_activated')})" ;;
  *) echo "usage: $0 gear Drive|Park|Neutral|Reverse | charge on|off | soc N | speed KMH | hvac k=v | state"; exit 1 ;;
esac
