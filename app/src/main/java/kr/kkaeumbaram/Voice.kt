package kr.kkaeumbaram

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.Mode as TtsMode

/** 말하기·듣기. Pleos가 안 뜨면 Android 기본 TTS/STT로 내려간다(보수안). */
interface Voice {
    val engineName: String
    fun say(text: String, onDone: () -> Unit = {})
    fun listen(onHeard: (String?) -> Unit)   // null = 못 들음
    fun volumeUp()
    fun volumeNormal()
    fun release()
}

class VoiceFactory(private val context: Context, private val log: (String) -> Unit) {
    fun create(onReady: (Voice) -> Unit) {
        val pleos = PleosVoice(context, log)
        pleos.init(
            ok = { onReady(pleos) },
            failed = { pleos.release(); onReady(AndroidVoice(context, log)) },
        )
    }
}

class PleosVoice(private val context: Context, private val log: (String) -> Unit) : Voice {
    override val engineName = "Pleos TTS/STT"
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var sayDone: () -> Unit = {}
    private var heard: ((String?) -> Unit)? = null

    private val ttsEvents = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { val d = sayDone; sayDone = {}; d() }
        override fun onError(e: ai.pleos.playground.tts.constant.ErrorType) { log("TTS 오류 $e"); val d = sayDone; sayDone = {}; d() }
        override fun onStop() {}
        override fun onUpdatedRms(v: Double) {}
    }
    private val sttResults = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) { logi("STT onUpdated '$text' final=$isFinal heard=${heard != null}"); if (isFinal) deliver(text) }
        override fun onUpdatedEpdData(a: Long, b: Long) {}
        override fun onStartedRecognition() {}
        override fun onEndedRecognition() {} // 최종 onUpdated(isFinal)보다 먼저 와서 여기서 null을 주면 결과를 놓친다. 무응답은 Engine 타임아웃이 처리
        override fun onError(e: ai.pleos.playground.stt.constant.ErrorType) { logi("STT onError $e"); log("STT 오류 $e"); deliver(null) }
        override fun onReady() {}
        override fun onUpdatedRms(v: Double) {}
    }
    private fun deliver(t: String?) { logi("STT deliver '$t' heard=${heard != null}"); val h = heard ?: return; heard = null; h(t) }

    fun init(ok: () -> Unit, failed: () -> Unit) {
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA); tts.addEventListener(ttsEvents)
                    runCatching {
                        tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : ai.pleos.playground.tts.listener.OnServerConnectionListener {
                            override fun onConnected() { log("TTS 서버 연결") }
                            override fun onFailed(e: ai.pleos.playground.tts.constant.ErrorType) { log("TTS 서버 등록 실패 $e") }
                        })
                    }.onFailure { log("TTS registerApp 예외 ${it.message}") }
                    runCatching {
                        stt.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                            override fun onSuccess() {
                                stt.setLocale(Locale.KOREA); stt.addListener(sttResults)
                                runCatching {
                                    stt.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : ai.pleos.playground.stt.listener.OnServerConnectionListener {
                                        override fun onConnected() { log("STT 서버 연결") }
                                        override fun onFailed(e: ai.pleos.playground.stt.constant.ErrorType) { log("STT 서버 등록 실패 $e") }
                                    })
                                }.onFailure { log("STT registerApp 예외 ${it.message}") }
                                log("Pleos 음성 준비"); ok()
                            }
                            override fun onFailure(e: ai.pleos.playground.stt.constant.ErrorType) { log("Pleos STT 초기화 실패 $e"); ok() /* TTS만이라도 */ }
                        })
                    }.onFailure { log("Pleos STT 예외 ${it.message}"); ok() }
                }
                override fun onFailure(e: ai.pleos.playground.tts.constant.ErrorType) { log("Pleos TTS 초기화 실패 $e"); failed() }
            })
        }.onFailure { log("Pleos TTS 예외 ${it.message}"); failed() }
    }

    override fun say(text: String, onDone: () -> Unit) { sayDone = onDone; runCatching { tts.speak(text, true) }.onFailure { log("speak 실패"); onDone() } }
    override fun listen(onHeard: (String?) -> Unit) { logi("STT listen"); heard = onHeard; runCatching { stt.request() }.onFailure { log("STT request 실패"); deliver(null) } }
    override fun volumeUp() = setVol(0.9f)
    override fun volumeNormal() = setVol(0.6f)
    private fun setVol(f: Float) = runCatching {
        val s = AudioManager.STREAM_MUSIC
        audio.setStreamVolume(s, (audio.getStreamMaxVolume(s) * f).toInt(), 0)
    }.let {}
    override fun release() { runCatching { tts.removeEventListener(ttsEvents); tts.release(); stt.removeListener(sttResults); stt.release() } }
}

/** Android 기본 엔진. 구글 에뮬레이터·일반 폰에서 데모용. */
class AndroidVoice(private val context: Context, private val log: (String) -> Unit) : Voice {
    override val engineName = "Android TTS/STT"
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private var pending: Pair<String, () -> Unit>? = null
    private lateinit var tts: android.speech.tts.TextToSpeech
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = android.speech.tts.TextToSpeech(context.applicationContext) { st ->
            ready = st == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) runCatching { tts.setLanguage(Locale.KOREA) }
            log(if (ready) "Android TTS 준비" else "Android TTS 없음")
            pending?.let { (t, d) -> pending = null; say(t, d) }
        }
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { doneMap.remove(id)?.invoke() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { doneMap.remove(id)?.invoke() }
        })
    }
    private val doneMap = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private var seq = 0

    override fun say(text: String, onDone: () -> Unit) {
        if (!ready) { pending = text to onDone; return }
        val id = "u${seq++}"
        doneMap[id] = { android.os.Handler(context.mainLooper).post(onDone) }
        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, id)
    }

    override fun listen(onHeard: (String?) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { log("STT 없음"); onHeard(null); return }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) { onHeard(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()) }
            override fun onError(e: Int) { log("STT 오류 $e"); onHeard(null) }
            override fun onReadyForSpeech(p: Bundle?) {}; override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}; override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}; override fun onPartialResults(p: Bundle?) {}; override fun onEvent(t: Int, p: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }
    override fun volumeUp() = setVol(0.9f)
    override fun volumeNormal() = setVol(0.6f)
    private fun setVol(f: Float) = runCatching {
        val s = AudioManager.STREAM_MUSIC
        audio.setStreamVolume(s, (audio.getStreamMaxVolume(s) * f).toInt(), 0)
    }.let {}
    override fun release() { tts.shutdown(); recognizer?.destroy() }
}

internal fun logi(s: String) = Log.i("Kkaeumbaram", s)
