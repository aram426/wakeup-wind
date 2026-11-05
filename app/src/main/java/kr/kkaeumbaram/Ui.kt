package kr.kkaeumbaram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF0E1116)
private val Panel = Color(0xFF171C24)
private val Ink = Color(0xFFF2F4F8)
private val Muted = Color(0xFF8B93A3)
private val Wind = Color(0xFF3B82F6)
private val Warn = Color(0xFFF59E0B)
private val Danger = Color(0xFFEF4444)
private val Calm = Color(0xFF22C55E)

@Composable
fun AppScreen(ui: Ui, vm: MainViewModel) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Wind, background = Bg, surface = Panel, onSurface = Ink)) {
        Row(Modifier.fillMaxSize().background(Bg).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusPane(ui, Modifier.width(300.dp))
            MainCard(ui, vm, Modifier.weight(1f))
            DemoPane(ui, vm, Modifier.width(300.dp))
        }
    }
}

private fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun StatusPane(ui: Ui, m: Modifier) {
    val s = ui.s
    Column(m.fillMaxHeight().background(Panel, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("깨움바람", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (ui.carConnected) "Vehicle SDK 초기화됨" else "차량 미연결 (로그만)", color = if (ui.carConnected) Calm else Warn, fontSize = 15.sp)
        Text("음성: ${ui.voiceEngine}", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Stat("연속 주행", mmss(s.sinceStopSec))
        Stat("다음 환기까지", mmss(maxOf(0, s.intervalSec - s.sinceAlertSec)))
        Stat("단계", s.level.name + if (s.strongMode) " · 세게" else "", when (s.level) { Level.L1 -> Calm; Level.L2 -> Warn; Level.L3 -> Danger })
        Spacer(Modifier.height(8.dp))
        Text("오늘 리포트", color = Muted, fontSize = 14.sp)
        Stat("환기", "${s.ventCount}회")
        Stat("최장 연속 주행", mmss(s.longestDriveSec))
        Stat("평균 반응시간", if (s.reactionMs.isEmpty()) "-" else "${s.avgReactionMs / 1000.0}초")
        Stat("스트레칭 / 낮잠 / 쉼터", "${s.stretches} / ${s.naps} / ${s.restSuggested}")
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 16.sp); Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MainCard(ui: Ui, vm: MainViewModel, m: Modifier) {
    val s = ui.s
    val (title, body, accent) = when (s.phase) {
        Phase.IDLE -> Triple("대기 중", "출발하면 30분마다 차가 환기하고 안부를 물어요.", Muted)
        Phase.DRIVING -> Triple("주행 중", "다음 환기까지 ${mmss(maxOf(0, s.intervalSec - s.sinceAlertSec))}", Wind)
        Phase.VENT -> Triple("환기 중 · ${mmss(60 - s.phaseSec)}", "외기 전환, 팬 강풍, 창문 15%, 시트 통풍", Wind)
        Phase.QUESTION -> Triple(s.question, if (ui.listening) "듣고 있어요…" else "대답해 주세요 (${15 - s.phaseSec}초)", if (s.level == Level.L1) Wind else Warn)
        Phase.STRETCH -> Triple("정차 스트레칭 · ${mmss(90 - s.phaseSec)}", "목 좌우 10초 → 어깨 돌리기 20초 → 허리 비틀기 20초 → 손목·발목 20초", Calm)
        Phase.REST -> Triple("쉼터까지만 가요", "더는 깨우지 않아요. 가까운 곳을 경유지로 넣을게요.", Danger)
        Phase.NAP -> Triple("충전 중 낮잠 · ${mmss(20 * 60 - s.phaseSec)}", "조명 낮춤, 공조 유지. 끝나면 바람으로 깨워요.", Calm)
    }
    Column(m.fillMaxHeight().background(Panel, RoundedCornerShape(16.dp)).padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(72.dp).height(6.dp).background(accent, RoundedCornerShape(3.dp)))
            Text(title, color = Ink, fontSize = if (title.length > 12) 30.sp else 40.sp, fontWeight = FontWeight.Bold, lineHeight = if (title.length > 12) 38.sp else 48.sp)
            Text(body, color = Muted, fontSize = 20.sp, lineHeight = 28.sp)
            if (s.phase == Phase.REST) ui.restOptions.forEachIndexed { i, (r, km) ->
                Text("${i + 1}. ${r.name} · ${"%.1f".format(km)}km · ${r.road}", color = Ink, fontSize = 20.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s.phase) {
                Phase.IDLE -> Big("출발", Wind) { vm.start() }
                Phase.DRIVING -> Big("지금 환기", Wind) { vm.ventNow() }
                Phase.VENT -> {}
                Phase.QUESTION -> { Big("괜찮아요", Calm) { vm.answered() }; Big("응답 없음(시연)", Danger) { vm.noAnswer() } }
                Phase.STRETCH -> Big("다 했어요", Calm) { vm.stretchDone() }
                Phase.REST -> { Big("쉼터 경유", Danger) { vm.restAccept() }; Big("15분 뒤에", Muted) { vm.restDecline() } }
                Phase.NAP -> Big("지금 일어날래요", Calm) { vm.wake() }
            }
        }
    }
}

@Composable
private fun Big(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Text(label, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun DemoPane(ui: Ui, vm: MainViewModel, m: Modifier) {
    Column(m.fillMaxHeight().background(Panel, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("시연 모드 (1초=1분)", color = Ink, fontSize = 16.sp); Switch(ui.demo, { vm.toggleDemo() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("세게 깨워줘", color = Ink, fontSize = 16.sp); Switch(ui.s.strongMode, { vm.strongMode(it) })
        }
        Text("차량 신호 흉내 (에뮬레이터 주입 대신)", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Small("+10분") { vm.fastForward(10) }; Small("+30분") { vm.fastForward(30) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Small("기어 P") { vm.gearPark() }; Small("기어 D") { vm.gearDrive() } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Small("충전 시작") { vm.charge() }; Small("Gleo: 쉼터 가자") { vm.gleo("GO_REST") } }
        Spacer(Modifier.height(6.dp))
        Text("로그", color = Muted, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f)) { items(ui.log) { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 16.sp) } }
    }
}

@Composable
private fun Small(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, shape = RoundedCornerShape(10.dp)) { Text(label, color = Ink, fontSize = 14.sp) }
}
