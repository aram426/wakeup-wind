package kr.kkaeumbaram

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 장면은 전부 벡터로 그린다. 정적인 그림 조각은 VectorArt.kt의 path 데이터이고, 움직이는 부분만 여기서 계산한다.
// 좌표는 카드 px(SceneMath.kt). 움직임은 연속 보간이고 루프는 이음매 없이 닫힌다.

/** 벡터 도형 하나. d는 SVG path(절대 좌표). c1이 c와 다르면 y0~y1 세로 그라데이션, sw > 0이면 끝이 둥근 선. */
internal class Vec(val d: String, c: Long, c1: Long = c, y0: Float = 0f, y1: Float = 0f, sw: Float = 0f, val a: Float = 1f) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
    val brush: Brush = if (c1 == c) SolidColor(Color(c)) else Brush.verticalGradient(listOf(Color(c), Color(c1)), y0, y1)
    val style: DrawStyle = if (sw > 0f) Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill
}

private val Sky200 = Color(0xFFBFDBFE)
private val Sky300 = Color(0xFF93C5FD)
private val Blue400 = Color(0xFF60A5FA)
private val Blue500 = Color(0xFF3B82F6)
private val Blue700 = Color(0xFF1D4ED8)
private val Navy = Color(0xFF1E3A8A)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate900 = Color(0xFF0F172A)
private val LaneWhite = Color(0xFFF8FAFC)
private val Red600 = Color(0xFFDC2626)
private val Amber100 = Color(0xFFFEF3C7)
private val Amber300 = Color(0xFFFCD34D)
private val Amber400 = Color(0xFFFBBF24)
private val Amber500 = Color(0xFFF59E0B)
private val Skin = Color(0xFFF7CBA8)
private val SkinShade = Color(0xFFEDB48C)
private val ShirtTop = Color(0xFF4F8FF7)
private val ShirtBottom = Color(0xFF2563EB)

private const val PI_F = PI.toFloat()
private const val CX = CARD_W / 2f

private fun frac(x: Float) = x - floor(x)

/** 화면에 올라온 뒤 흐른 시간(초). 값을 Canvas 안에서만 읽어서 다시 그리기만 일어나게 한다. */
@Composable
private fun rememberSeconds(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) withFrameNanos { value = (it - start) / 1e9f }
}

/** 카드에 그림 좌표계를 깐다. 가로는 가운데를 맞추고, 세로는 위(기본) 또는 아래(bottom)를 맞춘다. */
private inline fun DrawScope.art(dp: Float, bottom: Boolean = false, block: DrawScope.() -> Unit) {
    val k = dp / ART_PER_DP
    withTransform({
        translate((size.width - CARD_W * k) / 2f, if (bottom) size.height - CARD_H * k else 0f)
        scale(k, k, Offset.Zero)
    }, block)
}

private fun DrawScope.draw(shapes: List<Vec>) {
    for (s in shapes) drawPath(s.path, s.brush, s.a, s.style)
}

/** 모서리가 둥근 막대. r을 주지 않으면 양 끝이 반원이다. */
private fun DrawScope.pill(c: Color, x: Float, y: Float, w: Float, h: Float, a: Float = 1f, r: Float = h / 2f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r), alpha = a)

private fun DrawScope.seg(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, a: Float = 1f) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round, alpha = a)

/** 끝이 둥근 원호 선. 각도는 3시 방향에서 시계 방향. */
private fun DrawScope.arcLine(c: Color, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, w: Float, a: Float = 1f, ry: Float = r) =
    drawArc(c, start, sweep, false, Offset(cx - r, cy - ry), Size(2 * r, 2 * ry), a, Stroke(w, cap = StrokeCap.Round))

private fun DrawScope.sparkle(c: Color, x: Float, y: Float, size: Float) =
    withTransform({ translate(x, y); scale(size, size, Offset.Zero) }) { drawPath(Art.sparkle[0].path, c) }

/** (x, y)에서 deg 방향을 가리키는 화살촉. */
private fun DrawScope.arrow(c: Color, x: Float, y: Float, deg: Float, size: Float) =
    withTransform({ translate(x, y); rotate(deg, Offset.Zero); scale(size, size, Offset.Zero) }) { drawPath(Art.arrow[0].path, c) }

// ---------- 주행·대기 ----------

