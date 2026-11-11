package kr.kkaeumbaram

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Ui(
    val s: State = State(),
    val carConnected: Boolean = false,
    val voiceEngine: String = "준비 중",
    val demo: Boolean = true,            // 시연 모드: 60배속 + 신호 버튼
    val listening: Boolean = false,
    val restOptions: List<Pair<RestStop, Double>> = emptyList(),
    val log: List<String> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    private val engine = Engine()
    private val logLines = mutableListOf<String>()
    private fun log(s: String) { logLines.add(0, s); if (logLines.size > 40) logLines.removeAt(logLines.size - 1); _ui.update { it.copy(log = logLines.toList()) } }

    private val car: Car = runCatching {
        PleosCar(app, onGearPark = engine::stopped, onGearDrive = engine::startDriving, onChargeStart = engine::chargingStarted)
    }.getOrElse { log("Vehicle SDK 로드 실패: ${it.message}"); NoCar() }
    private var voice: Voice? = null
    private val focus = AudioFocus(app, ::log)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var preroll = false
    // 말 묶음 시작에 포커스를 잡고 150ms 쉰 뒤 첫 문장(Pleos 선행 묵음 가이드: 첫 음절 잘림 방지), 큐가 비면 포커스 반납.
    // ponytail: TTS SDK가 재생을 쥐고 있어 PCM에 묵음을 못 붙인다 → 지연으로 대신. PCM을 직접 재생하게 되면 패딩으로.
    private val speech = SpeechQueue(onBusy = { busy -> if (busy) { focus.gain(); preroll = true } else focus.abandon() }) { text, done ->
        val v = voice
        if (v == null) done()
        else if (preroll) { preroll = false; main.postDelayed({ v.say(text, done) }, 150) }
        else v.say(text, done)
    }
    private val stops = RestStops.load(app)

    init {
        _ui.update { it.copy(carConnected = car.connected) }
        engine.observe { s ->
            // 질문이 끝났는데(타임아웃·정차·버튼 응답) 마이크가 열려 있으면 닫는다
            if (s.phase != Phase.QUESTION && _ui.value.listening) voice?.stopListening()
            _ui.update { it.copy(s = s, listening = it.listening && s.phase == Phase.QUESTION) }
        }
        engine.onEffect = ::apply
        VoiceFactory(app, ::log).create { v -> voice = v; _ui.update { it.copy(voiceEngine = v.engineName) }; log("음성 엔진: ${v.engineName}") }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val ph = engine.state.phase
                val fast = _ui.value.demo && (ph == Phase.DRIVING || ph == Phase.NAP)
                engine.tick(if (fast) 60 else 1)   // 시연: 주행·낮잠만 1초=1분, 환기·질문·스트레칭은 실시간
                car.log.firstOrNull()?.let { if (logLines.firstOrNull() != it) log(it) }
            }
        }
    }

    private fun apply(e: Effect) {
        when (e) {
            is Effect.Say -> speech.say(e.text)
            is Effect.Vent -> car.vent(e.strong)
            Effect.VentOff -> car.ventOff()
            is Effect.Ask -> voice?.let { speech.say(e.text) {
                if (engine.state.phase != Phase.QUESTION) return@say   // 질문을 읽는 사이 정차 등으로 넘어감
                _ui.update { it.copy(listening = true) }
                voice?.listen { heard ->
                    _ui.update { it.copy(listening = false) }
                    if (heard != null) { log("들음: $heard"); engine.answered() } else log("응답 없음")
                }
            } } ?: log("음성 엔진 없음, 화면 버튼으로 응답")
            Effect.VolumeUp -> voice?.volumeUp()
            Effect.VolumeNormal -> voice?.volumeNormal()
            is Effect.Light -> car.light(e.on)
            Effect.SuggestRest -> car.lastLocation { lat, lon ->
                _ui.update { it.copy(restOptions = RestStops.nearest(stops, lat, lon).map { r -> r to r.distanceKm(lat, lon) }) }
            }
            Effect.AddRestWaypoint -> _ui.value.restOptions.firstOrNull()?.first?.let { car.addWaypoint(it.lat, it.lon, it.name) }
        }
    }

    // 화면·Gleo에서 들어오는 입력
    fun start() = engine.startDriving()
    fun answered() = engine.answered()
    fun noAnswer() = engine.noAnswer()
    fun ventNow() = engine.ventNow()
    fun gearPark() = engine.stopped()
    fun gearDrive() = engine.startDriving()
    fun charge() = engine.chargingStarted()
    fun wake() = engine.wakeFromNap()
    fun stretchDone() = engine.stretchDone()
    fun restAccept() = engine.restAccepted()
    fun restDecline() = engine.restDeclined()
    fun strongMode(on: Boolean) = engine.setStrongMode(on)
    fun toggleDemo() = _ui.update { it.copy(demo = !it.demo) }
    fun fastForward(min: Int) = engine.tick(min * 60)

    fun gleo(action: String) {
        log("Gleo: $action")
        when (action) {
            "VENT_NOW" -> ventNow()
            "I_AM_FINE" -> answered()
            "GO_REST" -> { if (ui.value.s.phase != Phase.REST) apply(Effect.SuggestRest); restAccept() }
            "STRONG_MODE" -> strongMode(true)
        }
    }

    override fun onCleared() { focus.abandon(); voice?.release(); car.release() }
}
