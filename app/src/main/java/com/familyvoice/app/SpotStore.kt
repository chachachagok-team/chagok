package com.familyvoice.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 여행과 상관없이 "따로 남긴 곳"(음식점·관광지·휴게소)을 차 안에 저장한다.
 * filesDir/spots.json · 서버로 보내지 않는다.
 */
class SpotStore(context: Context) {
    private val file = File(context.filesDir, "spots.json")

    fun load(): List<Spot> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val kind = runCatching { SpotKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            Spot(
                id = o.getString("id"), tripId = o.optString("tripId", Journal.SOLO), kind = kind,
                name = o.getString("name"), lat = o.getDouble("lat"), lng = o.getDouble("lng"),
                time = o.optString("time"), note = o.optString("note"), photo = null,
                rating = if (o.has("rating")) o.getInt("rating") else null,
                voice = if (o.has("voice")) o.getLong("voice") else null
            )
        }
    } catch (_: Exception) { emptyList() }

    fun save(list: List<Spot>) {
        val a = JSONArray()
        list.forEach { s ->
            val o = JSONObject().put("id", s.id).put("tripId", s.tripId).put("kind", s.kind.name).put("name", s.name)
                .put("lat", s.lat).put("lng", s.lng).put("time", s.time).put("note", s.note)
            s.rating?.let { o.put("rating", it) }
            s.voice?.let { o.put("voice", it) }
            a.put(o)
        }
        file.writeText(a.toString())
    }
}