/** 속도 1km/h마다 도로가 1초에 흐르는 거리. 60km/h에서 360px/s. */
private const val ROAD_PX_PER_KMH = 6f

/** 속도선: x, y, 길이, 진하기, 거리 0에서의 위상. */
private val SpeedLines = arrayOf(
    floatArrayOf(190f, 862f, 112f, 0.75f, 0.5f), floatArrayOf(236f, 902f, 78f, 0.55f, 0.3f),
    floatArrayOf(164f, 942f, 136f, 0.65f, 0.62f), floatArrayOf(254f, 978f, 58f, 0.45f, 0.41f),
)
private val WheelX = floatArrayOf(108f, 468f)

/**
 * 주행 장면. 움직임은 전부 "주행 거리"(= 속도를 시간으로 적분한 값) 하나에서 나온다.
 * 그래서 속도에 정확히 비례하고, 0이면 바퀴·배경·먼지가 함께 멈춘다. 대기 화면은 속도 0으로 같은 장면을 쓴다.
 */
@Composable
fun DrivingScene(speedKmh: Float, modifier: Modifier = Modifier) {
    val speed by rememberUpdatedState(speedKmh)
    val dist = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            dist.floatValue = (dist.floatValue + (now - last) / 1e9f * speed * ROAD_PX_PER_KMH) % DRIVE_LOOP
            last = now
        }
    }
    val dp = LocalDensity.current.density
    Canvas(modifier) { art(dp, bottom = true) { driving(dist.floatValue, speed > 1f) } }
}

private fun DrawScope.driving(d: Float, moving: Boolean) {
    draw(Art.far)
    for (x in tileOffsets(1800f, d / 3f)) translate(x, 0f) { draw(Art.skyline) }        // 스카이라인: 도로의 1/3 속도
    draw(Art.clouds)
    for (x in tileOffsets(1800f, d * 2f / 3f)) translate(x, 0f) { draw(Art.trees) }     // 가로수: 도로의 2/3 속도
    roadside(d, Art.rail, CARD_W + 20f)
    if (moving) {
        for (l in SpeedLines) {     // 270px마다 뒤로 90px 흐르며 나타났다 사라진다
            val q = frac(d / 270f + l[4])
            pill(Sky300, l[0] - 90f * (q - l[4]), l[1], l[2], 9f, l[3] * min(1f, sin(PI_F * q) / sin(PI_F * l[4])))
        }
        for (i in 0..2) {           // 먼지: 180px마다 한 덩이가 뒷바퀴에서 떨어져 작아지며 흩어진다
            val q = frac(d / 180f + i / 3f + 0.08f)
            drawCircle(Color.White, 11.6f - 7.5f * q, Offset(390f - 81f * q, 1052f - 9f * sin(1.5f * PI_F * q)), (0.9f - 0.675f * q) * min(1f, q / 0.08f) * min(1f, (1f - q) / 0.2f))
        }
    }
    truck(340f, 1066f, 1f, d * SPOKE_DEG_PER_PX, if (moving) 2f * sin(TAU * d / 450f) else 0f)
}

/** 길섶, 가드레일과 기둥, 도로와 차선. 가드레일은 railEnd에서 끝난다. */
private fun DrawScope.roadside(d: Float, rail: List<Vec>, railEnd: Float) {
    draw(Art.verge)
    val post = frac(d / 108f) * 108f
    for (i in 0..11) {
        val x = 26f + i * 108f - post
        if (x < min(CARD_W, railEnd - 20f)) pill(Slate400, x, 990f, 8f, 38f, r = 2f)
    }
    draw(rail)
    draw(Art.road)
    val dash = frac(d / 150f) * 150f
    for (i in 0..9) {
        val x = 20f + i * 150f - dash
        if (x < CARD_W) pill(LaneWhite, x, 1104f, 78f, 10f)
    }
}

/** 트럭. (x, ground) = 짐칸 왼쪽 끝이 땅에 닿는 점. 바퀴와 그림자는 땅에 붙고 차체만 bob만큼 출렁인다. */
private fun DrawScope.truck(x: Float, ground: Float, size: Float, spoke: Float, bob: Float) =
    withTransform({ translate(x, ground); scale(size, size, Offset.Zero) }) {
        draw(Art.truckGround)
        translate(0f, bob) { draw(Art.truckBody) }
        for (wx in WheelX) withTransform({ translate(wx, -38f); rotate(spoke, Offset.Zero) }) { draw(Art.wheel) }
    }

