package kr.kkaeumbaram

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
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
private val Yellow = Color(0xFFF5B301)
private val Red = Color(0xFFE5383B)
private val Wind = Color(0xFF1D4ED8)

private val GreenDeep = Color(0xFF15803D)
private val IdleGray = Color(0xFF8B93A3)

private fun Level.color() = when (this) { Level.L1 -> Green; Level.L2 -> Yellow; Level.L3 -> Red }
/** 단계 배지는 L1만 한 톤 진한 초록 (Figma v2 리뷰 반영). */
private fun Level.badge() = when (this) { Level.L1 -> GreenDeep; Level.L2 -> Yellow; Level.L3 -> Red }

// 모든 패널·컴포넌트 모서리 반경 3px (화면 2560 기준 절대값)
private val Corner = RoundedCornerShape(3f)
private val PaneShape = Corner
private fun Modifier.pane() = this
    .shadow(4.dp, PaneShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
    .background(Panel, PaneShape)

private const val SlideMs = 450

// 화면 규격과 앱 패널 절대 좌표 (px). 시안 프레임은 폭 1280 기준.
private const val PanelW = 1682f
private const val PanelH = 1210f
private const val DesignW = 1280f

@Composable
fun AppScreen(ui: Ui, vm: MainViewModel?) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Wind, background = Bg, surface = Panel, onSurface = Ink)) {
        // 액티비티 창 자체가 앱 패널(2560x1440 화면에서 1682x1210)이다. 창 폭 비율(k)로 어떤 해상도에서도 같은 비례를 유지한다.
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val k = constraints.maxWidth / PanelW
            // 패널 안쪽은 시안(폭 1280) 기준 dp로 짜여 있어, 패널 폭에 맞춰 density를 키우면 dp·sp가 함께 비례 확대된다.
            val u = PanelW * k / DesignW
            val inner = Density(u, LocalDensity.current.fontScale)
            Box {
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
    // 어드민일 때만 제목 아래에 차량·음성 상태가 끼어들고, 그만큼 주행 현황까지 간격이 줄어든다
    val gap by animateDpAsState(if (ui.admin) 26.dp else 36.dp, tween(SlideMs), label = "statusGap")
    Box(m.fillMaxHeight().pane()) {
        Column(Modifier.padding(22.dp)) {
            Text("깨움바람", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            AnimatedVisibility(ui.admin, enter = fadeIn(tween(SlideMs)) + expandVertically(tween(SlideMs)), exit = fadeOut(tween(200)) + shrinkVertically(tween(SlideMs))) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(Corner).background(Line).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(if (ui.carConnected) Green else Dim, Corner))
                        Text(if (ui.carConnected) "Vehicle SDK 초기화됨" else "차량 미연결 (로그만)", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("음성: ${ui.voiceEngine}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(gap))
            SectionLabel("주행 현황")
            Spacer(Modifier.height(2.dp))
            StatRow("연속 주행", mmss(s.sinceStopSec))
            StatRow("단계", "", badge = s.level)
            Spacer(Modifier.height(26.dp))
            SectionLabel("오늘 리포트")
            Spacer(Modifier.height(2.dp))
            StatRow("환기", "${s.ventCount}회")
            StatRow("최장 연속 주행", mmss(s.longestDriveSec))
            StatRow("평균 반응시간", if (s.reactionMs.isEmpty()) "-" else "${s.avgReactionMs / 1000.0}초")
            StatRow("휴식 이행", "${s.restAccepted} / ${s.restSuggested}")
        }
        if (s.phase == Phase.DRIVING) VentButton(Modifier.align(Alignment.BottomCenter).padding(22.dp).fillMaxWidth()) { vm?.ventNow() }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)

@Composable
private fun StatRow(label: String, value: String, badge: Level? = null) {
    val line = Modifier.drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
    Row(Modifier.fillMaxWidth().height(48.dp).then(line), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (badge != null) {
            Box(Modifier.clip(Corner).background(badge.badge()).padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text(badge.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** 지금 환기: 패널 폭 테두리 버튼(높이 56). 바람 아이콘은 Figma 벡터(선 2.67, 둥근 끝) 그대로, 32 박스 안. */
@Composable
private fun VentButton(m: Modifier, onClick: () -> Unit) {
    val d = LocalDensity.current.density
    Row(
        m.height(56.dp).clip(Corner).border(2.dp, Muted, Corner).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(32.dp)) {
            withTransform({ scale(d, d, Offset.Zero); translate(3.8f, 2.7f) }) {
                val p = Path().apply {
                    moveTo(0f, 8f); lineTo(14.667f, 8f); arcTo(Rect(10.667f, 0f, 18.667f, 8f), 90f, -270f, false)
                    moveTo(0f, 13.333f); lineTo(21.333f, 13.333f); arcTo(Rect(17.333f, 13.333f, 25.333f, 21.333f), -90f, 270f, false)
                    moveTo(0f, 18.667f); lineTo(10.667f, 18.667f); arcTo(Rect(6.667f, 18.667f, 14.667f, 26.667f), -90f, 270f, false)
                }
                drawPath(p, Ink, style = Stroke(2.667f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text("지금 환기", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
            Phase.NAP -> SleepScene(Modifier.fillMaxSize())
            else -> {}
        }
        when (s.phase) {
            Phase.DRIVING -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(lv.color(), "주행 중")
                Spacer(Modifier.height(10.dp))
                Text("다음 환기까지", color = Ink2, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(mmss(maxOf(0, s.intervalSec - s.sinceAlertSec)), color = Ink, fontSize = 176.sp, lineHeight = 176.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                val filled = (s.sinceAlertSec * 10f / s.intervalSec).toInt().coerceIn(0, 10)
                Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(10) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it < filled) lv.color() else Line, Corner)) }
                }
            }
            Phase.VENT -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                // Engine.vent()와 같은 기준: L2 이상이거나 "세게 깨워줘"면 세게 환기
                val strong = lv != Level.L1 || s.strongMode
                Header(
                    lv.color(), if (strong) "세게 환기 중" else "환기 중",
                    if (strong) "송풍 최대 · 창문 더 열기 · 시트 통풍" else "외기 전환 · 창문 조금 열기 · 시트 통풍",
                )
                Spacer(Modifier.weight(1f))
                val left = (VentSec - s.phaseSec).coerceAtLeast(0)
                TimerBar("환기 끝까지", "${left}초", steps(left, VentSec), lv.color())
            }
            Phase.IDLE -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(IdleGray, "대기 중", "기어를 D에 넣으면 주행 시간을 재기 시작해요.")
            }
            Phase.QUESTION -> QuestionPhase(ui, vm)
            Phase.REST -> RestPhase(ui, vm)
            Phase.STRETCH -> StretchPhase(ui, vm)
            Phase.NAP -> NapPhase(ui, vm)
        }
        AnimatedVisibility(!ui.admin, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 10.dp), enter = fadeIn(tween(SlideMs)), exit = fadeOut(tween(150))) {
            AdminButton({ vm?.toggleAdmin() })
        }
    }
}

private val RedDeep = Color(0xFFB91C1C)
private val RedSoftTag = Color(0xFFFEE2E2)
private val WindSoft = Color(0xFFDBEAFE)

// Engine의 단계 길이(초)와 같게 맞춘다
private const val VentSec = 60
private const val StretchSec = 90
private const val NapSec = 20 * 60

/** 남은 시간을 15칸 막대의 채운 칸 수로. */
private fun steps(left: Int, total: Int) = (left * 15f / total).roundToInt().coerceIn(0, 15)

/** 카드 머리: 단계 색 막대, 제목(40), 있으면 한 줄 설명(20). */
@Composable
private fun Header(accent: Color, title: String, body: String? = null) {
    Box(Modifier.width(71.dp).height(6.dp).background(accent, Corner))
    Spacer(Modifier.height(10.dp))
    Text(title, color = Ink, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
    if (body != null) {
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ink2, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    }
}

/** 카드 아래 남은 시간: 왼쪽 라벨, 오른쪽 큰 숫자, 그 아래 15칸 막대. */
@Composable
private fun TimerBar(label: String, value: String, filled: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
        Text(value, color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(15) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it < filled) color else Line, Corner)) }
    }
}

/** 큰 초록 버튼 (높이 80, 그림자). 사용자 뷰에서도 폭은 어드민 카드 안쪽 폭(550)에 맞춘다. */
@Composable
private fun PrimaryButton(label: String, m: Modifier, onClick: () -> Unit) {
    Box(
        m.height(80.dp)
            .shadow(6.dp, Corner, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(GreenDeep, Corner).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
}

/** 질문 화면: 마이크 장면 위에 듣는 중 칩, 아래에 남은 시간 막대와 답 버튼. "응답 없음"은 어드민에서만. */
@Composable
private fun QuestionPhase(ui: Ui, vm: MainViewModel?) {
    val s = ui.s
    val left = (15 - s.phaseSec).coerceIn(0, 15)
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Header(s.level.color(), s.question)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(Corner).background(WindSoft).padding(start = 12.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(Wind, Corner))
            Text(if (ui.listening) "듣고 있어요…" else "대답해 주세요", color = Wind, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        TimerBar("대답할 수 있는 시간", "${left}초", left, s.level.color())
        Spacer(Modifier.height(26.dp))
        Row(Modifier.width(550.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PrimaryButton("괜찮아요", Modifier.weight(1f)) { vm?.answered() }
            if (ui.admin) Box(
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
        Header(RedDeep, "쉼터까지만 가요", "더는 깨우지 않아요. 진행 방향에서 가장 가까운 쉼터예요.")
        Spacer(Modifier.height(14.dp))
        ui.rest?.let { RestCard(it, ui.restKm, ui.restNavi) { vm?.restAccept() } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            Box(
                Modifier.height(38.dp).clip(Corner).border(2.dp, Muted, Corner).clickable { vm?.restDecline() }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("15분 뒤에", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            Box(
                Modifier.height(38.dp).clip(Corner).background(RedDeep).clickable { vm?.restAccept() }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("쉼터 경유", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 쉼터 1곳. 내비 값(navi = 남은 m, s)이 오면 주행거리·시간, 아직이면 직선거리에 "약". */
@Composable
private fun RestCard(stop: RestStop, km: Double, navi: Pair<Int, Int>?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).clip(Corner).background(Color.White).border(3.dp, RedDeep, Corner)
            .clickable(onClick = onClick).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stop.name, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.clip(Corner).background(RedSoftTag).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(if (navi != null) "내비 기준" else "직선 기준", color = RedDeep, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(stop.road, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            if (navi != null) "${"%.1f".format(navi.first / 1000.0)}km · ${maxOf(1, navi.second / 60)}분" else "약 ${"%.1f".format(km)}km",
            color = RedDeep, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** 스트레칭 4자세: 이름, 초, 한 줄 설명. 순서대로 진행한다. */
private val StretchSteps = listOf(
    Triple("목 좌우", 10, "고개를 좌우로 천천히"),
    Triple("어깨 돌리기", 20, "양 어깨를 크게 뒤로"),
    Triple("허리 비틀기", 20, "상체를 좌우로 비틀기"),
    Triple("손목·발목", 20, "손목과 발목 돌리기"),
)

/** 정차 스트레칭: 2x2 자세 카드(진행 중인 자세만 초록 테두리), 남은 시간, "다 했어요". */
@Composable
private fun StretchPhase(ui: Ui, vm: MainViewModel?) {
    val s = ui.s
    val left = (StretchSec - s.phaseSec).coerceAtLeast(0)
    // 자세별 시간을 차례로 더해 지금 몇 번째 자세인지. 다 지나면 마지막 자세에 머문다
    var end = 0
    val active = StretchSteps.indexOfFirst { end += it.second; s.phaseSec < end }.let { if (it < 0) StretchSteps.lastIndex else it }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Header(Green, "정차 스트레칭")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) { val i = row * 2 + col; StretchCard(i, i == active, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.weight(1f))
        TimerBar("남은 시간", "${left}초", steps(left, StretchSec), Green)
        Spacer(Modifier.height(26.dp))
        PrimaryButton("다 했어요", Modifier.width(550.dp)) { vm?.stretchDone() }
    }
}

/** 자세 카드. 넓으면(사용자 뷰) 그림 왼쪽·정보 오른쪽, 좁으면(어드민 뷰) 위에서 아래로 가운데 정렬. */
@Composable
private fun StretchCard(i: Int, active: Boolean, m: Modifier) {
    val (title, sec, desc) = StretchSteps[i]
    val header = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(27.dp).background(if (active) GreenDeep else Line, Corner), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = if (active) Color.White else Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
    val secColor = if (active) GreenDeep else Ink
    BoxWithConstraints(m.height(262.dp).clip(Corner).background(Color.White).border(if (active) 3.dp else 2.dp, if (active) Green else Dim, Corner)) {
        if (maxWidth > 360.dp) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, top = 17.dp, end = 15.dp, bottom = 17.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StretchFigure(i, Modifier.size(219.dp, 228.dp))
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    header()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${sec}초", color = secColor, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(desc, color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                header()
                StretchFigure(i, Modifier.size(151.dp, 157.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("${sec}초", color = secColor, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(desc, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

/** 충전 중 낮잠: 수면 안대 장면, 일어날 때까지 남은 시간, "지금 일어날래요". */
@Composable
private fun NapPhase(ui: Ui, vm: MainViewModel?) {
    val left = (NapSec - ui.s.phaseSec).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Header(Green, "충전 중 낮잠", "20분 뒤에 바람으로 깨워 드릴게요.")
        Spacer(Modifier.weight(1f))
        TimerBar("일어날 때까지", mmss(left), steps(left, NapSec), Green)
        Spacer(Modifier.height(26.dp))
        PrimaryButton("지금 일어날래요", Modifier.width(550.dp)) { vm?.wake() }
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
            Text("에뮬레이터 주입 대신 · 속도 ${ui.speedKmh.toInt()}km/h", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 차량 속도 신호가 없을 때(콘솔 미주입) 트럭 모션 속도를 손으로 바꾼다
                SignalRow("속도 −20" to { vm?.setSpeed(ui.speedKmh - 20) }, "속도 +20" to { vm?.setSpeed(ui.speedKmh + 20) })
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
private val previewStop = RestStop("안성 졸음쉼터", 37.0, 127.2, "경부고속도로")
private fun previewUi(phase: Phase, sec: Int = 0, q: String = "", level: Level = Level.L1, admin: Boolean = false) = Ui(
    s = State(phase = phase, phaseSec = sec, level = level, sinceStopSec = 95 * 60, sinceAlertSec = 27 * 60, question = q, ventCount = 3, longestDriveSec = 95 * 60, reactionMs = listOf(2100, 3400, 1200), restSuggested = 2, restAccepted = 1),
    carConnected = true, voiceEngine = "Pleos TTS/STT", listening = phase == Phase.QUESTION,
    rest = if (phase == Phase.REST) previewStop else null, restKm = 3.2, restNavi = if (phase == Phase.REST) 4300 to 5 * 60 else null,
    log = listOf("환기 3회째", "기어 D", "Vehicle SDK 초기화됨"),
    admin = admin,
)

@Preview(name = "주행 L1", widthDp = 1682, heightDp = 1210) @Composable private fun PvDriving() = AppScreen(previewUi(Phase.DRIVING), null)
@Preview(name = "주행 L1 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvDrivingAdmin() = AppScreen(previewUi(Phase.DRIVING, admin = true), null)
@Preview(name = "주행 L2", widthDp = 1682, heightDp = 1210) @Composable private fun PvDrivingL2() = AppScreen(previewUi(Phase.DRIVING, level = Level.L2), null)
@Preview(name = "주행 L2 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvDrivingL2Admin() = AppScreen(previewUi(Phase.DRIVING, level = Level.L2, admin = true), null)
@Preview(name = "환기 L1", widthDp = 1682, heightDp = 1210) @Composable private fun PvVent() = AppScreen(previewUi(Phase.VENT, 12), null)
@Preview(name = "환기 L1 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvVentAdmin() = AppScreen(previewUi(Phase.VENT, 12, admin = true), null)
@Preview(name = "환기 L2", widthDp = 1682, heightDp = 1210) @Composable private fun PvVentL2() = AppScreen(previewUi(Phase.VENT, 12, level = Level.L2), null)
@Preview(name = "환기 L2 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvVentL2Admin() = AppScreen(previewUi(Phase.VENT, 12, level = Level.L2, admin = true), null)
@Preview(name = "대기", widthDp = 1682, heightDp = 1210) @Composable private fun PvIdle() = AppScreen(previewUi(Phase.IDLE), null)
@Preview(name = "대기 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvIdleAdmin() = AppScreen(previewUi(Phase.IDLE, admin = true), null)
@Preview(name = "질문", widthDp = 1682, heightDp = 1210) @Composable private fun PvQuestion() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?"), null)
@Preview(name = "질문 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvQuestionAdmin() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?", admin = true), null)
@Preview(name = "질문 L2", widthDp = 1682, heightDp = 1210) @Composable private fun PvQuestionL2() = AppScreen(previewUi(Phase.QUESTION, 4, "오늘 몇 개 남았어요?", level = Level.L2), null)
@Preview(name = "스트레칭", widthDp = 1682, heightDp = 1210) @Composable private fun PvStretch() = AppScreen(previewUi(Phase.STRETCH, 18), null)
@Preview(name = "스트레칭 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvStretchAdmin() = AppScreen(previewUi(Phase.STRETCH, 18, admin = true), null)
@Preview(name = "휴식 제안", widthDp = 1682, heightDp = 1210) @Composable private fun PvRest() = AppScreen(previewUi(Phase.REST, level = Level.L3), null)
@Preview(name = "휴식 제안 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvRestAdmin() = AppScreen(previewUi(Phase.REST, level = Level.L3, admin = true), null)
@Preview(name = "쪽잠", widthDp = 1682, heightDp = 1210) @Composable private fun PvNap() = AppScreen(previewUi(Phase.NAP, 78), null)
@Preview(name = "쪽잠 어드민", widthDp = 1682, heightDp = 1210) @Composable private fun PvNapAdmin() = AppScreen(previewUi(Phase.NAP, 78, admin = true), null)
