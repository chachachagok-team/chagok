package com.familyvoice.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 한 번 남긴 이야기 기억 (11/11~: 녹음 파일이 아니라 Pleos 음성 인식(STT)으로 받은 "글자"를 남긴다).
 * 다시 그 장소를 지나면 차가 Pleos TTS 로 읽어 준다. 녹음 파일은 만들지 않는다.
 */
data class Memory(
    val id: Long,            // 남긴 시각 (밀리초)
    val fileName: String,    // (예전 버전 호환용) 녹음 파일 이름 — 지금은 늘 ""
    val rainy: Boolean,      // 녹음할 때 앞 와이퍼가 돌고 있었는지
    val peak: Int,           // 녹음 최고 음량 (목소리가 들어왔는지 판단용)
    val lat: Double? = null, // 녹음한 장소 (위도)
    val lng: Double? = null, // 녹음한 장소 (경도)
    val question: String? = null, // 차가 한 질문 종류 (revisit, rain, long_drive, night, family, first, basic)
    val text: String = ""         // STT 로 받은 가족의 말 (차가 나중에 읽어 줄 문장)
)

/** 이야기 기억을 앱 안(filesDir/memories.json)에 저장하고 불러온다. 서버 없이 차 안에만 저장. */
class MemoryStore(private val context: Context) {

    private val indexFile get() = File(context.filesDir, "memories.json")

    /** (예전 버전 호환) 녹음 파일 — 지우기용으로만 남겨 둔다 */
    private fun fileOf(m: Memory): File = File(context.filesDir, m.fileName)

    fun load(): List<Memory> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Memory(
                    o.getLong("id"), o.getString("file"), o.optBoolean("rainy"), o.optInt("peak"),
                    if (o.has("lat")) o.getDouble("lat") else null,
                    if (o.has("lng")) o.getDouble("lng") else null,
                    o.optString("q").ifEmpty { null },
                    o.optString("t")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(m: Memory): List<Memory> = write(load() + m)

    /** 같은 id 가 있으면 바꾸고, 없으면 더한다 */
    fun upsert(m: Memory): List<Memory> = write(load().filter { it.id != m.id } + m)

    /** 이 시각 이후에 남긴 목소리를 지운다 (여행 기록 취소). 남은 목록을 돌려준다 */
    fun removeSince(since: Long): List<Memory> {
        val (gone, keep) = load().partition { it.id >= since }
        gone.forEach { if (it.fileName.isNotEmpty()) fileOf(it).delete() }
        return write(keep)
    }

    private fun write(all: List<Memory>): List<Memory> {
        val arr = JSONArray()
        all.forEach {
            val o = JSONObject().put("id", it.id).put("file", it.fileName).put("rainy", it.rainy).put("peak", it.peak)
            if (it.lat != null && it.lng != null) o.put("lat", it.lat).put("lng", it.lng)
            if (it.question != null) o.put("q", it.question)
            if (it.text.isNotEmpty()) o.put("t", it.text)
            arr.put(o)
        }
        indexFile.writeText(arr.toString())
        return all
    }
}