// ---------- 환기 ----------

/** 카드를 왼쪽에서 오른쪽으로 지나가는 바람줄. laps = 한 루프에 지나가는 횟수. */
private class Gust(val x: Float, val y: Float, val w: Float, val laps: Int, curled: Boolean) {
    /** 끝이 위로 말리는 줄. 원점 = 왼쪽 끝 */
    val curl: Path? by lazy {
        if (!curled) null else Path().apply { moveTo(0f, 0f); lineTo(w, 0f); arcTo(Rect(w - 13f, -26f, w + 13f, 0f), 90f, -270f, false) }
    }
}

private val Gusts = listOf(
    Gust(60f, 372f, 150f, 1, true), Gust(520f, 322f, 90f, 2, false), Gust(930f, 410f, 120f, 1, false), Gust(300f, 470f, 70f, 2, false),
    Gust(1040f, 560f, 96f, 1, true), Gust(120f, 640f, 110f, 2, false), Gust(760f, 760f, 140f, 1, true), Gust(380f, 830f, 84f, 2, false),
)
private val GustsStrong = Gusts + listOf(
    Gust(700f, 290f, 130f, 2, true), Gust(200f, 540f, 120f, 3, false), Gust(980f, 690f, 150f, 2, true),
    Gust(560f, 880f, 100f, 3, false), Gust(20f, 780f, 90f, 2, false), Gust(860f, 480f, 70f, 3, false),
)
private val GustStroke = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 알갱이: x, y, 위상. */
private val Motes = arrayOf(floatArrayOf(240f, 520f, 0f), floatArrayOf(700f, 300f, 0.3f), floatArrayOf(980f, 820f, 0.6f), floatArrayOf(430f, 900f, 0.8f))

/** 바람 글리프의 세 줄: y, 곧은 부분의 길이. */
private val GlyphLines = arrayOf(floatArrayOf(80f, 146.7f), floatArrayOf(133.3f, 213.3f), floatArrayOf(186.7f, 106.7f))

/**
 * 환기 장면. 물결 고리가 안에서 밖으로 퍼지고, 바람줄이 카드를 가로지르고, 글리프 위로 빛이 흐른다.
 * 세게 환기는 고리와 바람줄이 더 많고 진하며 루프가 3초에서 2초로 빨라진다.
 */
@Composable
fun VentScene(strong: Boolean, modifier: Modifier = Modifier) {
    val sec = rememberSeconds()
    val dp = LocalDensity.current.density
    Canvas(modifier) { art(dp) { vent(frac(sec.value / if (strong) 2f else 3f), strong) } }
}

private fun DrawScope.vent(t: Float, strong: Boolean) {
    val cy = 598f
    val lap = CARD_W + 300f
    val tone = if (strong) Sky300 else Sky200
    for (g in if (strong) GustsStrong else Gusts) {
        val x = (g.x + t * g.laps * lap) % lap - 150f
        val a = if (g.laps == 1) 0.85f else 0.55f
        val curl = g.curl
        if (curl != null) translate(x, g.y) { drawPath(curl, tone, a, GustStroke) } else pill(tone, x, g.y - 4.5f, g.w, 9f, a)
    }
    val rings = if (strong) 4 else 3
    for (i in 0 until rings) {      // 안에서 밖으로 퍼지며 옅어지고, 한 겹씩 엇갈려 돈다
        val p = frac(t + i.toFloat() / rings)
        val r = 196f + 170f * p
        val a = (if (strong) 0.95f else 0.8f) * (1f - p).pow(1.3f) * min(1f, p / 0.1f)
        val stroke = Stroke(9f - 4f * p, cap = StrokeCap.Round)
        for (j in 0..7) drawArc(tone, j * 45f + (if (i % 2 == 1) 40f else -40f) * p + i * 13f, 29f, false, Offset(CX - r, cy - r), Size(2 * r, 2 * r), a, stroke)
    }
    draw(Art.windDisc)
    for (m in Motes) drawCircle(Blue400, 5f, Offset((m[0] + t * lap) % lap - 150f, m[1] + 12f * sin(TAU * (2f * t + m[2]))), 0.75f)
    // 글리프: 좌우로 살짝 흔들리고, 줄 위로 빛이 흐른다
    val gx = CX - 126.7f + (if (strong) 10f else 6f) * sin(TAU * t)
    val gy = cy - 133.3f
    translate(gx, gy + 10f) { draw(Art.windShadow) }
    translate(gx, gy) {
        draw(Art.windGlyph)
        GlyphLines.forEachIndexed { k, l ->
            val u = frac((if (strong) 2f else 1f) * t + k / 3f)
            val x = 4f + (l[1] - 52f) * u
            seg(Color.White, x, l[0] - 6f, x + 44f, l[0] - 6f, 5f, 0.6f * sin(PI_F * u))
        }
    }
}

