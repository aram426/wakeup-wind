package kr.kkaeumbaram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {
    private val spoken = mutableListOf<String>()
    private val dones = ArrayDeque<() -> Unit>()
    private val q = SpeechQueue { t, d -> spoken += t; dones.addLast(d) }

    @Test fun `앞 문장이 끝나야 다음 문장, 질문 콜백은 큐가 빈 뒤`() {
        var sttOpened = false
        q.say("대답이 없네요. 소리를 키울게요.")
        q.say("환기할게요.")
        q.say("삐 소리가 나면 '네'라고 말해 주세요.") { sttOpened = true }
        q.say("한 문장 더")
        assertEquals(listOf("대답이 없네요. 소리를 키울게요."), spoken)
        dones.removeFirst()()                       // 1번 끝남(오류도 같은 경로)
        assertEquals(2, spoken.size)
        dones.removeFirst()(); dones.removeFirst()()
        assertEquals(4, spoken.size)
        assertTrue(!sttOpened)                      // 뒤 문장이 남아 있으면 아직 STT 안 연다
        dones.removeFirst()()
        assertTrue(sttOpened)
        q.say("다시")                               // 큐가 빈 뒤 새 문장은 바로 말한다
        assertEquals("다시", spoken.last())
    }
}
