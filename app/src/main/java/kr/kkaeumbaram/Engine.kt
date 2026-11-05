package kr.kkaeumbaram

/**
 * 깨움바람 상태 기계. Android 의존 없음 → JVM 단위 테스트 가능.
 * 시간은 외부에서 tick(초)으로 흘려 넣는다(시연 모드 빨리감기 때문).
 */
enum class Phase { IDLE, DRIVING, VENT, QUESTION, STRETCH, REST, NAP }
enum class Level { L1, L2, L3 }

sealed class Effect {
    data class Say(val text: String) : Effect()
    data class Vent(val strong: Boolean) : Effect()
    object VentOff : Effect()
    data class Ask(val text: String) : Effect()          // TTS 후 STT 듣기
    object VolumeUp : Effect()
    object VolumeNormal : Effect()
    data class Light(val on: Boolean) : Effect()
    object SuggestRest : Effect()                        // UI: 쉼터 3곳 보여주기
    object AddRestWaypoint : Effect()
}

data class State(
    val phase: Phase = Phase.IDLE,
    val level: Level = Level.L1,
    val strongMode: Boolean = false,
    val intervalSec: Int = 30 * 60,
    val sinceAlertSec: Int = 0,          // 마지막 환기 이후 주행 시간
    val sinceStopSec: Int = 0,           // 마지막 정차 이후 주행 시간
    val phaseSec: Int = 0,               // 현재 phase 경과
    val question: String = "",
    val noAnswerStreak: Int = 0,
    val restDeclinedSec: Int = -1,       // 거부 후 경과(-1 = 없음)
    // 리포트
    val ventCount: Int = 0,
    val longestDriveSec: Int = 0,
    val reactionMs: List<Long> = emptyList(),
    val naps: Int = 0,
    val restSuggested: Int = 0,
    val stretches: Int = 0,
) {
    val avgReactionMs: Long get() = if (reactionMs.isEmpty()) 0 else reactionMs.sum() / reactionMs.size
    val drivingTotalSec: Int get() = sinceStopSec
}

class Engine(private val now: () -> Long = System::currentTimeMillis) {
    var state = State(); private set
    private val listeners = mutableListOf<(State) -> Unit>()
    private var askedAt = 0L

    private val questions = listOf(
        "다음 배송지는 몇 번째예요?",
        "오늘 몇 개 남았어요?",
        "다음은 어느 동네로 가요?",
    )
    private var qi = 0

    // ponytail: 콜백 하나. 코루틴/Flow는 ViewModel이 감싼다.
    var onEffect: (Effect) -> Unit = {}
    fun observe(l: (State) -> Unit) { listeners += l; l(state) }
    private fun set(s: State) { state = s; listeners.forEach { it(s) } }

    val ventSec = 60
    val questionTimeoutSec = 15
    val stretchSec = 90
    val napSec = 20 * 60
    val forceRestSec = 90 * 60
    val reDeclineSec = 15 * 60

    fun startDriving() {
        if (state.phase == Phase.DRIVING) return
        set(state.copy(phase = Phase.DRIVING, phaseSec = 0, sinceAlertSec = 0, level = Level.L1, noAnswerStreak = 0))
    }

    fun setStrongMode(on: Boolean) {
        set(state.copy(strongMode = on, intervalSec = if (on) 20 * 60 else 30 * 60))
        onEffect(Effect.Say(if (on) "세게 깨우기 모드예요. 20분마다 확인할게요." else "기본 모드로 돌아왔어요."))
    }

    /** 1초 단위가 아니어도 됨. 시연 모드는 dt=60 같은 값으로 빨리감기. */
    fun tick(dt: Int) {
        val s = state
        val next = s.copy(phaseSec = s.phaseSec + dt)
        when (s.phase) {
            Phase.IDLE -> set(next)
            Phase.DRIVING -> {
                val driven = s.sinceStopSec + dt
                var n = next.copy(
                    sinceAlertSec = s.sinceAlertSec + dt,
                    sinceStopSec = driven,
                    longestDriveSec = maxOf(s.longestDriveSec, driven),
                    restDeclinedSec = if (s.restDeclinedSec >= 0) s.restDeclinedSec + dt else -1,
                )
                set(n)
                if (n.sinceStopSec >= forceRestSec && (n.restDeclinedSec < 0 || n.restDeclinedSec >= reDeclineSec)) {
                    escalateToRest(); return
                }
                if (n.sinceAlertSec >= n.intervalSec) vent()
            }
            Phase.VENT -> {
                set(next)
                if (next.phaseSec >= ventSec) ask()
            }
            Phase.QUESTION -> {
                set(next)
                if (next.phaseSec >= questionTimeoutSec) noAnswer()
            }
            Phase.STRETCH -> {
                set(next)
                if (next.phaseSec >= stretchSec) { onEffect(Effect.Say("수고했어요. 출발하면 다시 지켜볼게요.")); set(state.copy(phase = Phase.IDLE, phaseSec = 0)) }
            }
            Phase.NAP -> {
                set(next)
                if (next.phaseSec >= napSec) wakeFromNap()
            }
            Phase.REST -> set(next)
        }
    }