// ---------- 질문(듣기) ----------

private val WaveRadius = floatArrayOf(150f, 208f, 266f)
private val WaveStroke = Stroke(16f, cap = StrokeCap.Round)
private val MicLevels = intArrayOf(1, 2, 3, 2, 1, 2, 3, 3, 2, 1, 0, 1)

/**
 * 질문 장면. 듣는 중(listening)에만 음파 밝기가 안에서 밖으로 번지고 마이크 그릴 세 줄이 아래부터 레벨처럼 차오른다.
 * 아직 질문을 읽는 중이면 음파는 옅게 멈춰 있다.
 */
@Composable
fun ListeningScene(listening: Boolean, modifier: Modifier = Modifier) {
    val sec = rememberSeconds()
    val dp = LocalDensity.current.density
    Canvas(modifier) { art(dp) { listening(frac(sec.value / 2f), listening) } }
}

private fun DrawScope.listening(t: Float, on: Boolean) {
    val cy = 512f
    for (i in 0..2) {
        val r = WaveRadius[i]
        val a = if (on) 0.3f + 0.7f * wave(2f * t - i * 0.25f + 0.5f) else 0.28f
        for (side in 0..1) drawArc(if (on) Blue500 else Sky300, side * 180f - 30f, 60f, false, Offset(CX - r, cy - 5f - r), Size(2 * r, 2 * r), a, WaveStroke)
    }
    draw(Art.micDisc)
    draw(Art.micStand)
    draw(Art.micBody)
    val level = if (on) MicLevels[(t * 12).toInt() % 12] else 0
    for (j in 0..2) seg(Color.White, CX - 22f, cy - 63f - j * 28f, CX + 22f, cy - 63f - j * 28f, 8f, if (level > j) 0.95f else 0.35f)
    val s = 0.75f + 0.25f * wave(t)
    sparkle(Blue500, CX - 214f, cy - 150f, 22f * s)
    sparkle(Sky300, CX + 222f, cy + 108f, 13f * (1.75f - s))
}

// ---------- 쉼터 제안 ----------

