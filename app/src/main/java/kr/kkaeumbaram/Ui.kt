package kr.kkaeumbaram

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Figma "Light Colors v3" 토큰. 파랑은 브랜드와 주요 버튼, 초록·주황·빨강은 단계(L1·L2·L3) 표시에만 쓴다.
private val Bg = Color(0xFFE2E8F0)
private val Panel = Color(0xFFFFFFFF)
private val Inset = Color(0xFFF1F5F9)
private val Line = Color(0xFFE2E8F0)
private val Border = Color(0xFFCBD5E1)
private val IconGray = Color(0xFF64748B)
private val Ink = Color(0xFF0F172A)
private val Ink2 = Color(0xFF334155)
private val Muted = Color(0xFF475569)
private val Brand = Color(0xFF1D4ED8)
private val BrandDeep = Color(0xFF1E40AF)
private val BrandSoft = Color(0xFFDBEAFE)
private val L1 = Color(0xFF16A34A)
private val L1Deep = Color(0xFF15803D)
private val L1Soft = Color(0xFFF0FDF4)
private val L2 = Color(0xFFF59E0B)
private val L3 = Color(0xFFDC2626)
private val L3Deep = Color(0xFFB91C1C)
private val L3Soft = Color(0xFFFEE2E2)
private val Idle = Color(0xFF94A3B8)

private fun Level.color() = when (this) { Level.L1 -> L1; Level.L2 -> L2; Level.L3 -> L3 }

// 모서리 반경: 패널 20, 카드·버튼 16, 작은 버튼 12, 칩 8, 진행 칸 4
private val PaneShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(8.dp)
private val SegShape = RoundedCornerShape(4.dp)
private val DotShape = RoundedCornerShape(2.dp)

/**
 * 누르기. 화면은 터치와 키보드 단축키(MainActivity.onKeyUp)로만 조작하므로 포커스는 받지 않는다.
 * 받게 두면 키보드로 시연할 때 포커스 표시(회색 덮개)가 버튼이나 쉼터 카드에 남는다.
 */
private fun Modifier.tap(onClick: () -> Unit) = focusProperties { canFocus = false }.clickable(onClick = onClick)

private fun Modifier.pane() = this
    .shadow(8.dp, PaneShape, ambientColor = Ink.copy(alpha = 0.10f), spotColor = Ink.copy(alpha = 0.14f))
    .background(Panel, PaneShape)

/** 지평선이 있는 장면(주행·대기·쉼터)의 카드 바탕: 30% 아래부터 하늘색으로 물든다. */
private val SkyBrush = Brush.verticalGradient(0.30f to Color(0x00DCEAFE), 0.935f to Color(0xFFDCEAFE))

