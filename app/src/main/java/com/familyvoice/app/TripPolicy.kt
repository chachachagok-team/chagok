package com.familyvoice.app

import android.content.Context
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * "언제 물어볼지"를 정한다.
 * - 여행 모드(직접 켜기): 켜면 그날은 정차할 때마다 기록
 * - 자동 판단: 특별한 여행이면 그날 첫 정차 때 한 번만 "기록해 둘까요?" → 대답을 그날 하루 기억
 * 결정은 앱 안(SharedPreferences)에 저장해서 시동을 껐다 켜도 그날은 유지된다.
 */
/** 실제 여행 중에 멈춘 곳 하나 */
data class TripStop(val lat: Double, val lng: Double, val time: Long, val address: String)

class TripPolicy(context: Context) {

    companion object {
        /** 시연용 고정 집: 서울역. 고정을 풀려면 null 로 바꾼다 */
        val FIXED_HOME: Pair<Double, Double>? = 37.5547 to 126.9707
    }

    private val prefs = context.getSharedPreferences("trip_policy", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())

    // 시연용 기준 (실제 서비스 기준은 괄호)
    val farKm = 30f                 // 집에서 30km 이상 (실제도 비슷)
    val longStopMs = 10_000L        // 10초 이상 정차 (실제 15분)
    val samePlaceM = 300f

    /** 여행 모드: 오늘 날짜로 켜져 있을 때만 true */
    var tripModeToday: Boolean
        get() = prefs.getString("trip_mode_date", null) == today()
        set(on) { prefs.edit().putString("trip_mode_date", if (on) today() else null).apply() }

    /** 오늘 "기록해 둘까요?"에 대한 대답. null = 아직 안 물어봄 */
    var todayAnswer: Boolean?
        get() = if (prefs.getString("answer_date", null) == today()) prefs.getBoolean("answer", false) else null
        set(v) {
            if (v == null) prefs.edit().remove("answer_date").apply()
            else prefs.edit().putString("answer_date", today()).putBoolean("answer", v).apply()
        }

    fun setHome(lat: Double, lng: Double) =
        prefs.edit().putString("home_lat", lat.toString()).putString("home_lng", lng.toString()).apply()

    /** 집 위치. 지금은 서울역으로 고정 (FIXED_HOME). 고정을 풀면 저장된 값(setHome, 네비 즐겨찾기)을 쓴다 */
    val home: Pair<Double, Double>?
        get() {
            FIXED_HOME?.let { return it }
            val a = prefs.getString("home_lat", null)?.toDoubleOrNull() ?: return null
            val b = prefs.getString("home_lng", null)?.toDoubleOrNull() ?: return null
            return a to b
        }

    /** 지금 기록해야 하는가 */
    fun shouldRecord(): Boolean = tripActive || tripModeToday || todayAnswer == true

    /** 아직 묻지 않았고, 여행 중도 아니고, "아니요"라고 한 적도 없으면 물어볼 수 있음 */
    fun canAskToday(): Boolean = !tripActive && !tripModeToday && todayAnswer == null && !declined

    // ── 여행 하나 = 집에서 나가서 집에 돌아올 때까지 ─────────────────
    val maxTripDays = 7             // 이만큼 지나도 집에 안 돌아오면 자동으로 마감 (처가에 차를 두고 온 경우 등)
    val homeArriveM = 300f          // 집 이만큼 안에서 P → 집 도착
    val leftHomeM = 1000f           // 집에서 이만큼 멀어져야 "집을 나갔다"고 본다
    val skipPlaceM = 1000f          // "아니요"라고 한 곳·회사 근처는 다시 안 묻는다

    /** 지금 여행의 시작 시각 (여행 중이 아니면 0) */
    val tripStartedAt: Long get() = prefs.getLong("trip_started_at", 0L)
    val tripActive: Boolean get() = tripStartedAt > 0L

    /** 이번 여행 중에 집에서 한 번이라도 멀어졌는지 (그래야 집에 "돌아온" 것) */
    var leftHome: Boolean
        get() = prefs.getBoolean("trip_left_home", false)
        set(v) { prefs.edit().putBoolean("trip_left_home", v).apply() }

