package kr.kkaeumbaram

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private fun mod(v: Float, p: Float) = ((v % p) + p) % p

/**
 * 주행 장면. 움직임은 전부 "주행 거리"(= 속도를 시간으로 적분한 값) 하나에서 나온다.
 * 그래서 속도에 정확히 비례하고, 0이면 멈춘다.
 */
@Composable
fun DrivingScene(speedKmh: Float, modifier: Modifier = Modifier) {
    val speed by rememberUpdatedState(speedKmh)
    var dist by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            // 체감 속도는 실제의 2배. 트럭이 실제로 내는 80km/h 안팎에서 속도감이 나게
            dist += (now - last) / 1_000_000_000f * speed * 2f
            last = now
        }
    }
    val dp = LocalDensity.current.density
    Canvas(modifier) {
        val u = DrivingArt.UNIT * dp
        val ox = (size.width - 606 * dp) / 2f
        val oy = 317.7f * dp   // Figma v3: fx/driving y 417.6px
        val moving = speed > 1f
        val pal = DrivingArt.pal

        // 구름은 아주 천천히, 언덕 띠는 고정
        val cloudShift = mod(dist * 0.35f * dp, size.width)
        drawPx(DrivingArt.hills, pal, u, ox, oy)
        drawPx(DrivingArt.clouds, pal, u, ox, oy, dx = -cloudShift)
        drawPx(DrivingArt.clouds, pal, u, ox, oy, dx = size.width - cloudShift)

        // 도로: 카드 폭 전체. 차선 점선이 속도에 비례해 왼쪽으로 흐른다(한 칸 단위로 끊어서 픽셀 느낌 유지)
        val rows = listOf(Color(0xFF5D6161), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFF5D6161))
        rows.forEachIndexed { i, c -> drawRect(c, Offset(0f, oy + (42 + i) * u), Size(size.width, u)) }
        val period = 7 * u
        val shift = floor(dist * 4f * dp / u) * u
        var x = mod(ox + 2 * u - shift, period) - period
        while (x < size.width) {
            drawRect(Color(0xFFF2F2F2), Offset(x, oy + 45 * u), Size(4 * u, u))
            x += period
        }

        // 속도선: 달릴 때만, 몇 칸씩 끊어서 뒤로 밀린다
        if (moving) {
            val step = floor(dist * 0.15f).toInt() % 3
            drawPx(DrivingArt.speedLines, pal, u, ox, oy, alpha = if (step == 1) 0.55f else 1f, dx = -step * u)
        }

        // 트럭: 달리면 살짝 출렁인다
        val bob = if (moving && floor(dist * 0.05f).toInt() % 2 == 1) -3 * dp else 0f
        drawPx(DrivingArt.truck, pal, u, ox, oy, dy = bob)

        // 먼지: 뒤로 날리며 사라진다. 시간차로 두 겹
        if (moving) for (k in 0..1) {
            val p = mod(dist * 0.02f + k * 0.5f, 1f)
            drawPx(DrivingArt.dust, pal, u, ox, oy, alpha = 1f - p, dx = -p * 60 * dp, dy = bob)
        }
    }
}

private class Gust(val row: Int, val len: Int, val phase: Float, val speed: Float, val light: Boolean)

private val gusts = listOf(
    Gust(18, 9, 0.00f, 1.0f, false), Gust(22, 6, 0.35f, 1.3f, true), Gust(26, 8, 0.70f, 0.9f, false),
    Gust(35, 7, 0.15f, 1.2f, true), Gust(41, 10, 0.55f, 1.0f, false), Gust(53, 8, 0.25f, 1.4f, true),
    Gust(57, 6, 0.80f, 0.8f, false), Gust(62, 9, 0.45f, 1.1f, true),
)

/**
 * 환기 장면(Figma fx/wind). 링이 바깥으로 번지고, 엠블럼이 바람에 한 칸씩 밀리고,
 * 가는 바람줄이 왼쪽에서 오른쪽으로 흐르고, 십자 반짝이가 깜빡인다.
 */