private val Chevron by lazy { Path().apply { moveTo(0f, -30f); lineTo(26f, 0f); lineTo(0f, 30f) } }
private val ChevronStroke = Stroke(15f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 쉼터 제안 장면. 화살표 세 개가 왼쪽부터 차례로 진해지고, 닿을 때 P 표지판이 살짝 커진다. */
@Composable
fun RestScene(modifier: Modifier = Modifier) {
    val sec = rememberSeconds()
    val dp = LocalDensity.current.density
    Canvas(modifier) { art(dp, bottom = true) { rest(frac(sec.value / 1.6f)) } }
}

private fun DrawScope.rest(t: Float) {
    draw(Art.restBack)
    roadside(0f, Art.railRest, 838f)     // 가드레일이 표지판 앞에서 끊겨 쉼터 입구가 된다
    draw(Art.signPost)
    val pop = if (t >= 0.62f && t < 0.87f) 1f + 0.06f * max(0f, sin(TAU * (t - 0.62f) * 2f)) else 1f
    scale(pop, Offset(905f, 870f)) { draw(Art.sign) }
    for (i in 0..2) translate(668f + i * 50f, 955f) {
        drawPath(Chevron, Red600, 0.22f + 0.78f * max(0f, cos(TAU * (t - i * 0.2f))).pow(2), ChevronStroke)
    }
    truck(222f, 1066f, 0.66f, 0f, 0f)
}

// ---------- 쪽잠 ----------

private val ZShape by lazy { Path().apply { moveTo(0f, 0f); lineTo(1f, 0f); lineTo(0f, 1f); lineTo(1f, 1f) } }
private val ZStroke = Stroke(0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val ZColors = arrayOf(Sky300, Blue400, Blue500)

/** 쪽잠 장면. 끈이 먼저, 안대가 조금 늦게 숨 쉬듯 오르내리고, Z가 작게 태어나 대각선으로 커지며 떠오른다. */
@Composable
fun SleepScene(modifier: Modifier = Modifier) {
    val sec = rememberSeconds()
    val dp = LocalDensity.current.density
    Canvas(modifier) { art(dp) { sleep(frac(sec.value / 4f)) } }
}

private fun DrawScope.sleep(t: Float) {
    val cy = 545f
    val breath = wave(t)        // 0(내쉼) ~ 1(들이쉼)
    draw(Art.sleepDisc)
    val moon = Offset(CX - 204f, cy - 178f)
    drawCircle(Amber100, 70f + 8f * breath, moon, 0.6f)
    translate(moon.x, moon.y) { draw(Art.moon) }
    sparkle(Amber300, CX - 108f, cy - 236f, 9f * (0.6f + 0.4f * wave(t + 0.3f)))
    sparkle(Amber400, CX - 282f, cy - 84f, 7f * (0.6f + 0.4f * wave(t + 0.7f)))
    translate(CX, cy + 8f - 5f * wave(t + 0.06f)) { draw(Art.strapL); draw(Art.strapR) }
    val grow = 1f + 0.025f * breath
    withTransform({ translate(CX, cy + 8f - 5f * breath); scale(grow, grow, Offset.Zero) }) { draw(Art.mask) }
    for (i in 0..2) {
        val p = frac(t + i / 3f)
        val size = 20f + 36f * p
        withTransform({ translate(CX + 136f + 196f * p, cy - 84f - 196f * p); scale(size, size, Offset.Zero) }) {
            drawPath(ZShape, ZColors[min(2, (p * 3).toInt())], min(1f, p / 0.12f) * min(1f, (1f - p) / 0.22f), ZStroke)
        }
    }
    sparkle(Blue500, CX + 236f, cy + 116f, 20f * (0.7f + 0.3f * wave(t + 0.5f)))
    sparkle(Sky300, CX - 252f, cy + 74f, 11f * (0.7f + 0.3f * wave(t)))
}

// ---------- 스트레칭 ----------

/** 스트레칭 한 자세. 진행 중(active)인 자세만 6초 루프로 움직이고, 나머지는 자세가 잘 보이는 한 장면에 멈춰 있다. */
@Composable
fun StretchFigure(pose: Int, active: Boolean, modifier: Modifier = Modifier) {
    val sec = if (active) rememberSeconds() else null
    Canvas(modifier) {
        val u = size.width / FIGURE_W
        withTransform({ translate(0f, (size.height - FIGURE_H * u) / 2f); scale(u, u, Offset.Zero) }) {
            figure(pose, stretchPose(pose, if (sec != null) frac(sec.value / 6f) else 0.25f))
        }
    }
}

/** 사람은 관절 좌표(Pose)로 그린다. 표시(화살표·고리)는 자세마다 몸 뒤와 앞에 나눠 놓는다. */
private fun DrawScope.figure(pose: Int, p: Pose) = translate(0f, 8f) {
    val sy = p.shoulderY
    val cx = 110f + p.lean
    drawOval(Slate400, Offset(64f, 206.5f), Size(92f, 9f), 0.3f)
    seg(Slate300, 32f, 213f, 188f, 213f, 4f)
    when (pose) {
        0 -> {
            arcLine(Blue500, 110f, 40f, 44f, -146f, 112f, 4f)
            arrowOnRing(Blue500, 110f, 40f, 44f, -146f, -236f, 8.5f)
            arrowOnRing(Blue500, 110f, 40f, 44f, -34f, 56f, 8.5f)
        }
        1 -> {
            ringArrow(Blue500, 54f, 70f, 12f, 60f + 360f * p.turn, 3.6f, cw = false)
            ringArrow(Blue500, 166f, 70f, 12f, 120f - 360f * p.turn, 3.6f, cw = true)
        }
        2 -> arcLine(Amber500, 110f, 119f, 48f, 180f, 180f, 5f, 0.5f, ry = 11f)
    }
    leg(99f, p.ankleL, -17f)
    leg(121f, p.ankleR, -11f)
    pill(Navy, 88f, 122f, 44f, 20f, r = 9f)
    pill(SkinShade, cx - 6.5f, sy - 16f, 13f, 20f, r = 4f)
    val torso = Path().apply {
        moveTo(cx - 21f, sy); quadraticTo(cx - 29f, sy, cx - 29f, sy + 9f)
        lineTo(86f, 125f); quadraticTo(87f, 135f, 96f, 135f)
        lineTo(124f, 135f); quadraticTo(133f, 135f, 134f, 125f)
        lineTo(cx + 29f, sy + 9f); quadraticTo(cx + 29f, sy, cx + 21f, sy); close()
    }
    drawPath(torso, Brush.verticalGradient(listOf(ShirtTop, ShirtBottom), sy, 135f))
    drawPath(Path().apply { moveTo(cx - 8f, sy); lineTo(cx, sy + 10f); lineTo(cx + 8f, sy); close() }, SkinShade)
    arm(Offset(cx - 24f, sy + 9f), p.elbowL, p.wristL)
    arm(Offset(cx + 24f, sy + 9f), p.elbowR, p.wristR)
    // 머리는 목 밑동을 중심으로 기운다
    withTransform({ translate(p.lean, sy - 70f); rotate(p.headDeg, Offset(110f, 60f)); scale(1.16f, 1.16f, Offset(110f, 60f)) }) { draw(Art.face) }
    when (pose) {
        2 -> {
            arcLine(Amber500, 110f, 119f, 48f, 0f, 180f, 5f, ry = 11f)
            val a = ((20f + 360f * p.turn) % 180f) * PI_F / 180f
            arrow(Amber500, 110f + 48f * cos(a), 119f + 11f * sin(a), 180f, 10f)
        }
        3 -> {
            ringArrow(Blue400, 47f, 120f, 13f, 200f + 360f * p.turn, 3.4f, cw = false)
            ringArrow(Blue400, 173f, 120f, 13f, -20f - 360f * p.turn, 3.4f, cw = true)
            ringArrow(Blue400, p.ankleR.x + 7f, p.ankleR.y + 4f, 17f, 150f + 360f * p.turn, 3.4f, cw = true)
        }
    }
}

private fun DrawScope.leg(hipX: Float, ankle: Offset, shoeX: Float) {
    drawLine(Navy, Offset(hipX, 132f), ankle, 18f, StrokeCap.Round)
    val lean = atan2(ankle.x - hipX, ankle.y - 132f) * 180f / PI_F
    withTransform({ translate(ankle.x, ankle.y); rotate(-lean, Offset.Zero) }) { pill(Slate900, shoeX, -1f, 28f, 13f) }
}

/** 팔과 손. 어두운 밑선을 먼저 깔아 몸통과 구분한다. */
private fun DrawScope.arm(shoulder: Offset, elbow: Offset, wrist: Offset) {
    drawLine(Blue700, shoulder, elbow, 16f, StrokeCap.Round)
    drawLine(Blue700, elbow, wrist, 16f, StrokeCap.Round)
    drawLine(Blue500, shoulder, elbow, 12.5f, StrokeCap.Round)
    drawLine(Blue500, elbow, wrist, 12.5f, StrokeCap.Round)
    val d = wrist - elbow
    drawCircle(Skin, 7f, wrist + d / d.getDistance() * 6f)
}

/** 한 바퀴 조금 못 도는 화살표 고리: start에서 cw 방향으로 300도 돌고 끝에 화살촉. */
private fun DrawScope.ringArrow(c: Color, cx: Float, cy: Float, r: Float, start: Float, w: Float, cw: Boolean) {
    val sweep = if (cw) 300f else -300f
    arcLine(c, cx, cy, r, start, sweep, w)
    arrowOnRing(c, cx, cy, r, start + sweep, start + sweep + (if (cw) 90f else -90f), w * 2.1f)
}

/** 반지름 r인 원 위 at도 지점에 dir 방향 화살촉. */
private fun DrawScope.arrowOnRing(c: Color, cx: Float, cy: Float, r: Float, at: Float, dir: Float, size: Float) {
    val a = at * PI_F / 180f
    arrow(c, cx + r * cos(a), cy + r * sin(a), dir, size)
}
