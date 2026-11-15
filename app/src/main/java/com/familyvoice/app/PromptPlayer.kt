package com.familyvoice.app

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer

/**
 * res/raw 에 넣어둔 안내 음성(v_로 시작하는 파일)을 순서대로 재생한다.
 * Pleos TTS SDK가 나오기 전까지 TTS 대신 쓴다.
 * 차 안에서 잘 들리게 소리를 키워서 튼다 (VoiceRules.PROMPT_GAIN_MB).
 */
class PromptPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    /** names 순서대로 재생하고, 다 끝나면 onDone 호출. 없는 파일은 건너뛴다. */
    fun play(vararg names: String, onDone: () -> Unit = {}) {
        stop()
        playAt(names.toList(), 0, onDone)
    }

    fun has(name: String): Boolean = resId(name) != 0

    private fun playAt(names: List<String>, index: Int, onDone: () -> Unit) {
        if (index >= names.size) { onDone(); return }
        val id = resId(names[index])
        if (id == 0) { playAt(names, index + 1, onDone); return }
        val p = MediaPlayer.create(context, id, AudioFocus.SPEECH, am.generateAudioSessionId()) ?: run { playAt(names, index + 1, onDone); return }
        player = p
        enhancer = try {
            LoudnessEnhancer(p.audioSessionId).apply { setTargetGain(VoiceRules.PROMPT_GAIN_MB); enabled = true }
        } catch (_: Exception) { null }
        p.setOnErrorListener { mp, _, _ ->
            finish(mp)
            playAt(names, index + 1, onDone)
            true
        }
        p.setOnCompletionListener {
            finish(it)
            playAt(names, index + 1, onDone)
        }
        p.start()
    }

    /** 다 튼 플레이어 정리 (지금 플레이어면 소리 키우기도 같이 끈다) */
    private fun finish(mp: MediaPlayer) {
        if (player === mp) { enhancer?.release(); enhancer = null; player = null }
        mp.release()
    }

    fun stop() {
        enhancer?.release()
        enhancer = null
        player?.release()
        player = null
    }

    private fun resId(name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)
}