    /** "아니요/나중에"라고 함 → 집에 돌아올 때까지 다시 안 묻는다 */
    var declined: Boolean
        get() = prefs.getBoolean("trip_declined", false)
        set(v) { prefs.edit().putBoolean("trip_declined", v).apply() }

    /** 여행 시작. 이미 여행 중이면 시작 시각은 그대로 둔다. odoKm = 지금 차 주행거리계 (모르면 null) */
    fun startTrip(odoKm: Float? = null) {
        if (!tripActive) {
            prefs.edit().putLong("trip_started_at", System.currentTimeMillis()).putBoolean("trip_left_home", false)
                .putString("trip_odo", odoKm?.toString()).putLong("trip_drive_ms", 0L).putBoolean("trip_rain", false)
                .putString("trip_stops", "[]").apply()
        }
        declined = false
    }

    // ── 여행 카드에 들어갈 것들 (여행 중에 모은다) ──
    /** 여행 시작 때 차 주행거리계 (km) */
    val tripStartOdo: Float? get() = prefs.getString("trip_odo", null)?.toFloatOrNull()
    /** 기어 D 로 달린 시간 합 */
    val tripDriveMs: Long get() = prefs.getLong("trip_drive_ms", 0L)
    fun addDriveMs(ms: Long) { if (ms > 0) prefs.edit().putLong("trip_drive_ms", tripDriveMs + ms).apply() }
    /** 여행 중 와이퍼가 돈 적이 있는지 (날씨 "비") */
    var tripRained: Boolean
        get() = prefs.getBoolean("trip_rain", false)
        set(v) { prefs.edit().putBoolean("trip_rain", v).apply() }