// 글자는 시스템 글꼴(Pleos는 Asta Sans). 그 글꼴은 굵기 600을 Bold 파일로, 700 이상을 ExtraBold 파일로 그린다.
private val Bold = FontWeight.SemiBold
// 줄 높이를 적은 스타일은 그 높이를 그대로 지킨다(글자는 가운데, 첫 줄·끝 줄도 잘라내지 않음). Figma의 줄 높이와 같은 동작이다.
private val Base = TextStyle(fontFeatureSettings = "tnum", lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

/** Figma 텍스트 스타일 v3. 줄 높이를 적지 않은 것은 글꼴 기본값을 쓴다. */
private object Type {
    val Countdown = Base.copy(fontSize = 176.sp, lineHeight = 176.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val Title = Base.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = Bold, lineBreak = LineBreak.Heading)
    val Body = Base.copy(fontSize = 20.sp, lineHeight = 28.sp)
    val LabelL = Base.copy(fontSize = 22.sp)
    val Label = Base.copy(fontSize = 20.sp, fontWeight = Bold)
    val ValueXL = Base.copy(fontSize = 48.sp, fontWeight = Bold)
    val ValueL = Base.copy(fontSize = 40.sp, fontWeight = Bold)
    val ValueM = Base.copy(fontSize = 36.sp, fontWeight = Bold)
    val ValueS = Base.copy(fontSize = 24.sp, fontWeight = Bold)
    val ButtonL = Base.copy(fontSize = 30.sp, fontWeight = Bold)
    val Brand = Base.copy(fontSize = 26.sp, fontWeight = Bold)
    val CardTitle = Base.copy(fontSize = 22.sp, fontWeight = Bold)
    val Section = Base.copy(fontSize = 18.sp, fontWeight = Bold)
    val RowLabel = Base.copy(fontSize = 18.sp)
    val Caption = Base.copy(fontSize = 16.sp)
    val Micro = Base.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val MicroBold = Micro.copy(fontWeight = Bold)
}

private const val SlideMs = 450

// 화면 규격과 앱 패널 절대 좌표 (px). 시안 프레임은 폭 1280 기준.
private const val PanelW = 1682f
private const val PanelH = 1210f
private const val DesignW = 1280f

@Composable
fun AppScreen(ui: Ui, vm: MainViewModel?) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Brand, background = Bg, surface = Panel, onSurface = Ink)) {
        // 액티비티 창 자체가 앱 패널(2560x1440 화면에서 1682x1210)이다. 창 폭 비율(k)로 어떤 해상도에서도 같은 비례를 유지한다.
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val k = constraints.maxWidth / PanelW
            // 패널 안쪽은 시안(폭 1280) 기준 dp로 짜여 있어, 패널 폭에 맞춰 density를 키우면 dp·sp가 함께 비례 확대된다.
            val u = PanelW * k / DesignW
            val inner = Density(u, LocalDensity.current.fontScale)
            Box {
                CompositionLocalProvider(LocalDensity provides inner, LocalTextStyle provides Base) {
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
private fun StatusPane(ui: Ui, vm: MainViewModel?, modifier: Modifier) {
    val s = ui.s
    Column(modifier.fillMaxHeight().pane().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindIcon(28.dp, Brand)
            Text("깨움바람", color = Ink, style = Type.Brand)
        }
        // 어드민일 때만 제목 아래에 차량·음성 상태가 끼어든다
        AnimatedVisibility(ui.admin, enter = fadeIn(tween(SlideMs)) + expandVertically(tween(SlideMs)), exit = fadeOut(tween(200)) + shrinkVertically(tween(SlideMs))) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.background(Inset, ChipShape).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(if (ui.carConnected) L1 else Idle, DotShape))
                    Text(if (ui.carConnected) "Vehicle SDK 초기화됨" else "차량 미연결 (로그만)", color = Ink, style = Type.Micro)
                }
                Text("음성: ${ui.voiceEngine}", color = Muted, style = Type.Micro)
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("주행 현황")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().background(Inset, CardShape).padding(16.dp)) {
            Text("연속 주행", color = Muted, style = Type.RowLabel)
            Spacer(Modifier.height(2.dp))
            Text(mmss(s.sinceStopSec), color = Ink, style = Type.ValueXL)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("단계", color = Muted, style = Type.RowLabel)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LevelMeter(s.level)
                    LevelBadge(s.level)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("오늘 리포트")
        Spacer(Modifier.height(4.dp))
        StatRow("환기", "${s.ventCount}회")
        StatRow("최장 연속 주행", mmss(s.longestDriveSec))
        StatRow("평균 반응시간", if (s.reactionMs.isEmpty()) "-" else "%.2f초".format(s.avgReactionMs / 1000.0))
        StatRow("휴식 이행", "${s.restAccepted} / ${s.restSuggested}")
        Spacer(Modifier.weight(1f))
        if (s.phase == Phase.DRIVING) VentButton(Modifier.fillMaxWidth()) { vm?.ventNow() }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, color = Ink, style = Type.Section)

/** 단계 미터: 세 칸 중 지금 단계까지 단계 색으로 켠다. */
@Composable
private fun LevelMeter(level: Level) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { Box(Modifier.size(22.dp, 8.dp).background(if (it <= level.ordinal) level.color() else Border, SegShape)) }
    }
}

