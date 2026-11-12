package kr.kkaeumbaram

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Figma "라이트 v2" 팔레트
private val Bg = Color(0xFFC9CDCD)
private val Panel = Color(0xFFF2F2F2)
private val Ink = Color(0xFF0B0C0C)
private val Ink2 = Color(0xFF222525)
private val Muted = Color(0xFF5D6161)
private val Line = Color(0xFFDADDDD)
private val Dim = Color(0xFFA7ADAD)
private val Green = Color(0xFF1E9E4F)
private val GreenSoft = Color(0xFFDFF2C9)
private val Yellow = Color(0xFFF5B301)
private val YellowSoft = Color(0xFFFDF0CC)
private val Red = Color(0xFFE5383B)
private val RedSoft = Color(0xFFFAD7D8)
private val Wind = Color(0xFF1D4ED8)

private fun Level.color() = when (this) { Level.L1 -> Green; Level.L2 -> Yellow; Level.L3 -> Red }
private fun Level.soft() = when (this) { Level.L1 -> GreenSoft; Level.L2 -> YellowSoft; Level.L3 -> RedSoft }

// 모든 패널·컴포넌트 모서리 반경 3px (화면 2560 기준 절대값)
private val Corner = RoundedCornerShape(3f)
private val PaneShape = Corner
private fun Modifier.pane() = this
    .shadow(4.dp, PaneShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
    .background(Panel, PaneShape)

private const val SlideMs = 450

// 화면 규격과 앱 패널 절대 좌표 (px). 시안 프레임은 폭 1280 기준.
private const val ScreenW = 2560f
private const val PanelX = 853f
private const val PanelY = 90f
private const val PanelW = 1682f
private const val PanelH = 1210f
private const val DesignW = 1280f

@Composable
fun AppScreen(ui: Ui, vm: MainViewModel?) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Wind, background = Bg, surface = Panel, onSurface = Ink)) {
        // 화면 2560x1440 중 앱 패널은 x853~2535, y90~1300 (1682x1210). 화면 폭 비율(k)로 어떤 해상도에서도 같은 비례를 유지한다.
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val k = constraints.maxWidth / ScreenW
            // 패널 안쪽은 시안(폭 1280) 기준 dp로 짜여 있어, 패널 폭에 맞춰 density를 키우면 dp·sp가 함께 비례 확대된다.
            val u = PanelW * k / DesignW
            val inner = Density(u, LocalDensity.current.fontScale)
            Box(Modifier.offset { IntOffset((PanelX * k).roundToInt(), (PanelY * k).roundToInt()) }) {
                CompositionLocalProvider(LocalDensity provides inner) {
                    Row(Modifier.size(DesignW.dp, (PanelH * k / u).dp).background(Bg).padding(21.dp)) {
                        StatusPane(ui, vm, Modifier.width(298.dp))
                        Spacer(Modifier.width(18.dp))
                        MainCard(ui, vm, Modifier.weight(1f))
                        // 어드민 뷰: 오른쪽 패널이 밀려 나오며 가운데 카드가 줄어들고, 닫을 땐 반대로 밀려 들어가며 카드가 커진다
                        AnimatedVisibility(
                            visible = ui.admin,
                            enter = expandHorizontally(tween(SlideMs, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
                                slideInHorizontally(tween(SlideMs, easing = FastOutSlowInEasing)) { it },
                            exit = shrinkHorizontally(tween(SlideMs, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
                                slideOutHorizontally(tween(SlideMs, easing = FastOutSlowInEasing)) { it },
                        ) {
                            Row {
                                Spacer(Modifier.width(18.dp))
                                DemoPane(ui, vm, Modifier.width(298.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun StatusPane(ui: Ui, vm: MainViewModel?, m: Modifier) {
    val s = ui.s
    // 환기·질문·쉼터 화면에서는 오늘 리포트 줄이 촘촘해진다 (Figma v3)
    val vent = s.phase == Phase.VENT || s.phase == Phase.QUESTION || s.phase == Phase.REST
    Box(m.fillMaxHeight().pane()) {
        Column(Modifier.padding(22.dp)) {
            Column(Modifier.height(120.dp)) {
                Text("깨움바람", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                AnimatedVisibility(ui.admin, enter = fadeIn(tween(SlideMs)), exit = fadeOut(tween(200))) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.clip(Corner).background(s.level.soft()).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(8.dp).background(if (ui.carConnected) s.level.color() else Dim, Corner))
                            Text(if (ui.carConnected) "Vehicle SDK 초기화됨" else "차량 미연결 (로그만)", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("음성: ${ui.voiceEngine}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            SectionLabel("주행 현황")
            Spacer(Modifier.height(2.dp))
            StatRow("연속 주행", mmss(s.sinceStopSec))
            StatRow("단계", "", badge = s.level)
            Spacer(Modifier.height(26.dp))
            SectionLabel("오늘 리포트", alpha = if (vent) 0.8f else 1f)
            Spacer(Modifier.height(2.dp))
            StatRow("환기", "${s.ventCount}회", compact = vent)
            StatRow("최장 연속 주행", mmss(s.longestDriveSec), compact = vent)
            StatRow("평균 반응시간", if (s.reactionMs.isEmpty()) "-" else "${s.avgReactionMs / 1000.0}초", compact = vent)
            StatRow("휴식 이행", "${s.restAccepted} / ${s.restSuggested}", compact = vent)
        }
        if (s.phase == Phase.DRIVING) VentButton(s.level, Modifier.align(Alignment.BottomCenter).padding(bottom = 55.dp)) { vm?.ventNow() }
    }
}

@Composable
private fun SectionLabel(text: String, alpha: Float = 1f) =
    Text(text, color = Muted.copy(alpha = alpha), fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
private fun StatRow(label: String, value: String, compact: Boolean = false, badge: Level? = null) {
    val line = if (compact) Modifier else Modifier.drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
    Row(Modifier.fillMaxWidth().height(if (compact) 34.dp else 48.dp).then(line), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.Medium)
        if (badge != null) {
            Box(Modifier.clip(Corner).background(badge.color()).padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text(badge.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else if (compact) Text(value, color = Ink2, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        else Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 지금 환기: 85x62 정사각 버튼, 단계 색. 바람 아이콘은 Figma 벡터(선 2.67, 둥근 끝) 그대로. */
@Composable
private fun VentButton(level: Level, m: Modifier, onClick: () -> Unit) {
    val d = LocalDensity.current.density
    Canvas(
        m.size(85.dp, 62.dp)
            .shadow(8.dp, Corner, ambientColor = Wind.copy(alpha = 0.35f), spotColor = Wind.copy(alpha = 0.35f))
            .background(level.color())
            .clickable(onClick = onClick),
    ) {
        withTransform({ scale(d, d, Offset.Zero); translate(27f + 4f, 15f + 2.667f) }) {
            val p = Path().apply {
                moveTo(0f, 8f); lineTo(14.667f, 8f); arcTo(Rect(10.667f, 0f, 18.667f, 8f), 90f, -270f, false)
                moveTo(0f, 13.333f); lineTo(21.333f, 13.333f); arcTo(Rect(17.333f, 13.333f, 25.333f, 21.333f), -90f, 270f, false)
                moveTo(0f, 18.667f); lineTo(10.667f, 18.667f); arcTo(Rect(6.667f, 18.667f, 14.667f, 26.667f), -90f, 270f, false)
            }
            drawPath(p, Color.White, style = Stroke(2.667f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** 어드민 전환 버튼 36x36. Figma Button/Admin Icon 인스턴스(사람 + 방패, 선 #A7ADAD 2). */
@Composable
private fun AdminButton(onClick: () -> Unit, m: Modifier = Modifier) {
    val d = LocalDensity.current.density
    val shape = Corner
    Canvas(m.size(36.dp).clip(shape).background(Panel).border(2.dp, Dim, shape).clickable(onClick = onClick)) {
        withTransform({ scale(d, d, Offset.Zero); translate(2f, 2f) }) {
            val st = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(Dim, 4f, Offset(14f, 9f), style = st)
            val shoulders = Path().apply {
                moveTo(17f, 26f); lineTo(5f, 26f); lineTo(5f, 24f)
                cubicTo(5f, 19.6f, 9f, 17f, 14f, 17f); cubicTo(16.2f, 17f, 18.2f, 17.5f, 19.8f, 18.5f)
            }
            drawPath(shoulders, Dim, style = st)
            val shield = Path().apply {
                moveTo(24f, 18f); lineTo(28f, 20f); lineTo(28f, 23.5f)
                cubicTo(28f, 26f, 26.3f, 27.8f, 24f, 29f); cubicTo(21.7f, 27.8f, 20f, 26f, 20f, 23.5f)
                lineTo(20f, 20f); close()
            }
            drawPath(shield, Dim, style = st)
        }
    }
}

@Composable
private fun MainCard(ui: Ui, vm: MainViewModel?, m: Modifier) {
    val s = ui.s
    val lv = s.level
    Box(m.fillMaxHeight().pane().clip(PaneShape)) {
        when (s.phase) {
            Phase.DRIVING -> DrivingScene(ui.speedKmh, Modifier.fillMaxSize())
            Phase.VENT -> VentScene(Modifier.fillMaxSize())
            Phase.IDLE -> DrivingScene(0f, Modifier.fillMaxSize())
            Phase.QUESTION -> ListeningScene(Modifier.fillMaxSize())
            Phase.REST -> RestScene(Modifier.fillMaxSize())
            else -> {}
        }
        when (s.phase) {
            Phase.DRIVING -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Box(Modifier.width(71.dp).height(6.dp).background(lv.color(), Corner))
                Spacer(Modifier.height(10.dp))
                Text("주행 중", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text("다음 환기까지", color = Ink2, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(mmss(maxOf(0, s.intervalSec - s.sinceAlertSec)), color = Ink, fontSize = 176.sp, lineHeight = 176.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                val filled = (s.sinceAlertSec * 10f / s.intervalSec).toInt().coerceIn(0, 10)
                Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(10) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it < filled) lv.color() else Line, Corner)) }
                }
            }
            Phase.VENT -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Box(Modifier.width(71.dp).height(6.dp).background(lv.color(), Corner))
                Spacer(Modifier.height(10.dp))
                Text("환기 중", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Phase.IDLE -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Box(Modifier.width(71.dp).height(6.dp).background(Dim, Corner))
                Spacer(Modifier.height(10.dp))
                Text("대기 중", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Phase.QUESTION -> QuestionPhase(ui, vm)
            Phase.REST -> RestPhase(ui, vm)
            else -> OtherPhase(ui, vm)
        }
        AnimatedVisibility(!ui.admin, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 10.dp), enter = fadeIn(tween(SlideMs)), exit = fadeOut(tween(150))) {
            AdminButton({ vm?.toggleAdmin() })
        }
    }
}

private val GreenDeep = Color(0xFF15803D)
private val RedDeep = Color(0xFFB91C1C)
private val RedSoftTag = Color(0xFFFEE2E2)
private val WindSoft = Color(0xFFDBEAFE)

/** 질문 화면 (Figma v3): 마이크 장면 위에 듣는 중 칩, 아래에 남은 시간 막대와 답 버튼. */
@Composable
private fun QuestionPhase(ui: Ui, vm: MainViewModel?) {
    val s = ui.s
    val left = (15 - s.phaseSec).coerceIn(0, 15)
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Box(Modifier.width(71.dp).height(6.dp).background(s.level.color(), Corner))
        Spacer(Modifier.height(10.dp))
        Text(s.question, color = Ink, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(Corner).background(WindSoft).padding(start = 12.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(Wind, Corner))
            Text(if (ui.listening) "듣고 있어요…" else "대답해 주세요", color = Wind, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("대답할 수 있는 시간", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
            Text("${left}초", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(15) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it < left) s.level.color() else Line, Corner)) }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).height(80.dp)
                    .shadow(6.dp, Corner, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                    .background(GreenDeep, Corner).clickable { vm?.answered() },
                contentAlignment = Alignment.Center,
            ) { Text("괜찮아요", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.width(180.dp).height(56.dp).clip(Corner).border(2.dp, Muted, Corner).clickable { vm?.noAnswer() },
                contentAlignment = Alignment.Center,
            ) { Text("응답 없음(시연)", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** 쉼터 제안 화면 (Figma v3): 가까운 쉼터 카드(첫 번째가 강조)와 "15분 뒤에" 버튼. 카드를 누르면 그 쪽으로 경유. */
@Composable
private fun RestPhase(ui: Ui, vm: MainViewModel?) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Box(Modifier.width(71.dp).height(6.dp).background(RedDeep, Corner))
        Spacer(Modifier.height(10.dp))
        Text("쉼터까지만 가요", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("더는 깨우지 않아요. 가까운 곳을 경유지로 넣을게요.", color = Ink2, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.restOptions.forEachIndexed { i, (r, km) -> RestOption(i + 1, r, km, selected = i == 0) { vm?.restAccept() } }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.height(38.dp).clip(Corner).border(2.dp, Muted, Corner).clickable { vm?.restDecline() }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("15분 뒤에", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun RestOption(rank: Int, stop: RestStop, km: Double, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) RedDeep else Ink
    Row(
        Modifier.fillMaxWidth().height(80.dp).clip(Corner).background(Color.White)
            .border(if (selected) 3.dp else 2.dp, if (selected) RedDeep else Dim, Corner)
            .clickable(onClick = onClick).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).background(if (selected) RedDeep else Line, Corner), contentAlignment = Alignment.Center) {
            Text("$rank", color = if (selected) Color.White else Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stop.name, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (selected) Box(Modifier.clip(Corner).background(RedSoftTag).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("가장 가까움", color = RedDeep, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(stop.road, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text("${"%.1f".format(km)}km", color = accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

/** Figma에 아직 없는 단계(스트레칭·낮잠). 같은 팔레트로 글과 버튼만. */
@Composable
private fun OtherPhase(ui: Ui, vm: MainViewModel?) {
    val s = ui.s
    val (title, body, accent) = when (s.phase) {
        Phase.STRETCH -> Triple("정차 스트레칭 · ${mmss(90 - s.phaseSec)}", "목 좌우 10초 → 어깨 돌리기 20초 → 허리 비틀기 20초 → 손목·발목 20초", Green)
        Phase.NAP -> Triple("충전 중 낮잠 · ${mmss(20 * 60 - s.phaseSec)}", "조명 낮춤, 공조 유지. 끝나면 바람으로 깨워요.", Green)
        else -> Triple("", "", Dim)
    }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(71.dp).height(6.dp).background(accent, Corner))
            Text(title, color = Ink, fontSize = if (title.length > 12) 28.sp else 34.sp, fontWeight = FontWeight.Bold, lineHeight = if (title.length > 12) 36.sp else 42.sp)
            Text(body, color = Muted, fontSize = 20.sp, lineHeight = 28.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s.phase) {
                Phase.STRETCH -> Big("다 했어요", Green) { vm?.stretchDone() }
                Phase.NAP -> Big("지금 일어날래요", Green) { vm?.wake() }
                else -> {}
            }
        }
    }
}

@Composable
private fun Big(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(Corner).background(color).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DemoPane(ui: Ui, vm: MainViewModel?, m: Modifier) {
    Box(m.fillMaxHeight().pane()) {
        Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 62.dp)) {
            SectionLabel("시연 · 디버그")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(Corner).background(Bg).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow("시연 모드 (1초=1분)", ui.demo) { vm?.toggleDemo() }
                ToggleRow("세게 깨워줘", ui.s.strongMode) { vm?.strongMode(!ui.s.strongMode) }
            }
            Spacer(Modifier.height(22.dp))
            SectionLabel("차량 신호 흉내")
            Spacer(Modifier.height(2.dp))
            Text("에뮬레이터 주입 대신", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalRow("+10분" to { vm?.fastForward(10) }, "+30분" to { vm?.fastForward(30) })
                SignalRow("기어 P" to { vm?.gearPark() }, "기어 D" to { vm?.gearDrive() })
                SignalRow("충전 시작" to { vm?.charge() }, "Gleo: 쉼터 가자" to { vm?.gleo("GO_REST") })
            }
            Spacer(Modifier.height(22.dp))
            SectionLabel("로그")
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).clip(Corner).background(Bg).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(ui.log) { i, line ->
                    Text(line, color = if (i == 0) Ink else Ink2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        // 어드민 뷰에서의 전환 버튼 위치: 패널 아래 가운데
        AdminButton({ vm?.toggleAdmin() }, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val shape = Corner
        Box(
            Modifier.size(64.dp, 36.dp).clip(shape).background(if (on) Wind else Line).border(2.dp, if (on) Wind else Muted, shape).clickable(onClick = onClick),
        ) {
            Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 5.dp).size(22.dp).background(if (on) Color.White else Muted, Corner))
        }
    }
}

@Composable
private fun SignalRow(a: Pair<String, () -> Unit>, b: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Signal(a.first, a.second, Modifier.weight(1f)); Signal(b.first, b.second, Modifier.weight(1f))
    }
}

@Composable
private fun Signal(label: String, onClick: () -> Unit, m: Modifier) {
    val shape = Corner
    Box(m.height(56.dp).clip(shape).background(Panel).border(2.dp, Muted, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- Android Studio Preview (에뮬레이터 없이 화면만 확인; Split/Design 탭) ----
// vm = null 이라 버튼은 안 눌림. 상태만 바꿔가며 디자인 확인용.
private val previewStops = listOf(RestStop("안성 졸음쉼터", 37.0, 127.2, "경부고속도로") to 3.2, RestStop("오산 휴게소", 37.1, 127.1, "경부고속도로") to 8.7)
private fun previewUi(phase: Phase, sec: Int = 0, q: String = "", level: Level = Level.L1, admin: Boolean = false) = Ui(
    s = State(phase = phase, phaseSec = sec, level = level, sinceStopSec = 95 * 60, sinceAlertSec = 27 * 60, question = q, ventCount = 3, longestDriveSec = 95 * 60, reactionMs = listOf(2100, 3400, 1200), restSuggested = 2, restAccepted = 1),
    carConnected = true, voiceEngine = "Pleos TTS/STT", listening = phase == Phase.QUESTION,
    restOptions = if (phase == Phase.REST) previewStops else emptyList(),
    log = listOf("환기 3회째", "기어 D", "Vehicle SDK 초기화됨"),
    admin = admin,
)

@Preview(name = "주행 L1", widthDp = 2560, heightDp = 1440) @Composable private fun PvDriving() = AppScreen(previewUi(Phase.DRIVING), null)
@Preview(name = "주행 L1 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvDrivingAdmin() = AppScreen(previewUi(Phase.DRIVING, admin = true), null)
@Preview(name = "주행 L2", widthDp = 2560, heightDp = 1440) @Composable private fun PvDrivingL2() = AppScreen(previewUi(Phase.DRIVING, level = Level.L2), null)
@Preview(name = "주행 L2 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvDrivingL2Admin() = AppScreen(previewUi(Phase.DRIVING, level = Level.L2, admin = true), null)
@Preview(name = "환기 L1", widthDp = 2560, heightDp = 1440) @Composable private fun PvVent() = AppScreen(previewUi(Phase.VENT, 12), null)
@Preview(name = "환기 L1 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvVentAdmin() = AppScreen(previewUi(Phase.VENT, 12, admin = true), null)
@Preview(name = "환기 L2", widthDp = 2560, heightDp = 1440) @Composable private fun PvVentL2() = AppScreen(previewUi(Phase.VENT, 12, level = Level.L2), null)
@Preview(name = "환기 L2 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvVentL2Admin() = AppScreen(previewUi(Phase.VENT, 12, level = Level.L2, admin = true), null)
@Preview(name = "대기", widthDp = 2560, heightDp = 1440) @Composable private fun PvIdle() = AppScreen(previewUi(Phase.IDLE), null)
@Preview(name = "대기 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvIdleAdmin() = AppScreen(previewUi(Phase.IDLE, admin = true), null)
@Preview(name = "질문", widthDp = 2560, heightDp = 1440) @Composable private fun PvQuestion() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?"), null)
@Preview(name = "질문 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvQuestionAdmin() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?", admin = true), null)
@Preview(name = "질문 L2", widthDp = 2560, heightDp = 1440) @Composable private fun PvQuestionL2() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?", level = Level.L2), null)
@Preview(name = "스트레칭", widthDp = 2560, heightDp = 1440) @Composable private fun PvStretch() = AppScreen(previewUi(Phase.STRETCH, 20), null)
@Preview(name = "휴식 제안", widthDp = 2560, heightDp = 1440) @Composable private fun PvRest() = AppScreen(previewUi(Phase.REST, level = Level.L3), null)
@Preview(name = "휴식 제안 어드민", widthDp = 2560, heightDp = 1440) @Composable private fun PvRestAdmin() = AppScreen(previewUi(Phase.REST, level = Level.L3, admin = true), null)
@Preview(name = "쪽잠", widthDp = 2560, heightDp = 1440) @Composable private fun PvNap() = AppScreen(previewUi(Phase.NAP, 8 * 60), null)