    /** 여행 중 P 를 넣은 곳들. 300m 안에서 또 서면 한 곳으로 친다 */
    fun tripStops(): List<TripStop> = try {
        val a = org.json.JSONArray(prefs.getString("trip_stops", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { TripStop(it.getDouble("lat"), it.getDouble("lng"), it.getLong("t"), it.optString("addr")) } }
    } catch (_: Exception) { emptyList() }

    /** 이 시각 뒤에 남긴 정차 지우기 (잠깐 섰다 바로 출발한 곳은 '쉬었다 간 곳'이 아니라서). 지웠으면 true */
    fun dropStopsSince(time: Long): Boolean {
        val list = tripStops()
        val keep = list.filter { it.time < time }
        if (keep.size == list.size) return false
        saveStops(keep)
        return true
    }

    fun addStop(lat: Double, lng: Double, time: Long) {
        val list = tripStops()
        val last = list.lastOrNull()
        if (last != null && LocationTracker.distanceM(last.lat, last.lng, lat, lng) < 300f) return
        saveStops(list + TripStop(lat, lng, time, ""))
    }

    /** 마지막으로 선 곳에 주소를 붙인다 (네비가 늦게 알려줘서) */
    fun setLastStopAddress(addr: String) {
        val list = tripStops().toMutableList()
        if (list.isEmpty() || list.last().address.isNotEmpty()) return
        list[list.lastIndex] = list.last().copy(address = addr)
        saveStops(list)
    }

    private fun saveStops(list: List<TripStop>) {
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("lat", it.lat).put("lng", it.lng).put("t", it.time).put("addr", it.address)) }
        prefs.edit().putString("trip_stops", a.toString()).apply()
    }

    /** 여행 끝 (집 도착 또는 자동 마감). 다음 여행을 다시 물어볼 수 있게 대답도 지운다 */
    fun endTrip() {
        prefs.edit().putLong("trip_started_at", 0L).putBoolean("trip_left_home", false).putBoolean("trip_declined", false).apply()
        tripModeToday = false
        todayAnswer = null
    }

    /** 집에 안 돌아온 채로 너무 오래 지났는가 */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        tripActive && now - tripStartedAt > maxTripDays * 24L * 3600_000L

    /** 집 근처인가 (집 위치를 모르면 false) */
    fun isNearHome(lat: Double, lng: Double, withinM: Float = homeArriveM): Boolean {
        val h = home ?: return false
        return LocationTracker.distanceM(lat, lng, h.first, h.second) <= withinM
    }

    /** 회사 위치 (네비 즐겨찾기에서 가져옴) */
    var office: Pair<Double, Double>?
        get() {
            val a = prefs.getString("office_lat", null)?.toDoubleOrNull() ?: return null
            val b = prefs.getString("office_lng", null)?.toDoubleOrNull() ?: return null
            return a to b
        }
        set(v) {
            if (v == null) prefs.edit().remove("office_lat").remove("office_lng").apply()
            else prefs.edit().putString("office_lat", v.first.toString()).putString("office_lng", v.second.toString()).apply()
        }

    /** "아니요"라고 한 곳을 기억해 둔다 (예: 30km 넘는 출근길) */
    fun addSkipPlace(lat: Double, lng: Double) {
        val list = (skipPlaces() + (lat to lng)).takeLast(20)
        prefs.edit().putString("skip_places", list.joinToString(";") { "${it.first},${it.second}" }).apply()
    }

    /** "아니요" 한 곳 목록 비우기 (테스트 화면의 초기화 버튼) */
    fun clearSkipPlaces() = prefs.edit().remove("skip_places").apply()

    private fun skipPlaces(): List<Pair<Double, Double>> =
        prefs.getString("skip_places", "").orEmpty().split(";").mapNotNull { s ->
            val p = s.split(",")
            val a = p.getOrNull(0)?.toDoubleOrNull()
            val b = p.getOrNull(1)?.toDoubleOrNull()
            if (a != null && b != null) a to b else null
        }

    /** 다시 묻지 않을 곳인가 ("아니요" 한 곳, 회사) */
    fun isSkipPlace(lat: Double, lng: Double): Boolean {
        val o = office
        if (o != null && LocationTracker.distanceM(lat, lng, o.first, o.second) <= skipPlaceM) return true
        return skipPlaces().any { LocationTracker.distanceM(lat, lng, it.first, it.second) <= skipPlaceM }
    }

    /**
     * 네비 목적지로 여행인지 판단할 이유 목록.
     * 강한 신호: 목적지가 집에서 30km 넘게 → 이것 하나로 여행
     * 약한 신호: 처음 가는 곳 / 2명 이상 / 주말 → 2개 이상이면 여행
     * 집·회사·"아니요" 한 곳으로 가는 길이면 빈 목록
     */
    fun naviReasons(destLat: Double, destLng: Double, from: Location?, firstTime: Boolean, passengers: Int?): List<String> {
        if (isNearHome(destLat, destLng, skipPlaceM) || isSkipPlace(destLat, destLng)) return emptyList()
        val out = mutableListOf<String>()
        val base = home ?: from?.let { it.latitude to it.longitude }
        if (base != null) {
            val km = LocationTracker.distanceM(base.first, base.second, destLat, destLng) / 1000f
            if (km >= farKm) out += "목적지까지 ${km.toInt()}km"
        }
        if (firstTime) out += "처음 가는 곳"
        if (passengers != null && passengers >= 2) out += "${passengers}명 탑승"
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) out += "주말"
        return out
    }

    fun isNaviTrip(reasons: List<String>): Boolean =
        reasons.any { it.startsWith("목적지까지") } || reasons.size >= 2

    /** 특별한 여행인 이유 목록. 집에서 멀거나, 이유가 2개 이상이면 특별한 여행 */
    fun reasons(loc: Location?, memories: List<Memory>, passengers: Int?): List<String> {
        val out = mutableListOf<String>()
        val h = home
        if (loc != null && h != null) {
            val km = LocationTracker.distanceM(loc.latitude, loc.longitude, h.first, h.second) / 1000f
            if (km >= farKm) out += "집에서 ${km.toInt()}km"
        }
        if (loc != null) {
            val beenHere = memories.any { m ->
                m.lat != null && m.lng != null &&
                    LocationTracker.distanceM(loc.latitude, loc.longitude, m.lat, m.lng) <= samePlaceM
            }
            if (!beenHere) out += "처음 온 곳"
        }
        if (passengers != null && passengers >= 2) out += "${passengers}명 탑승"
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) out += "주말"
        return out
    }

    fun isSpecial(reasons: List<String>): Boolean =
        reasons.any { it.startsWith("집에서") } || reasons.size >= 2
}
