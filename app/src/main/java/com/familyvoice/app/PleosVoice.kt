package com.familyvoice.app

import android.content.Context
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServerListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServerListener
import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode as LlmMode
import ai.pleos.playground.llm.listener.ResultListener as LlmResultListener
import ai.pleos.playground.llm.listener.OnServerConnectionListener as LlmServerListener
import java.util.Locale

/**
 * Pleos 음성 SDK 묶음
 * - TTS: 차가 문장을 직접 읽어 준다 (녹음 파일 없이 아무 문장이나)
 * - STT: 탑승자가 말한 걸 글자로 알아듣는다 ("응", "아니" 대답)
 * 둘 다 차 안의 Pleos 음성 서비스(ai.pleos.playground.caas)에 연결하고,
 * Playground 프로젝트의 클라이언트 ID/시크릿으로 앱을 등록(registerApp)해야 쓸 수 있다.
 * 값은 local.properties(GitHub에 안 올라감) → BuildConfig 로 들어온다.
 */
class PleosVoice(private val context: Context, private val log: (String) -> Unit) {

    private val clientId = BuildConfig.PLEOS_CLIENT_ID
    private val clientSecret = BuildConfig.PLEOS_CLIENT_SECRET

    private var tts: TextToSpeech? = null
    var ttsReady = false
        private set
    private var onSpeakDone: ((Boolean) -> Unit)? = null   // true = 끝까지 말함, false = 오류

    private var stt: SpeechToText? = null
    var sttReady = false
        private set
    private var onHeard: ((String, Boolean) -> Unit)? = null   // (들은 말, 정상 여부) — false = 엔진·권한 오류

    fun hasKeys() = clientId.isNotBlank() && clientSecret.isNotBlank()

    // ───────── TTS ─────────
    private val ttsModes = listOf(TtsMode.SERVER, TtsMode.HYBRID, TtsMode.ON_DEVICE)  // 에뮬레이터엔 차 안 엔진이 없어서 서버 방식 먼저

    /** SERVER → HYBRID → ON_DEVICE 순서로 되는 방식을 찾는다 */
    /** 시험용: TTS 를 ON_DEVICE(차 안 엔진) 하나로만 연결 */
    fun initTtsOnDevice() { try { tts?.release() } catch (_: Throwable) {}; tts = null; ttsReady = false; initTts(ttsModes.indexOf(TtsMode.ON_DEVICE)) }

