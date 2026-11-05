package kr.kkaeumbaram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private var clock = 0L
    private fun engine(): Pair<Engine, MutableList<Effect>> {
        val fx = mutableListOf<Effect>()
        val e = Engine { clock }
        e.onEffect = { fx += it }
        e.startDriving()
        return e to fx
    }

    @Test fun `30분 주행 후 환기 그리고 질문, 대답하면 반응시간 기록`() {
        val (e, fx) = engine()
        e.tick(30 * 60)
        assertEquals(Phase.VENT, e.state.phase)
        assertTrue(fx.any { it is Effect.Vent })
        e.tick(60)
        assertEquals(Phase.QUESTION, e.state.phase)
        clock += 1800
        e.answered()
        assertEquals(Phase.DRIVING, e.state.phase)
        assertEquals(listOf(1800L), e.state.reactionMs)
        assertEquals(1, e.state.ventCount)
    }

    @Test fun `무응답 두 번이면 L2 거쳐 L3 쉼터`() {
        val (e, fx) = engine()
        e.tick(30 * 60); e.tick(60)
        e.noAnswer()
        assertEquals(Level.L2, e.state.level)
        assertEquals(Phase.VENT, e.state.phase)
        assertTrue(fx.any { it is Effect.VolumeUp })
        e.tick(60)                       // L2 질문
        e.tick(15)                       // 타임아웃 → 무응답
        assertEquals(Phase.REST, e.state.phase)
        assertEquals(Level.L3, e.state.level)
        e.restAccepted()
        assertTrue(fx.any { it is Effect.AddRestWaypoint })
        assertEquals(Phase.DRIVING, e.state.phase)
    }

    @Test fun `90분 무정차면 바로 쉼터, 정차하면 스트레칭, 충전이면 낮잠`() {
        val (e, _) = engine()
        e.tick(29 * 60); e.tick(60); e.tick(60); clock += 500; e.answered()   // 1차 환기 통과
        e.tick(29 * 60); e.tick(60); e.tick(60); clock += 500; e.answered()   // 2차
        e.tick(30 * 60)                                                       // 누적 90분+
        assertEquals(Phase.REST, e.state.phase)
        e.restDeclined()
        e.stopped()
        assertEquals(Phase.STRETCH, e.state.phase)
        assertEquals(0, e.state.sinceStopSec)
        e.chargingStarted()
        assertEquals(Phase.NAP, e.state.phase)
        e.tick(20 * 60)
        assertEquals(1, e.state.naps)
        assertEquals(Phase.IDLE, e.state.phase)
    }
}