@Composable
fun VentScene(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "vent")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val dp = LocalDensity.current.density
    Canvas(modifier) {
        val u = WindArt.UNIT * dp
        val ox = (size.width - 606 * dp) / 2f
        val oy = 72.4f * dp    // Figma v3: fx/wind y 95.1px
        val pal = WindArt.pal
        val pivot = Offset(ox + 305 * dp, oy + 395 * dp)
        val twoPi = (2 * PI).toFloat()

        drawPx(WindArt.disc, pal, u, ox, oy, alpha = 0.85f + 0.15f * sin(twoPi * t))

        // 링 3겹: 바깥으로 번지며 숨 쉬듯 진해졌다 옅어진다
        listOf(WindArt.ring1, WindArt.ring2, WindArt.ring3).forEachIndexed { k, ring ->
            val p = mod(t + k / 3f, 1f)
            val s = 0.94f + 0.12f * p
            val a = 0.35f + 0.65f * sin(PI.toFloat() * p)
            withTransform({ scale(s, s, pivot) }) { drawPx(ring, pal, u, ox, oy, alpha = a) }
        }

        // 바람줄: 왼쪽 → 오른쪽
        for (g in gusts) {
            val f = mod(t * g.speed + g.phase, 1f)
            val gx = (f * 66 - 6).roundToInt()
            val a = sin(PI.toFloat() * f) * 0.8f
            drawRect(
                (if (g.light) Color(0xFFDBEAFE) else Color(0xFF93C5FD)).copy(alpha = a),
                Offset(ox + gx * u, oy + g.row * u), Size(g.len * u, u),
            )
        }

        // 엠블럼과 이어진 줄: 바람에 밀리듯 한 칸씩 흔들린다
        val sway = sin(twoPi * t).roundToInt() * u
        drawPx(WindArt.emblem, pal, u, ox, oy, dx = sway)
        drawPx(WindArt.gust, pal, u, ox, oy, dx = sway * 1.5f)

        // 십자 반짝이 4개: 서로 엇갈려 깜빡
        for (j in 0..3) {
            val q = mod(t + j * 0.25f, 1f)
            val a = 0.3f + 0.7f * (1f - abs(2f * q - 1f))
            drawPx(WindArt.sparkles.subList(j * 3, j * 3 + 3), pal, u, ox, oy, alpha = a)
        }
    }
}

/**
 * 질문 장면(Figma fx/listening). 마이크는 그대로 두고, 음파 세 겹이 안쪽부터 차례로 밝아졌다 꺼지며 바깥으로 번진다.
 * 배경 원은 숨 쉬듯 옅어지고, 십자 반짝이가 엇갈려 깜빡인다.
 */
@Composable
fun ListeningScene(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "listening")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val dp = LocalDensity.current.density
    Canvas(modifier) {
        val u = MicArt.UNIT * dp
        val ox = (size.width - 606 * dp) / 2f
        val oy = 100.4f * dp   // Figma v3: fx/listening y 131.9px
        val pal = MicArt.pal

        drawPx(MicArt.disc, pal, u, ox, oy, alpha = 0.45f * (0.85f + 0.15f * sin(2 * PI.toFloat() * t)))
        listOf(MicArt.wave1, MicArt.wave2, MicArt.wave3).forEachIndexed { k, wave ->
            val p = mod(t - k * 0.2f, 1f)
            drawPx(wave, pal, u, ox, oy, alpha = 0.35f + 0.65f * sin(PI.toFloat() * p))
        }
        drawPx(MicArt.mic, pal, u, ox, oy)
        for (j in 0..3) {
            val q = mod(t + j * 0.25f, 1f)
            drawPx(MicArt.sparkles.subList(j * 3, j * 3 + 3), pal, u, ox, oy, alpha = 0.3f + 0.7f * (1f - abs(2f * q - 1f)))
        }
    }
}

/**
 * 쉼터 제안 장면(Figma fx/rest stop). 카드가 어드민 뷰로 좁아지면(606dp) 장면도 1.08배, 넓으면(922dp) 1.3배로 따라 줄고 커진다.
 * 도로는 카드 폭 전체, 화살표는 옅은 것부터 차례로 켜지며 쉼터 표지판 쪽을 가리킨다.
 */
@Composable
fun RestScene(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "rest")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val dp = LocalDensity.current.density
    Canvas(modifier) {
        val k = ((size.width / dp - 606f) / (922f - 606f)).coerceIn(0f, 1f)
        val s = 1.08f + 0.22f * k
        val ox = (-28.8f + (35.5f + 28.8f) * k) * dp
        val oy = (266.1f + (156.5f - 266.1f) * k) * dp
        val u = RestArt.UNIT * s * dp
        val ru = RestArt.ROAD_UNIT * s * dp
        val pal = RestArt.pal

        // 도로: 위·아래 진한 줄 사이에 밝은 줄, 가운데 줄에 차선 점선(4칸 + 3칸 간격)
        val rows = listOf(Color(0xFF5D6161), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFFA7ADAD), Color(0xFF5D6161))
        rows.forEachIndexed { i, c -> drawRect(c, Offset(0f, oy + (42 + i) * ru), Size(size.width, ru)) }
        var gx = 2f - 7f * 4
        while (ox + gx * ru < size.width) {
            drawRect(Color(0xFFF2F2F2), Offset(ox + gx * ru, oy + 45 * ru), Size(4 * ru, ru))
            gx += 7f
        }

        drawPx(RestArt.speedLines, pal, u, ox, oy)
        drawPx(RestArt.truck, pal, u, ox, oy)
        drawPx(RestArt.sign, pal, u, ox, oy)
        // 화살표: 한 개씩 차례로 진해지며 흐른다
        for (j in 0..2) {
            val p = mod(t - j * 0.18f, 1f)
            drawPx(RestArt.chevrons.subList(j * 5, j * 5 + 5), pal, u, ox, oy, alpha = 0.4f + 0.6f * (1f - p))
        }
    }
}