/** 단계 배지. L1은 한 톤 진한 초록에 흰 글자, L2는 주황에 진한 글자(흰 글자는 대비가 모자란다), L3는 빨강에 흰 글자. */
@Composable
private fun LevelBadge(level: Level) {
    Box(Modifier.background(if (level == Level.L1) L1Deep else level.color(), ChipShape).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text(level.name, color = if (level == Level.L2) Ink else Color.White, style = Type.Section)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val line = Modifier.drawBehind { drawLine(Line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
    Row(Modifier.fillMaxWidth().height(52.dp).then(line), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, style = Type.RowLabel)
        Text(value, color = Ink, style = Type.ValueS)
    }
}

// 브랜드 바람 아이콘(32 상자 기준 좌표)
private val WindPath by lazy {
    Path().apply {
        moveTo(0f, 8f); lineTo(14.667f, 8f); arcTo(Rect(10.667f, 0f, 18.667f, 8f), 90f, -270f, false)
        moveTo(0f, 13.333f); lineTo(21.333f, 13.333f); arcTo(Rect(17.333f, 13.333f, 25.333f, 21.333f), -90f, 270f, false)
        moveTo(0f, 18.667f); lineTo(10.667f, 18.667f); arcTo(Rect(6.667f, 18.667f, 14.667f, 26.667f), -90f, 270f, false)
    }
}

/** 바람 아이콘. 크기와 상관없이 선 굵기는 2dp. */
@Composable
private fun WindIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 32f
        withTransform({ scale(k, k, Offset.Zero); translate(3.8f, 2.7f) }) {
            drawPath(WindPath, color, style = Stroke(2.dp.toPx() / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** 지금 환기: 옅은 파랑 바탕의 패널 폭 버튼(높이 64). */
@Composable
private fun VentButton(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(64.dp).clip(CardShape).background(BrandSoft).tap(onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        WindIcon(32.dp, BrandDeep)
        Text("지금 환기", color = BrandDeep, style = Type.CardTitle)
    }
}

/** 어드민 전환 버튼: 44x44 터치 영역 안의 사람 + 방패 아이콘(36 상자, 선 1.5). */
@Composable
private fun AdminButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDensity.current.density
    Canvas(modifier.size(44.dp).clip(ControlShape).tap(onClick)) {
        withTransform({ scale(d, d, Offset.Zero); translate(6f, 6f) }) {
            val st = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(IconGray, 4f, Offset(14f, 9f), style = st)
            val shoulders = Path().apply {
                moveTo(17f, 26f); lineTo(5f, 26f); lineTo(5f, 24f)
                cubicTo(5f, 19.6f, 9f, 17f, 14f, 17f); cubicTo(16.2f, 17f, 18.2f, 17.5f, 19.8f, 18.5f)
            }
            drawPath(shoulders, IconGray, style = st)
            val shield = Path().apply {
                moveTo(24f, 18f); lineTo(28f, 20f); lineTo(28f, 23.5f)
                cubicTo(28f, 26f, 26.3f, 27.8f, 24f, 29f); cubicTo(21.7f, 27.8f, 20f, 26f, 20f, 23.5f)
                lineTo(20f, 20f); close()
            }
            drawPath(shield, IconGray, style = st)
        }
    }
}

@Composable
private fun MainCard(ui: Ui, vm: MainViewModel?, modifier: Modifier) {
    val s = ui.s
    val lv = s.level
    // Engine.vent()와 같은 기준: L2 이상이거나 "세게 깨워줘"면 세게 환기
    val strong = lv != Level.L1 || s.strongMode
    val sky = s.phase == Phase.DRIVING || s.phase == Phase.IDLE || s.phase == Phase.REST
    Box(modifier.fillMaxHeight().pane().clip(PaneShape).then(if (sky) Modifier.background(SkyBrush) else Modifier)) {
        when (s.phase) {
            Phase.DRIVING -> DrivingScene(ui.speedKmh, Modifier.fillMaxSize())
            Phase.IDLE -> DrivingScene(0f, Modifier.fillMaxSize())
            Phase.VENT -> VentScene(strong, Modifier.fillMaxSize())
            Phase.QUESTION -> ListeningScene(ui.listening, Modifier.fillMaxSize())
            Phase.REST -> RestScene(Modifier.fillMaxSize())
            Phase.NAP -> SleepScene(Modifier.fillMaxSize())
            Phase.STRETCH -> {}
        }
        when (s.phase) {
            Phase.DRIVING -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(lv.color(), "주행 중")
                Spacer(Modifier.height(10.dp))
                Text("다음 환기까지", color = Muted, style = Type.LabelL)
                // Android는 한 줄 글자의 상자를 글꼴 기본 높이(약 211) 아래로 줄이지 않는다. 시안의 줄 높이 176에 맞추려고 상자만 176으로 잡고 글자는 가운데서 넘치게 둔다.
                Text(
                    mmss(maxOf(0, s.intervalSec - s.sinceAlertSec)), color = Ink, style = Type.Countdown,
                    modifier = Modifier.height(176.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                )
                Spacer(Modifier.height(14.dp))
                Steps(10, (s.sinceAlertSec * 10f / s.intervalSec).toInt().coerceIn(0, 10), lv.color())
            }
            Phase.VENT -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(
                    lv.color(), if (strong) "세게 환기 중" else "환기 중",
                    if (strong) "송풍 최대 · 창문 더 열기 · 시트 통풍" else "외기 전환 · 창문 조금 열기 · 시트 통풍",
                )
                Spacer(Modifier.weight(1f))
                val left = (VentSec - s.phaseSec).coerceAtLeast(0)
                TimerBar("환기 끝까지", "${left}초", steps(left, VentSec), lv.color())
            }
            Phase.IDLE -> Column(Modifier.fillMaxSize().padding(28.dp)) {
                Header(Idle, "대기 중", "기어를 D에 넣으면 주행 시간을 재기 시작해요.")
            }
            Phase.QUESTION -> QuestionPhase(ui, vm)
            Phase.REST -> RestPhase(ui, vm)
            Phase.STRETCH -> StretchPhase(ui, vm)
            Phase.NAP -> NapPhase(ui, vm)
        }
        AnimatedVisibility(!ui.admin, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp), enter = fadeIn(tween(SlideMs)), exit = fadeOut(tween(150))) {
            AdminButton({ vm?.toggleAdmin() })
        }
    }
}

// Engine의 단계 길이(초)와 같게 맞춘다
private const val VentSec = 60
private const val StretchSec = 90
private const val NapSec = 20 * 60

/** 남은 시간을 15칸 막대의 채운 칸 수로. */
private fun steps(left: Int, total: Int) = (left * 15f / total).roundToInt().coerceIn(0, 15)

/** 카드 머리: 단계 색 막대, 제목(40), 있으면 한 줄 설명(20). */
@Composable
private fun Header(accent: Color, title: String, body: String? = null) {
    Box(Modifier.size(56.dp, 8.dp).background(accent, SegShape))
    Spacer(Modifier.height(10.dp))
    Text(title, color = Ink, style = Type.Title)
    if (body != null) {
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ink2, style = Type.Body)
    }
}

/** 진행 막대: n칸 중 filled칸을 color로 채운다. */
@Composable
private fun Steps(n: Int, filled: Int, color: Color) {
    Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(n) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it < filled) color else Line, SegShape)) }
    }
}

