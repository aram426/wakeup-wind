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
        val (e, fx) = engine()
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
        fx.clear(); e.tick(59)
        assertTrue(Effect.VentOff !in fx)
        e.tick(1)                                                             // 기상 환기도 60초 뒤 원복
        assertEquals(listOf<Effect>(Effect.VentOff), fx)
        e.tick(60)
        assertEquals(1, fx.size)
    }

    @Test fun `리포트 집계 환기 평균반응시간 최장연속 휴식이행`() {
        val (e, _) = engine()
        e.tick(30 * 60); e.tick(60); clock += 1000; e.answered()   // 환기 1, 반응 1.0초
        e.tick(30 * 60); e.tick(60); clock += 3000; e.answered()   // 환기 2, 반응 3.0초
        assertEquals(2, e.state.ventCount)
        assertEquals(2000L, e.state.avgReactionMs)
        assertEquals(60 * 60, e.state.longestDriveSec)                 // 환기·질문 중엔 안 늘어남
        e.tick(30 * 60)                                             // 90분+ → 쉼터 제안
        assertEquals(Phase.REST, e.state.phase)
        e.restDeclined()
        assertEquals(1, e.state.restSuggested); assertEquals(0, e.state.restAccepted)
        e.tick(15 * 60)                                             // 15분 뒤 다시 제안
        assertEquals(Phase.REST, e.state.phase)
        e.restAccepted()
        assertEquals(2, e.state.restSuggested); assertEquals(1, e.state.restAccepted)
    }
}