    fun initTts(modeIndex: Int = 0) {
        if (!hasKeys()) { log("TTS: local.properties에 pleos.clientId / pleos.clientSecret 이 없어요"); return }
        if (modeIndex >= ttsModes.size) { log("TTS: 세 가지 방식 모두 실패 → 준비해 둔 음성 파일로 대신해요"); return }
        val mode = ttsModes[modeIndex]
        fun next(reason: String) {
            log("TTS($mode) 실패: $reason")
            try { tts?.release() } catch (_: Throwable) {}
            tts = null; ttsReady = false
            initTts(modeIndex + 1)
        }
        try {
            ttsReady = false
            val t = TextToSpeech(context, mode)
            tts = t
            log("TTS($mode) 연결 시도…")
            t.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    // SDK 안쪽 준비가 끝난 뒤에(initialize 성공 후) 리스너를 붙여야 한다
                    try {
                        t.addEventListener(object : TtsEventListener {
                            override fun onStart() = log("TTS: 말하는 중…")
                            override fun onReady() {}
                            override fun onDone() { log("TTS: 다 말했어요"); onSpeakDone?.invoke(true); onSpeakDone = null }
                            override fun onError(e: TtsError) { log("TTS 오류: $e"); onSpeakDone?.invoke(false); onSpeakDone = null }
                            override fun onStop() {}
                            override fun onUpdatedRms(rms: Double) {}
                        })
                        t.setLocale(Locale.KOREAN)
                    } catch (e: Throwable) { next("리스너 ${e.javaClass.simpleName}: ${e.message}"); return }
                    if (mode == TtsMode.ON_DEVICE) { ttsReady = true; log("TTS 준비 완료 ✓ ($mode)"); return }
                    t.registerApp(clientId, clientSecret, object : TtsServerListener {
                        override fun onConnected() { ttsReady = true; log("TTS 준비 완료 ✓ ($mode)") }
                        override fun onFailed(e: TtsError) = next("앱 등록 $e")
                    })
                }
                override fun onFailure(e: TtsError) = next("연결 $e")
            })
        } catch (e: Throwable) { next("${e.javaClass.simpleName}: ${e.message}") }
    }

    /** 문장을 차 목소리로 읽기. 준비 안 됐으면 바로 onDone */
    /** 문장을 차 목소리(Pleos TTS)로 읽기. onDone(true) = 다 읽음, onDone(false) = 준비 안 됨·오류 */
    fun speak(text: String, onDone: (Boolean) -> Unit = {}) {
        val t = tts
        if (t == null || !ttsReady) { log("TTS가 아직 준비 안 됐어요"); onDone(false); return }
        onSpeakDone = onDone
        try { t.speak(text, true) } catch (e: Throwable) { log("TTS speak 오류: ${e.message}"); onSpeakDone = null; onDone(false) }
    }

    fun stopSpeaking() { try { tts?.stop() } catch (_: Throwable) {}; onSpeakDone = null }

    // ───────── STT ─────────
    /**
     * STT 연결. 기본은 ON_DEVICE(차 안 엔진) — 앱 등록(clientId/secret)이 필요 없어서
     * 콘솔에서 SpeechToText 가 Coming Soon 이어도 동작할 수 있다 (11/11 시험용).
     * 문서 순서대로: 생성 → initialize → (성공 후) addListener → setLocale → (서버 방식만) registerApp
     */
    var sttMode: SttMode = SttMode.ON_DEVICE
        private set

    fun initStt(mode: SttMode = SttMode.ON_DEVICE) {
        try { stt?.release() } catch (_: Throwable) {}
        stt = null; sttReady = false; sttMode = mode
        if (mode != SttMode.ON_DEVICE && !hasKeys()) { log("STT($mode): local.properties에 키가 없어요"); return }
        try {
            val s = SpeechToText(context, mode)
            stt = s
            log("STT($mode) 연결 시도…")
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    try {
                        s.addListener(object : ResultListener {
                            override fun onUpdated(text: String, isFinal: Boolean) {
                                log("STT: \"$text\"" + if (isFinal) " (최종)" else "")
                                if (isFinal) { onHeard?.invoke(text, true); onHeard = null }
                            }
                            override fun onUpdatedEpdData(start: Long, end: Long) {}
                            override fun onStartedRecognition() = log("STT: 듣고 있어요… 말해 보세요")
                            override fun onEndedRecognition() = log("STT: 듣기 끝")
                            override fun onError(e: SttError) { log("STT($mode) 오류: $e"); onHeard?.invoke("", false); onHeard = null }
                            override fun onReady() = log("STT: 준비됨")
                            override fun onUpdatedRms(rms: Double) {}
                        })
                        s.setLocale(Locale.KOREAN)
                    } catch (e: Throwable) { log("STT($mode) 리스너 오류: ${e.javaClass.simpleName} ${e.message}"); return }
                    if (mode == SttMode.ON_DEVICE) { sttReady = true; log("STT 준비 완료 ✓ (ON_DEVICE · 앱 등록 없이)"); return }
                    log("STT: 음성 서비스 연결됨, 앱 등록 중…")
                    s.registerApp(clientId, clientSecret, object : SttServerListener {
                        override fun onConnected() { sttReady = true; log("STT 준비 완료 ✓ ($mode)") }
                        override fun onFailed(e: SttError) { log("STT($mode) 앱 등록 실패: $e") }
                    })
                }
                override fun onFailure(e: SttError) = log("STT($mode) 연결 실패: $e")
            })
        } catch (e: Throwable) { log("STT($mode) 시작 오류: ${e.javaClass.simpleName} ${e.message}") }
    }

    /** 한 번 듣고 최종 결과를 돌려준다. (들은 말, 정상 여부) — 못 알아들으면 ("", true), 엔진·권한 오류면 ("", false) */
    fun listen(onResult: (String, Boolean) -> Unit) {
        val s = stt
        if (s == null || !sttReady) { log("STT가 아직 준비 안 됐어요"); onResult("", false); return }
        onHeard = onResult
        try { s.request() } catch (e: Throwable) { log("STT request 오류: ${e.message}"); onHeard = null; onResult("", false) }
    }

    /** "응/네/좋아/기록해" → true, "아니/괜찮아/됐어" → false, 모르겠으면 null */
    fun yesNo(text: String): Boolean? {
        val t = text.replace(" ", "")
        if (listOf("아니", "괜찮", "됐어", "싫어", "안해", "노").any { it in t }) return false
        if (listOf("응", "네", "예", "좋아", "그래", "기록", "해줘", "어").any { it in t }) return true
        return null
    }

    // ───────── LLM (Pleos Gleo AI SDK 의 LLM SDK) ─────────
    // 쓰는 곳: 예전에 이야기를 남긴 곳에 다가갈 때, 그때 남긴 말을 넣고 "들렀다 갈까요?" 제안 문장을 만든다.
    // 안 되면(지금 에뮬레이터처럼 서비스가 안 열려 있으면) 앱이 정해 둔 문장 틀을 쓴다.
    private var llm: LLM? = null
    var llmReady = false
        private set

    /** 문서 순서대로: 생성 → initialize → (서버 방식만) registerApp */
    fun initLlm(mode: LlmMode = LlmMode.SERVER) {
        try { llm?.release() } catch (_: Throwable) {}
        llm = null; llmReady = false
        try {
            val l = LLM(context, mode)
            llm = l
            l.initialize()
            if (mode == LlmMode.SERVER) {
                if (!hasKeys()) { log("LLM: 클라이언트 ID/시크릿이 없어요"); return }
                l.registerApp(clientId, clientSecret, object : LlmServerListener {
                    override fun onConnected() { llmReady = true; log("LLM 준비 완료 ✓ ($mode)") }
                    override fun onFailed(reason: String) { log("LLM($mode) 앱 등록 실패: $reason") }
                })
            } else {
                llmReady = true
                log("LLM: $mode 로 연결 시도 (실제로 되는지는 문장 만들기에서 확인)")
            }
        } catch (e: Throwable) { log("LLM($mode) 시작 오류: ${e.javaClass.simpleName} ${e.message}") }
    }

    /** 문장 하나 만들기. 실패하면 null */
    fun generate(prompt: String, onResult: (String?) -> Unit) {
        val l = llm
        if (l == null || !llmReady) { onResult(null); return }
        var done = false
        try {
            l.generateContent(prompt, object : LlmResultListener {
                override fun onResponse(text: String, isFinal: Boolean) {
                    if (done) return; done = true; log("LLM 응답: ${text.take(40)}…"); onResult(text)
                }
                override fun onError(reason: String) {
                    if (done) return; done = true; log("LLM 오류: $reason"); onResult(null)
                }
            })
        } catch (e: Throwable) { if (!done) { done = true; log("LLM 호출 오류: ${e.message}"); onResult(null) } }
    }

    fun release() {
        try { tts?.release() } catch (_: Throwable) {}
        try { stt?.release() } catch (_: Throwable) {}
        try { llm?.release() } catch (_: Throwable) {}
    }
}