/** 카드 아래 남은 시간: 왼쪽 라벨, 오른쪽 큰 숫자, 그 아래 15칸 막대. */
@Composable
private fun TimerBar(label: String, value: String, filled: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, color = Muted, style = Type.Label, modifier = Modifier.padding(bottom = 5.dp))
        Text(value, color = Ink, style = Type.ValueL)
    }
    Spacer(Modifier.height(8.dp))
    Steps(15, filled, color)
}

/** 주요 버튼: 브랜드 파랑, 큰 글자. */
@Composable
private fun PrimaryButton(label: String, modifier: Modifier, height: Dp = 80.dp, onClick: () -> Unit) {
    Box(modifier.height(height).clip(CardShape).background(Brand).tap(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, style = Type.ButtonL)
    }
}

/** 보조 버튼: 흰 바탕에 테두리. */
@Composable
private fun OutlineButton(label: String, modifier: Modifier, style: TextStyle, color: Color, onClick: () -> Unit) {
    Box(modifier.clip(CardShape).background(Panel).border(2.dp, Border, CardShape).tap(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = color, style = style)
    }
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
            Modifier.background(BrandSoft, ChipShape).padding(start = 12.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(Brand, DotShape))
            Text(if (ui.listening) "듣고 있어요…" else "대답해 주세요", color = Brand, style = Type.Label)
        }
        Spacer(Modifier.weight(1f))
        TimerBar("대답할 수 있는 시간", "${left}초", left, s.level.color())
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("괜찮아요", Modifier.weight(1f)) { vm?.answered() }
            if (ui.admin) OutlineButton("응답 없음(시연)", Modifier.size(180.dp, 80.dp), Type.RowLabel, Muted) { vm?.noAnswer() }
        }
    }
}

