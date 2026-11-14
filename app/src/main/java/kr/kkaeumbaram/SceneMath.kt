package kr.kkaeumbaram

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// 장면 그림의 좌표계는 "카드 px": 사용자 카드가 1211.57 x 1154.81이 되는 단위(dp x 1.3140625).
// 어드민 카드는 폭이 좁아 가운데 796.32만 보인다.
internal const val ART_PER_DP = 1.3140625f
internal const val CARD_W = 1211.57f
internal const val CARD_H = 1154.81f

/**
 * 주행 장면이 한 바퀴 도는 거리. 길가 기둥(108), 차선(150), 먼지(180), 속도선(270), 차체 출렁임(450),
 * 스카이라인·가로수 띠(1800)가 전부 이 거리에서 처음 자리로 돌아오므로, 여기서 거리를 0으로 되감아도 이음매가 없다.
 */
internal const val DRIVE_LOOP = 5400f

/** 바퀴살 회전(도/px). 실제 구름각 180 / (pi x 38) = 1.508과 0.1% 차이로 루프에 맞췄다. */
internal const val SPOKE_DEG_PER_PX = 113f * 72f / DRIVE_LOOP

internal const val TAU = (2 * PI).toFloat()

/** 0 → 1 → 0으로 부드럽게 오가는 값. x는 위상. */
internal fun wave(x: Float) = 0.5f - 0.5f * cos(TAU * x)

/** period마다 반복되는 띠를 shift만큼 왼쪽으로 밀었을 때 카드에 걸치는 복사본들의 x. 띠 밖으로 80까지 삐져나온 그림도 덮는다. */
internal fun tileOffsets(period: Float, shift: Float): List<Float> {
    val out = ArrayList<Float>(3)
    var x = -(((shift % period) + period) % period)
    while (x < CARD_W + 80f) {
        if (x + period > -80f) out.add(x)
        x += period
    }
    return out
}

internal const val FIGURE_W = 220f
internal const val FIGURE_H = 230f

/** 스트레칭 자세의 관절. 좌표는 220 x 230 그림 상자 기준. */
internal data class Pose(
    val shoulderY: Float,   // 어깨 높이(기본 70)
    val lean: Float,        // 상체가 옆으로 밀린 거리
    val headDeg: Float,     // 머리 기울기(도)
    val elbowL: Offset, val wristL: Offset, val elbowR: Offset, val wristR: Offset,
    val ankleL: Offset, val ankleR: Offset,
    val turn: Float,        // 고리 화살표가 돈 정도(0~1)
)

/** 자세 0 목 좌우, 1 어깨 돌리기, 2 허리 비틀기, 3 손목·발목. t는 그 자세의 루프 위상. */
internal fun stretchPose(pose: Int, t: Float): Pose {
    val turn = t - floor(t)
    val ankleL = Offset(99f, 198f)
    val ankleR = Offset(121f, 198f)
    return when (pose) {
        0 -> Pose(70f, 0f, -15f * sin(TAU * t), Offset(79f, 104f), Offset(77f, 127f), Offset(141f, 104f), Offset(143f, 127f), ankleL, ankleR, turn)
        1 -> {
            val up = wave(2 * t)
            Pose(70f - 6f * up, 0f, 0f, Offset(75f, 100f - 5f * up), Offset(76f, 124f - 5f * up), Offset(145f, 100f - 5f * up), Offset(144f, 124f - 5f * up), ankleL, ankleR, turn)
        }
        2 -> {
            val d = 7f * sin(TAU * t)
            Pose(70f, d, 0f, Offset(64f + d, 99f), Offset(97f + d * 1.6f, 97f), Offset(156f + d, 99f), Offset(123f + d * 1.6f, 97f), ankleL, ankleR, turn)
        }
        else -> {
            val k = sin(TAU * t)
            Pose(70f, 0f, 0f, Offset(67f, 99f), Offset(51f, 115f), Offset(153f, 99f), Offset(169f, 115f), ankleL, Offset(149f + 2f * k, 186f - 2f * k), turn)
        }
    }
}