    fun ventNow() { if (state.phase == Phase.DRIVING) vent() }

    private fun vent() {
        val strong = state.level != Level.L1 || state.strongMode
        set(state.copy(phase = Phase.VENT, phaseSec = 0, sinceAlertSec = 0, ventCount = state.ventCount + 1))
        onEffect(Effect.Say(if (strong) "환기할게요. 창문 열고 바람 세게 틀어요." else "환기할 시간이에요."))
        onEffect(Effect.Vent(strong))
        onEffect(Effect.Light(true))
    }

    private fun ask() {
        onEffect(Effect.VentOff)
        val q = if (state.level == Level.L1) questions[qi++ % questions.size] else "삐 소리가 나면 '네'라고 말해 주세요."
        askedAt = now()
        set(state.copy(phase = Phase.QUESTION, phaseSec = 0, question = q))
        onEffect(Effect.Ask(q))
    }

    /** STT 결과 또는 화면 버튼. 내용은 보지 않고 '응답했다'만 본다(반응시간이 지표). */
    fun answered() {
        if (state.phase != Phase.QUESTION) return
        val rt = now() - askedAt
        set(state.copy(
            phase = Phase.DRIVING, phaseSec = 0, level = Level.L1, noAnswerStreak = 0,
            reactionMs = state.reactionMs + rt,
        ))
        onEffect(Effect.VolumeNormal)
        onEffect(Effect.Light(false))
        onEffect(Effect.Say("좋아요. ${state.intervalSec / 60}분 뒤에 또 볼게요."))
    }

    fun noAnswer() {
        if (state.phase != Phase.QUESTION) return
        val streak = state.noAnswerStreak + 1
        when (state.level) {
            Level.L1 -> {
                set(state.copy(level = Level.L2, noAnswerStreak = streak))
                onEffect(Effect.VolumeUp)
                onEffect(Effect.Say("대답이 없네요. 소리를 키울게요."))
                vent()
            }
            else -> {
                set(state.copy(noAnswerStreak = streak))
                escalateToRest()
            }
        }
    }

    private fun escalateToRest() {
        onEffect(Effect.VentOff)
        set(state.copy(phase = Phase.REST, phaseSec = 0, level = Level.L3, restSuggested = state.restSuggested + 1))
        onEffect(Effect.Say("더는 깨우지 않을게요. 가까운 쉼터까지만 가요."))
        onEffect(Effect.SuggestRest)
    }

    fun restAccepted() {
        if (state.phase != Phase.REST) return
        onEffect(Effect.AddRestWaypoint)
        onEffect(Effect.Say("쉼터를 경유지에 넣었어요. 도착하면 알려 드릴게요."))
        set(state.copy(phase = Phase.DRIVING, phaseSec = 0, level = Level.L1, noAnswerStreak = 0, sinceAlertSec = 0, restDeclinedSec = -1))
    }

    fun restDeclined() {
        if (state.phase != Phase.REST) return
        onEffect(Effect.Say("알겠어요. 15분 뒤에 다시 물어볼게요."))
        set(state.copy(phase = Phase.DRIVING, phaseSec = 0, level = Level.L2, noAnswerStreak = 0, sinceAlertSec = 0, restDeclinedSec = 0))
    }

    /** 기어 P 또는 배송지 도착. */
    fun stopped() {
        if (state.phase !in listOf(Phase.DRIVING, Phase.VENT, Phase.QUESTION, Phase.REST)) return
        onEffect(Effect.VentOff)
        onEffect(Effect.VolumeNormal)
        set(state.copy(phase = Phase.STRETCH, phaseSec = 0, sinceStopSec = 0, sinceAlertSec = 0, level = Level.L1, noAnswerStreak = 0, stretches = state.stretches + 1, restDeclinedSec = -1))
        onEffect(Effect.Say("잠깐 섰네요. 90초만 목이랑 어깨 풀어요."))
    }

    fun stretchDone() {
        if (state.phase == Phase.STRETCH) set(state.copy(phase = Phase.IDLE, phaseSec = 0))
    }

    /** 충전 포트 열림/충전 ON + 정차. */
    fun chargingStarted() {
        if (state.phase == Phase.NAP) return
        onEffect(Effect.VentOff)
        set(state.copy(phase = Phase.NAP, phaseSec = 0, sinceStopSec = 0, sinceAlertSec = 0, level = Level.L1))
        onEffect(Effect.Say("충전 시작했어요. 20분만 눈 붙일까요? 조명 낮출게요."))
        onEffect(Effect.Light(false))
    }

    fun wakeFromNap() {
        if (state.phase != Phase.NAP) return
        set(state.copy(phase = Phase.IDLE, phaseSec = 0, naps = state.naps + 1))
        onEffect(Effect.Light(true))
        onEffect(Effect.Vent(true))
        onEffect(Effect.Say("20분 지났어요. 일어나요. 바람 넣을게요."))
    }
}