/** 쉼터 제안 화면: 가장 가까운 쉼터 카드와 "15분 뒤에"·"쉼터 경유" 버튼. 카드를 눌러도 그 쉼터를 경유한다. */
@Composable
private fun RestPhase(ui: Ui, vm: MainViewModel?) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Header(L3Deep, "쉼터까지만 가요", "더는 깨우지 않아요. 진행 방향에서 가장 가까운 쉼터예요.")
        Spacer(Modifier.height(14.dp))
        ui.rest?.let { RestCard(it, ui.restKm, ui.restNavi) { vm?.restAccept() } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("15분 뒤에", Modifier.weight(5f).height(72.dp), Type.ValueS, Ink) { vm?.restDecline() }
            PrimaryButton("쉼터 경유", Modifier.weight(8f), 72.dp) { vm?.restAccept() }
        }
    }
}

/** 쉼터 1곳. 내비 값(navi = 남은 m, s)이 오면 주행거리·시간, 아직이면 직선거리에 "약". */
@Composable
private fun RestCard(stop: RestStop, km: Double, navi: Pair<Int, Int>?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).clip(CardShape).background(Panel).border(2.dp, L3, CardShape)
            .tap(onClick).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stop.name, color = Ink, style = Type.ValueS, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.background(L3Soft, ChipShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(if (navi != null) "내비 기준" else "직선 기준", color = L3Deep, style = Type.RowLabel, maxLines = 1)
                }
            }
            Text(stop.road, color = Muted, style = Type.RowLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (navi != null) "${"%.1f".format(navi.first / 1000.0)}km · ${maxOf(1, navi.second / 60)}분" else "약 ${"%.1f".format(km)}km",
            color = L3Deep, style = Type.ValueM, maxLines = 1,
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

/** 정차 스트레칭: 2x2 자세 카드(진행 중인 자세만 초록), 남은 시간, "다 했어요". */
@Composable
private fun StretchPhase(ui: Ui, vm: MainViewModel?) {
    val s = ui.s
    val left = (StretchSec - s.phaseSec).coerceAtLeast(0)
    // 자세별 시간을 차례로 더해 지금 몇 번째 자세인지. 다 지나면 마지막 자세에 머문다
    var end = 0
    val active = StretchSteps.indexOfFirst { end += it.second; s.phaseSec < end }.let { if (it < 0) StretchSteps.lastIndex else it }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Header(L1, "정차 스트레칭")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..1) { val i = row * 2 + col; StretchCard(i, i == active, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.weight(1f))
        TimerBar("남은 시간", "${left}초", steps(left, StretchSec), L1)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("다 했어요", Modifier.fillMaxWidth()) { vm?.stretchDone() }
    }
}

