package kr.kkaeumbaram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMathTest {
    /** 주행 장면의 층마다 (반복 주기, 도로 대비 속도). 한 루프를 돌면 전부 제자리여야 이음매가 없다. */
    private val layers = listOf(108f to 1f, 150f to 1f, 180f to 1f, 270f to 1f, 450f to 1f, 1800f to 1f / 3f, 1800f to 2f / 3f)

    @Test
    fun `주행 루프가 끝나면 모든 층이 처음 자리로 돌아온다`() {
        for ((period, factor) in layers) {
            val rest = (DRIVE_LOOP * factor) % period
            assertTrue("주기 $period, 속도 $factor: 나머지 $rest", rest < 0.01f || period - rest < 0.01f)
        }
        val spoke = (DRIVE_LOOP * SPOKE_DEG_PER_PX) % 72f      // 바퀴살은 72도마다 같은 그림
        assertTrue("바퀴살 나머지 $spoke", spoke < 0.01f || 72f - spoke < 0.01f)
    }

    @Test
    fun `띠 복사본이 얼마나 밀려도 카드 폭을 빈틈없이 덮는다`() {
        var shift = 0f
        while (shift < 4000f) {
            val xs = tileOffsets(1800f, shift)
            assertTrue("shift $shift: 왼쪽이 빈다 ${xs.first()}", xs.first() <= 0f)
            assertTrue("shift $shift: 오른쪽이 빈다 ${xs.last() + 1800f}", xs.last() + 1800f >= CARD_W)
            for (i in 1 until xs.size) assertEquals(1800f, xs[i] - xs[i - 1], 0.01f)
            shift += 37f
        }
    }

    private fun Pose.numbers() = listOf(shoulderY, lean, headDeg, turn) + joints().flatMap { listOf(it.x, it.y) }
    private fun Pose.joints() = listOf(elbowL, wristL, elbowR, wristR, ankleL, ankleR)

    @Test
    fun `스트레칭 자세는 한 루프 뒤 첫 프레임으로 돌아온다`() {
        for (pose in 0..3) {
            val a = stretchPose(pose, 0f).numbers()
            val b = stretchPose(pose, 1f).numbers()
            for (i in a.indices) assertEquals("자세 $pose, 값 $i", a[i], b[i], 0.01f)
        }
    }

    @Test
    fun `스트레칭 관절은 그림 상자를 벗어나지 않는다`() {
        for (pose in 0..3) for (step in 0..40) {
            for (j in stretchPose(pose, step / 40f).joints()) {
                assertTrue("자세 $pose, t=${step / 40f}: (${j.x}, ${j.y})", j.x in 0f..FIGURE_W && j.y in 0f..FIGURE_H)
            }
        }
    }

    @Test
    fun `자세마다 움직이는 부위가 다르다`() {
        val still = stretchPose(0, 0f)
        assertEquals("목 좌우: 4분의 1 지점에서 머리가 왼쪽으로 15도", -15f, stretchPose(0, 0.25f).headDeg, 0.01f)
        assertTrue("어깨 돌리기: 어깨가 올라간다", stretchPose(1, 0.25f).shoulderY < still.shoulderY - 5f)
        assertEquals("허리 비틀기: 상체가 오른쪽으로 7", 7f, stretchPose(2, 0.25f).lean, 0.01f)
        assertTrue("손목·발목: 오른발을 든다", stretchPose(3, 0f).ankleR.y < still.ankleR.y - 8f)
    }
}
