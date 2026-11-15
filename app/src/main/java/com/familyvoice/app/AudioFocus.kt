package com.familyvoice.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * 차가 말하거나 목소리를 들려줄 때 다른 소리(음악 등)와 겹치지 않게 한다 (오디오 포커스).
 *  - pause(): 음악을 잠깐 멈추게 한다. 질문하고 대답을 듣는 동안 (마이크에 음악이 섞이지 않게)
 *  - duck():  음악 소리를 줄인다. 예전 목소리를 들려줄 때
 *  - release(): 원래대로
 * 네비 안내는 차 쪽에서 우선순위를 정한다.
 */
class AudioFocus(context: Context) {

    companion object {
        /**
         * 앱이 내는 소리의 종류. 안내·목소리는 "비서 말소리"로 낸다.
         * 에뮬레이터에서 소리가 너무 작거나 안 나면 USAGE_MEDIA 로 바꿔 보기.
         */
        private const val USAGE = AudioAttributes.USAGE_ASSISTANT

        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(USAGE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null

    fun pause(): Boolean = acquire(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

    fun duck(): Boolean = acquire(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

    private fun acquire(kind: Int): Boolean {
        release()
        val r = AudioFocusRequest.Builder(kind)
            .setAudioAttributes(SPEECH)
            .setOnAudioFocusChangeListener { }
            .build()
        request = r
        return try {
            am.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) { false }
    }

    fun release() {
        request?.let { r -> try { am.abandonAudioFocusRequest(r) } catch (_: Exception) {} }
        request = null
    }
}