/** 자세 카드. 넓으면(사용자 뷰) 그림 왼쪽·정보 오른쪽, 좁으면(어드민 뷰) 위에서 아래로 가운데 정렬. 진행 중인 자세만 그림이 움직인다. */
@Composable
private fun StretchCard(i: Int, active: Boolean, modifier: Modifier) {
    val (title, sec, desc) = StretchSteps[i]
    val header = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(27.dp).background(if (active) L1Deep else Inset, ChipShape), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = if (active) Color.White else Muted, style = Type.Section)
            }
            Text(title, color = Ink, style = Type.CardTitle)
        }
    }
    val secColor = if (active) L1Deep else Ink
    BoxWithConstraints(
        modifier.height(262.dp).clip(CardShape).background(if (active) L1Soft else Panel)
            .border(if (active) 2.dp else 1.dp, if (active) L1 else Border, CardShape),
    ) {
        if (maxWidth > 360.dp) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, top = 17.dp, end = 15.dp, bottom = 17.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StretchFigure(i, active, Modifier.size(219.dp, 228.dp))
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    header()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${sec}초", color = secColor, style = Type.ValueL)
                        Text(desc, color = Muted, style = Type.Caption)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                header()
                StretchFigure(i, active, Modifier.size(151.dp, 157.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("${sec}초", color = secColor, style = Base.copy(fontSize = 27.sp, fontWeight = Bold))
                    Text(desc, color = Muted, style = Base.copy(fontSize = 15.sp))
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
        Header(L1, "충전 중 낮잠", "20분 뒤에 바람으로 깨워 드릴게요.")
        Spacer(Modifier.weight(1f))
        TimerBar("일어날 때까지", mmss(left), steps(left, NapSec), L1)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("지금 일어날래요", Modifier.fillMaxWidth()) { vm?.wake() }
    }
}

@Composable
private fun DemoPane(ui: Ui, vm: MainViewModel?, modifier: Modifier) {
    Column(modifier.fillMaxHeight().pane().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 12.dp)) {
        SectionLabel("시연 · 디버그")
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(Inset, CardShape).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow("시연 모드 (1초=1분)", ui.demo) { vm?.toggleDemo() }
            ToggleRow("세게 깨워줘", ui.s.strongMode) { vm?.strongMode(!ui.s.strongMode) }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("차량 신호 흉내")
        Spacer(Modifier.height(2.dp))
        Text("에뮬레이터 주입 대신 · 속도 ${ui.speedKmh.toInt()}km/h", color = Muted, style = Type.Micro)
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
            Modifier.fillMaxWidth().weight(1f).clip(CardShape).background(Inset).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(ui.log) { i, line -> Text(line, color = if (i == 0) Ink else Ink2, style = if (i == 0) Type.MicroBold else Type.Micro) }
        }
        Spacer(Modifier.height(12.dp))
        // 어드민 뷰에서의 전환 버튼 위치: 패널 아래 가운데
        AdminButton({ vm?.toggleAdmin() }, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, style = Type.Caption)
        Box(Modifier.size(56.dp, 32.dp).clip(CircleShape).background(if (on) Brand else Border).tap(onClick).padding(4.dp)) {
            Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).size(24.dp).background(Color.White, CircleShape))
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
private fun Signal(label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(56.dp).clip(ControlShape).background(Inset).tap(onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, style = Type.Micro)
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
