package com.familyvoice.app

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 하루 동안 실제로 지나온 길 (위도, 경도 목록) */
data class DayRoute(val day: String, val points: List<Pair<Double, Double>>)

/**
 * 하루 코스 저장: 위치를 30m마다 한 점씩 임시로 모아 두었다가
 * [기록할게요]면 남기고, [오늘은 괜찮아요]면 지운다. 기록하지 않은 지난 날의 임시 경로도 지운다.
 * 서버 없이 차 안(filesDir/routes/날짜.json)에만 저장.
 */
class RouteStore(context: Context) {
    private val dir = File(context.filesDir, "routes").apply { mkdirs() }
    private val kept = context.getSharedPreferences("route_days", Context.MODE_PRIVATE)
    private val pending = mutableListOf<Triple<Double, Double, Long>>()
    private var lastLat = Double.NaN
    private var lastLng = Double.NaN

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
    private fun fileOf(day: String) = File(dir, "$day.json")

    /** 위치 한 점 추가. 30m 이상 움직였을 때만 남긴다. 남겼으면 true */
    fun add(lat: Double, lng: Double): Boolean {
        if (!lastLat.isNaN() && LocationTracker.distanceM(lastLat, lastLng, lat, lng) < 30f) return false
        lastLat = lat; lastLng = lng
        pending.add(Triple(lat, lng, System.currentTimeMillis()))
        if (pending.size >= 5) flush()
        return true
    }

    fun flush() {
        if (pending.isEmpty()) return
        val f = fileOf(today())
        val arr = try { if (f.exists()) JSONArray(f.readText()) else JSONArray() } catch (_: Exception) { JSONArray() }
        pending.forEach { (la, ln, t) -> arr.put(JSONArray().put(la).put(ln).put(t)) }
        f.writeText(arr.toString())
        pending.clear()
    }

    /** 오늘 경로를 남기기로 함 */
    fun keepToday() = kept.edit().putBoolean(today(), true).apply()

    /** 오늘 경로를 지움 ([오늘은 괜찮아요]) */
    fun dropToday() {
        pending.clear()
        fileOf(today()).delete()
        kept.edit().remove(today()).apply()
        lastLat = Double.NaN; lastLng = Double.NaN
    }

    /** 저장된 길을 전부 지운다 (시연 준비) */
    fun clearAll() {
        pending.clear()
        dir.listFiles()?.forEach { it.delete() }
        kept.edit().clear().apply()
        lastLat = Double.NaN; lastLng = Double.NaN
    }

    /** 기록하기로 한 날만 남기고, 지난 임시 경로는 지운다 */
    fun cleanup() {
        val t = today()
        dir.listFiles()?.forEach { f ->
            val d = f.nameWithoutExtension
            if (d != t && !kept.getBoolean(d, false)) f.delete()
        }
    }

    /** 이 시각 이후에 지나온 점들 (여러 날에 걸친 여행도) — 여행 카드를 만들 때 */
    fun pointsSince(startMs: Long): List<Pair<Double, Double>> {
        flush()
        val startDay = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date(startMs))
        return dir.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension >= startDay }?.sortedBy { it.name }
            ?.flatMap { f ->
                try {
                    val a = JSONArray(f.readText())
                    (0 until a.length()).mapNotNull { i ->
                        val p = a.getJSONArray(i)
                        if (p.length() >= 3 && p.getLong(2) < startMs) null else p.getDouble(0) to p.getDouble(1)
                    }
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
    }

    /** 저장된 경로들 (최근 날짜 먼저) */
    fun load(): List<DayRoute> {
        flush()
        return dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name }?.mapNotNull { f ->
            try {
                val a = JSONArray(f.readText())
                DayRoute(f.nameWithoutExtension, (0 until a.length()).map { i -> a.getJSONArray(i).let { it.getDouble(0) to it.getDouble(1) } })
            } catch (_: Exception) { null }
        } ?: emptyList()
    }
}
