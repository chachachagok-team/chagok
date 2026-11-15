package com.familyvoice.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 시연용: Pleos 시뮬레이터에서 현재 차 속도를 읽어 온다.
 *
 * Pleos Vehicle SDK 에는 "현재 속도" API 가 없다 (기어·와이퍼·주행거리만 열려 있다).
 * 그런데 시뮬레이터 조작 페이지(PC 의 127.0.0.1:8088)는 차 상태를 JSON 으로 내려 준다:
 *     GET /vehicle/state  →  { "controls": { "speed_kph": 73, ... }, ... }
 * 에뮬레이터 안에서 PC 는 10.0.2.2 이므로 그 주소로 읽는다.
 *
 * ⚠ 실제 차에는 이런 서버가 없다. 시연 환경에서만 쓰는 보조 통로이고,
 *   읽히지 않으면 조용히 꺼져서 앱이 만든 속도 곡선으로 돌아간다 (VoiceRules.SIM_SPEED 로 끌 수 있음).
 */
object SimSpeed {

    private val hosts = listOf("10.0.2.2", "127.0.0.1")   // 에뮬레이터에서 본 PC → 혹시 모를 경우
    @Volatile private var host: String? = null
    @Volatile private var speed = -1          // -1 = 아직 못 읽음
    @Volatile private var at = 0L
    @Volatile private var running = false
    @Volatile var lastError: String = "아직 안 읽어 봤어요"
        private set

    /** 폴링 시작 (앱 시작 때 한 번) */
    fun start() {
        if (running || !VoiceRules.SIM_SPEED) return
        running = true
        Thread({
            while (running) {
                readOnce()
                Thread.sleep(VoiceRules.SIM_SPEED_POLL_MS)
            }
        }, "sim-speed").apply { isDaemon = true }.start()
    }

    fun stop() { running = false }

    /** 지금 속도(km/h). 못 읽으면 null */
    fun kmh(): Int? {
        if (!VoiceRules.SIM_SPEED) return null
        if (speed < 0 || System.currentTimeMillis() - at > VoiceRules.SIM_SPEED_HOLD_MS) return null
        return speed
    }

    fun status(): String = kmh()?.let { "시뮬레이터에서 속도 읽힘: ${it}km/h (${host})" } ?: "못 읽음 · $lastError"

    private fun readOnce() {
        for (h in listOfNotNull(host) + hosts) {
            try {
                val c = (URL("http://$h:${VoiceRules.SIM_SPEED_PORT}/vehicle/state").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 700; readTimeout = 700; requestMethod = "GET"
                }
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val v = JSONObject(body).optJSONObject("controls")?.optInt("speed_kph", -1) ?: -1
                if (v >= 0) {
                    host = h; speed = v; at = System.currentTimeMillis(); lastError = ""
                    return
                }
                lastError = "state 에 controls.speed_kph 가 없어요"
            } catch (e: Exception) {
                lastError = "$h: ${e.javaClass.simpleName}"
            }
        }
    }
}
