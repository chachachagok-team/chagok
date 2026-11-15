package com.familyvoice.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 실제로 다녀온 여행(카드)과 그 여행에서 들른 곳을 차 안에 저장한다. 앱을 꺼도 남는다.
 * filesDir/trips.json · 서버로 보내지 않는다. (예시 여행은 코드에 있어서 저장하지 않음)
 */
class TripStore(context: Context) {
    private val file = File(context.filesDir, "trips.json")

    fun load(): Pair<List<Trip>, List<Spot>> = try {
        val o = JSONObject(file.readText())
        val ta = o.getJSONArray("trips")
        val trips = (0 until ta.length()).map { i ->
            val t = ta.getJSONObject(i)
            val r = t.getJSONArray("route")
            Trip(
                id = t.getString("id"), title = t.getString("title"), dateLabel = t.getString("dateLabel"),
                nights = t.getString("nights"), weather = t.getString("weather"), distanceKm = t.getInt("distanceKm"),
                driveTime = t.getString("driveTime"), people = t.getInt("people"),
                route = (0 until r.length()).map { k -> r.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } },
                sortKey = t.getString("sortKey"),
                riders = t.optJSONArray("riders")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                summary = t.optString("summary")
            )
        }
        val sa = o.getJSONArray("spots")
        val spots = (0 until sa.length()).mapNotNull { i ->
            val s = sa.getJSONObject(i)
            val kind = runCatching { SpotKind.valueOf(s.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            Spot(
                id = s.getString("id"), tripId = s.getString("tripId"), kind = kind, name = s.getString("name"),
                lat = s.getDouble("lat"), lng = s.getDouble("lng"), time = s.optString("time"), note = s.optString("note"),
                photo = null, rating = if (s.has("rating")) s.getInt("rating") else null,
                voice = if (s.has("voice")) s.getLong("voice") else null
            )
        }
        trips to spots
    } catch (_: Exception) { emptyList<Trip>() to emptyList() }

    fun save(trips: List<Trip>, spots: List<Spot>) {
        val ta = JSONArray()
        trips.forEach { t ->
            val r = JSONArray()
            t.route.forEach { (la, ln) -> r.put(JSONArray().put(la).put(ln)) }
            ta.put(
                JSONObject().put("id", t.id).put("title", t.title).put("dateLabel", t.dateLabel).put("nights", t.nights)
                    .put("weather", t.weather).put("distanceKm", t.distanceKm).put("driveTime", t.driveTime)
                    .put("people", t.people).put("route", r).put("sortKey", t.sortKey)
                    .put("riders", JSONArray(t.riders))
                    .put("summary", t.summary)
            )
        }
        val sa = JSONArray()
        spots.forEach { s ->
            val o = JSONObject().put("id", s.id).put("tripId", s.tripId).put("kind", s.kind.name).put("name", s.name)
                .put("lat", s.lat).put("lng", s.lng).put("time", s.time).put("note", s.note)
            s.rating?.let { o.put("rating", it) }
            s.voice?.let { o.put("voice", it) }          // 그 자리에서 남긴 목소리
            sa.put(o)
        }
        file.writeText(JSONObject().put("trips", ta).put("spots", sa).toString())
    }
}
