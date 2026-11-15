package com.familyvoice.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import androidx.compose.ui.graphics.Color

/** 장소 종류: 지도 위 칩(전체 옆)으로 나눠 보는 단위 */
enum class SpotKind(val label: String, val short: String, val color: Color, val hex: String) {
    FOOD("음식점", "맛집", Color(0xFFC9853A), "#C9853A"),
    SIGHT("관광지", "구경", Color(0xFF4E8C7A), "#4E8C7A"),
    REST("휴게소", "쉼", Color(0xFF2F5D8A), "#2F5D8A"),
    STOP("들른 곳", "들름", Color(0xFF6E8FA8), "#6E8FA8")   // 실제 여행에서 멈춘 곳 (종류를 모를 때). 카테고리 탭에는 안 나옴
}

/** 여행 중 들른 한 곳: 사진 + 한 줄 기록 */
data class Spot(
    val id: String,
    val tripId: String,
    val kind: SpotKind,
    val name: String,
    val lat: Double,
    val lng: Double,
    val time: String,          // "9월 3일 오후 12:40"
    val note: String,          // 한 줄 기록 (후기)
    val photo: Int? = null,    // R.drawable.… (없으면 색 카드)
    val rating: Int? = null,   // 평점 1~5 (없으면 안 보임)
    val voice: Long? = null    // 같이 남긴 목소리 (Memory id). 없으면 null
)

/** 집 출발 ~ 집 도착을 여행 하나로 묶는다 */
data class Trip(
    val id: String,
    val title: String,         // "부산 여행"
    val dateLabel: String,     // "2026년 9월 3일"
    val nights: String,        // "2박 3일"
    val weather: String,       // "맑음 · 27°C"
    val distanceKm: Int,
    val driveTime: String,     // "4시간 50분"
    val people: Int,
    val route: List<Pair<Double, Double>>,   // 집 → 목적지 (위도, 경도)
    val sortKey: String,       // "2026-09-03" 최신순 정렬용
    val riders: List<String> = emptyList(),  // 그날 함께 탄 사람 ("엄마", "아들"…). 모르면 비움
    val summary: String = ""                 // 여행 전체 한 줄 요약 (집 도착 때 채움). 없으면 비움
)

/**
 * 시연용 예시 데이터 (가상 가족). 실제 기록이 쌓이면 이 자리를 실제 데이터로 바꾼다.
 * 경로는 고속도로를 따라 대략 찍은 점들이다.
 */
object Journal {
    private val seoul = TripPolicy.FIXED_HOME ?: (37.5547 to 126.9707)   // 집 (서울역)

    /** 돌아오는 길 (마지막에 집으로). 가는 길과 다른 길로 와야 지도에서 왕복이 보인다 */
    private fun back(vararg p: Pair<Double, Double>): List<Pair<Double, Double>> = p.toList() + seoul

    private val seedTrips: List<Trip> = listOf(
        Trip(
            "busan", "부산 여행", "2026년 9월 3일", "2박 3일", "맑음 · 27°C", 412, "4시간 50분", 5,
            listOf(seoul, 37.4700 to 127.0380, 37.2820 to 127.1040, 37.1500 to 127.0770, 37.0160 to 127.1800,
                36.8350 to 127.1800, 36.6300 to 127.3700, 36.4000 to 127.4300, 36.3000 to 127.5800, 36.1700 to 127.7800,
                36.2300 to 127.9200, 36.1300 to 128.1100, 36.1200 to 128.3500, 35.9900 to 128.4500, 35.8900 to 128.6300,
                35.8300 to 128.7600, 35.9500 to 128.9300, 35.8400 to 129.2100, 35.5700 to 129.1300, 35.2800 to 129.0900,
                35.1600 to 129.1600) +
                back(35.2300 to 128.8900, 35.5300 to 128.4900, 35.9000 to 128.3000, 36.4100 to 128.1600, 36.9700 to 127.9300, 37.3000 to 127.6300, 37.4500 to 127.2000),
            "2026-09-03"
        ),
        Trip(
            "gangneung", "강릉 여행", "2025년 8월 14일", "1박 2일", "비 · 23°C", 238, "3시간 10분", 4,
            listOf(seoul, 37.4700 to 127.0380, 37.2820 to 127.1040, 37.2400 to 127.2000, 37.2400 to 127.3800,
                37.3000 to 127.6400, 37.3100 to 127.8200, 37.3600 to 127.9500, 37.4800 to 128.0600, 37.5100 to 128.2000,
                37.5600 to 128.4000, 37.6800 to 128.7000, 37.7600 to 128.8800, 37.7724 to 128.9472) +
                back(37.9000 to 128.8000, 38.0500 to 128.6200, 37.9300 to 128.3000, 37.7400 to 127.9500, 37.6600 to 127.5500, 37.6200 to 127.2300),
            "2025-08-14"
        ),
        Trip(
            "changwon", "창원 여행", "2025년 8월 4일", "1박 2일", "소나기 · 31°C", 365, "4시간 20분", 4,
            listOf(seoul, 37.4700 to 127.0380, 37.2820 to 127.1040, 37.2960 to 127.6760, 36.9900 to 127.9000,
                36.7000 to 128.1200, 36.4100 to 128.1600, 36.2500 to 128.3000, 35.9200 to 128.2800, 35.7000 to 128.4500,
                35.3500 to 128.6000, 35.2280 to 128.6810) +
                back(35.5000 to 128.7500, 35.8700 to 128.6000, 36.1300 to 128.3500, 36.3500 to 127.4500, 36.8000 to 127.1500, 37.2500 to 127.1000),
            "2025-08-04"
        ),
        // ── 더 넣은 예시 여행 (계절·해를 다양하게) ──
        Trip(
            "gapyeong", "가평 여행", "2026년 5월 5일", "당일치기", "맑음 · 21°C", 128, "2시간 40분", 5,
            listOf(seoul, 37.5800 to 127.0500, 37.6200 to 127.2300, 37.6640 to 127.4220, 37.7300 to 127.4800, 37.7900 to 127.5260) +
                back(37.8250 to 127.5100, 37.7000 to 127.5300, 37.6400 to 127.4800, 37.5800 to 127.3300, 37.5500 to 127.2000),
            "2026-05-05"
        ),
        Trip(
            "jeonju", "전주 여행", "2026년 2월 17일", "1박 2일", "눈 · -2°C", 436, "5시간 30분", 5,
            listOf(seoul, 37.4700 to 127.0380, 37.2820 to 127.1040, 36.9900 to 127.1400, 36.8100 to 127.1500,
                36.5700 to 127.1900, 36.2100 to 127.1000, 35.9800 to 127.0500, 35.8150 to 127.1530) +
                back(35.9500 to 127.2500, 36.3500 to 127.4000, 36.6400 to 127.4500, 37.0000 to 127.4000, 37.3000 to 127.3000, 37.5000 to 127.1500),
            "2026-02-17"
        ),
        Trip(
            // 시연 대본의 '작년 그 여행' — 가는 길에 천안삼거리휴게소(e0)에 들렀고, 올해 그 휴게소를 다시 지난다
            "gyeongju", "경주 여행", "2025년 10월 4일", "2박 3일", "맑음 · 22°C", 694, "8시간 10분", 6,
            listOf(seoul, 37.4700 to 127.0380, 37.2820 to 127.1040, 36.8000 to 127.1500, 36.3500 to 127.4500,
                36.2500 to 128.2700, 36.1300 to 128.3500, 35.8700 to 128.6000, 35.9000 to 129.0000, 35.8380 to 129.2120) +
                back(35.7900 to 129.3320, 36.0000 to 128.9500, 36.5600 to 128.7300, 36.8200 to 128.6200, 37.1300 to 128.2000,
                    37.3400 to 127.9200, 37.4000 to 127.5000),
            "2025-10-04",
            riders = listOf("아빠", "엄마", "아들", "딸", "할머니", "할아버지")
        ),
        Trip(
            "taean", "태안 여행", "2024년 7월 27일", "1박 2일", "소나기 · 30°C", 334, "4시간 40분", 4,
            listOf(seoul, 37.4800 to 126.8800, 37.3000 to 126.8300, 37.0000 to 126.8000, 36.9580 to 126.7800,
                36.7800 to 126.6000, 36.7500 to 126.4500, 36.7300 to 126.3500, 36.7870 to 126.1430) +
                back(36.7520 to 126.2980, 36.9000 to 126.6000, 36.8000 to 127.0000, 37.0000 to 127.1000, 37.2800 to 127.1000),
            "2024-07-27"
        )
    ).sortedByDescending { it.sortKey }

    private val seedSpots: List<Spot> = listOf(
        // 부산 여행
        Spot("b1", "busan", SpotKind.REST, "칠곡휴게소", 35.9847, 128.4284, "9월 3일 오전 10:50", "할머니랑 처음 같이 온 장거리. 호두과자는 이번에도 필수", R.drawable.demo_rest_grandma),
        Spot("b2", "busan", SpotKind.SIGHT, "해운대 해수욕장", 35.1587, 129.1604, "9월 3일 오후 2:30", "파도가 생각보다 세서 발만 담갔다", photo = R.drawable.spot_b2),
        Spot("b3", "busan", SpotKind.FOOD, "돼지국밥집", 35.1153, 129.0408, "9월 3일 오후 7:10", "국물이 진했다. 아이들도 한 그릇씩 다 비움", rating = 5, photo = R.drawable.spot_b3),
        Spot("b4", "busan", SpotKind.SIGHT, "감천문화마을", 35.0975, 129.0106, "9월 4일 오전 11:00", "계단이 많아서 아들은 아빠 등에 업혀 다님", photo = R.drawable.spot_b4),
        Spot("b5", "busan", SpotKind.SIGHT, "광안대교 야경 드라이브", 35.1532, 129.1187, "9월 4일 오후 8:20", "돌아오는 길, 노을이 다리 위로 지던 순간", R.drawable.demo_sunset),
        // 강릉 여행
        Spot("g1", "gangneung", SpotKind.REST, "덕평휴게소", 37.2405, 127.3744, "8월 14일 오전 9:40", "출발 1시간 만에 첫 휴식. 호두과자 한 봉지", R.drawable.demo_rest),
        Spot("g2", "gangneung", SpotKind.SIGHT, "안목해변", 37.7724, 128.9472, "8월 14일 오후 1:30", "비 그친 뒤 바다 냄새가 진했던 날", R.drawable.demo_beach),
        Spot("g3", "gangneung", SpotKind.FOOD, "초당순두부", 37.7910, 128.9150, "8월 14일 오후 6:00", "순두부 젤라또까지 먹고 나옴", rating = 4, photo = R.drawable.spot_g3),
        // 창원 여행
        Spot("c1", "changwon", SpotKind.REST, "문경휴게소", 36.7010, 128.1113, "8월 4일 오전 11:20", "갑자기 소나기. 와이퍼가 제일 빨리 돌던 날", R.drawable.demo_rain),
        Spot("c2", "changwon", SpotKind.FOOD, "마산 아구찜 골목", 35.2120, 128.5780, "8월 4일 오후 6:30", "맵다고 하면서 밥 두 공기", rating = 4, photo = R.drawable.spot_c2),
        Spot("c3", "changwon", SpotKind.SIGHT, "용지호수공원", 35.2260, 128.6840, "8월 5일 오전 10:00", "아침 산책. 가족사진 한 장 남김", R.drawable.demo_family),
        Spot("c4", "changwon", SpotKind.SIGHT, "진해 경화역", 35.1530, 128.6920, "8월 5일 오후 2:00", "벚꽃 철에 다시 오자고 약속", photo = R.drawable.spot_c4),
        // 가평 여행
        Spot("k1", "gapyeong", SpotKind.REST, "가평휴게소", 37.6494, 127.4299, "5월 5일 오전 9:50", "어린이날이라 휴게소부터 북적북적. 핫도그 두 개", photo = R.drawable.spot_k1),
        Spot("k2", "gapyeong", SpotKind.SIGHT, "남이섬", 37.7910, 127.5250, "5월 5일 오전 11:30", "자전거 타다가 딸이 처음으로 혼자 달림", photo = R.drawable.spot_k2),
        Spot("k3", "gapyeong", SpotKind.FOOD, "가평 잣두부집", 37.8250, 127.5100, "5월 5일 오후 1:40", "잣두부 정식. 할아버지가 제일 좋아하심", rating = 4, photo = R.drawable.spot_k3),
        // 전주 여행
        Spot("j1", "jeonju", SpotKind.REST, "정안알밤휴게소", 36.5736, 127.1140, "2월 17일 오전 10:20", "눈이 펑펑. 천천히 가자고 알밤빵 사 먹음", photo = R.drawable.spot_j1),
        Spot("j2", "jeonju", SpotKind.SIGHT, "전주 한옥마을", 35.8150, 127.1530, "2월 17일 오후 2:00", "한복 입고 골목 사진. 손이 꽁꽁 얼었다", photo = R.drawable.spot_j2),
        Spot("j3", "jeonju", SpotKind.FOOD, "전주 비빔밥집", 35.8180, 127.1460, "2월 17일 오후 6:30", "놋그릇 비빔밥. 아들은 고추장 빼고", rating = 5, photo = R.drawable.spot_j3),
        // 경주 여행 (작년) — e0 천안삼거리휴게소가 올해 다시 지나는 그 휴게소
        Spot("e0", "gyeongju", SpotKind.REST, "천안삼거리휴게소", 36.7947, 127.1629, "10월 4일 오전 10:40", "호두과자가 너무 맛있어서 내년에 또 사 먹기로. 아들은 인형뽑기에서 강아지 인형 득템", photo = R.drawable.spot_e0),
        Spot("e1", "gyeongju", SpotKind.REST, "김천휴게소", 36.1614, 128.2589, "10월 4일 오후 12:30", "두 번째 휴식. 다리 펴고 커피 한 잔", photo = R.drawable.spot_e1),
        Spot("e2", "gyeongju", SpotKind.FOOD, "황리단길 쌈밥집", 35.8370, 129.2100, "10월 4일 오후 1:30", "쌈밥 한 상. 다 같이 배 두드리며 나옴", rating = 5, photo = R.drawable.spot_e2),
        Spot("e3", "gyeongju", SpotKind.SIGHT, "동궁과 월지", 35.8340, 129.2270, "10월 4일 오후 7:30", "밤 연못에 비친 불빛. 할머니가 한참 서 계셨다", photo = R.drawable.spot_e3),
        Spot("e4", "gyeongju", SpotKind.SIGHT, "불국사", 35.7900, 129.3320, "10월 5일 오전 10:00", "할머니가 신혼여행 때 왔던 곳이래", photo = R.drawable.spot_e4),
        // 태안 여행
        Spot("t1", "taean", SpotKind.REST, "행담도휴게소", 36.9423, 126.7899, "7월 27일 오전 9:30", "서해대교 한가운데 휴게소. 바다 위라 다들 신기해함", photo = R.drawable.spot_t1),
        Spot("t2", "taean", SpotKind.SIGHT, "만리포해수욕장", 36.7870, 126.1430, "7월 27일 오후 1:00", "갑자기 소나기. 그래도 모래성은 끝까지 완성", photo = R.drawable.spot_t2),
        Spot("t3", "taean", SpotKind.FOOD, "태안 꽃게탕집", 36.7520, 126.2980, "7월 27일 오후 6:20", "꽃게탕 국물에 밥 비빔. 아빠 최애", rating = 4, photo = R.drawable.spot_t3)
    )

    /**
     * 화면에 보이는 목록. 시연 중 여행이 끝나면 여기에 새 기록이 더해진다.
     * (Compose 가 변화를 알아챌 수 있도록 상태 목록으로 둔다)
     */
    val trips = mutableStateListOf<Trip>().also { it.addAll(seedTrips) }
    val spots = mutableStateListOf<Spot>().also { it.addAll(seedSpots) }

    /**
     * 실제 고속도로 경로로 바꾸기: assets/trip_routes.json (scripts/make_trip_routes.sh 로 만든 파일)이 있으면
     * 각 여행의 직선 경로를 그 경로로 갈아 끼운다. 파일이 없으면 지금 직선 경로 그대로.
     */
    /** 여행이 아닌 경로도 같은 파일에 들어 있다 (예: "scenario" = 시연용 가는 길) */
    private val extraRoutes = mutableMapOf<String, List<Pair<Double, Double>>>()

    fun extraRoute(key: String): List<Pair<Double, Double>> = extraRoutes[key].orEmpty()

    fun loadDetailedRoutes(context: Context) {
        val o = try { JSONObject(context.assets.open("trip_routes.json").bufferedReader().use { it.readText() }) } catch (_: Exception) { return }
        o.keys().forEach { k ->
            if (trips.none { it.id == k }) {
                val a = o.optJSONArray(k) ?: return@forEach
                val pts = (0 until a.length()).map { i -> a.getJSONArray(i).let { it.getDouble(0) to it.getDouble(1) } }
                if (pts.size >= 2) extraRoutes[k] = pts
            }
        }
        for (i in trips.indices) {
            val t = trips[i]
            val a = o.optJSONArray(t.id) ?: continue
            val pts = (0 until a.length()).map { k -> a.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } }
            if (pts.size < 2) continue
            // 카드의 거리·운전 시간도 실제 경로에 맞춘다 (직선 경로로 적어 둔 숫자는 실제보다 작다)
            val km = (1 until pts.size).sumOf { k ->
                LocationTracker.distanceM(pts[k - 1].first, pts[k - 1].second, pts[k].first, pts[k].second).toDouble()
            } / 1000.0
            val min = ((km / 82.0 * 60.0) / 10.0).toInt() * 10          // 평균 시속 82km, 10분 단위
            trips[i] = t.copy(route = pts, distanceKm = (km + 0.5).toInt(), driveTime = if (min < 60) "${min}분" else "${min / 60}시간 ${min % 60}분")
        }
        // 휴게소 핀은 실제 고속도로 위로 옮긴다 (예시 좌표가 대략이라 길에서 떨어져 보이므로).
        // 가는 길(집 ~ 목적지) 안에서만 찾는다 — 돌아오는 길 쪽으로 붙지 않게.
        for (k in spots.indices) {
            val sp = spots[k]
            if (sp.kind != SpotKind.REST) continue
            val t = trip(sp.tripId) ?: continue
            val r = t.route
            val dest = destinationIndex(t)
            val best = (0..dest).minByOrNull { j -> LocationTracker.distanceM(r[j].first, r[j].second, sp.lat, sp.lng) } ?: continue
            if (LocationTracker.distanceM(r[best].first, r[best].second, sp.lat, sp.lng) < 15_000f) {
                spots[k] = sp.copy(lat = r[best].first, lng = r[best].second)
            }
        }
    }

    /** 경로에서 목적지(집에서 가장 먼 점)의 위치 */
    private fun destinationIndex(t: Trip): Int {
        val h = t.route.first()
        return t.route.indices.maxByOrNull { LocationTracker.distanceM(h.first, h.second, t.route[it].first, t.route[it].second) } ?: 0
    }

    /** 여행과 상관없이 따로 남긴 곳의 tripId */
    const val SOLO = "solo"

    /** 저장해 둔 실제 여행(카드)과 들른 곳을 불러온다 (이미 있으면 건너뜀), 최신순 정렬 */
    fun restoreTrips(saved: List<Trip>, savedSpots: List<Spot>) {
        saved.forEach { t -> if (trips.none { it.id == t.id }) trips.add(t) }
        val sorted = trips.sortedByDescending { it.sortKey }
        trips.clear(); trips.addAll(sorted)
        savedSpots.forEach { s -> if (spots.none { it.id == s.id }) spots.add(s) }
    }

    /** 따로 남긴 곳 하나를 맨 앞에 더한다 */
    fun addSpot(s: Spot) {
        spots.removeAll { it.id == s.id }
        spots.add(0, s)
    }

    /** 저장해 둔 "따로 남긴 곳"을 불러온다 (이미 있으면 건너뜀) */
    fun restore(list: List<Spot>) {
        list.reversed().forEach { s -> if (spots.none { it.id == s.id }) spots.add(0, s) }
    }

    /** 방금 끝난 여행을 기록에 더한다 (맨 위로) */
    fun addTrip(t: Trip, newSpots: List<Spot>) {
        trips.removeAll { it.id == t.id }
        spots.removeAll { it.tripId == t.id }
        trips.add(0, t)
        spots.addAll(newSpots)
    }

    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }

    /**
     * 여행의 목적지: 들른 곳 중 집(경로 첫 점)에서 가장 먼 곳. 들른 곳이 없으면 경로에서 가장 먼 점.
     * (돌아오는 길이 더 멀리 돌아올 수도 있어서 경로만 보면 틀린다 — 예: 속초 갔다가 강릉 거쳐 옴)
     */
    fun destinationOf(t: Trip): Pair<Double, Double> {
        val h = t.route.first()
        spotsOf(t.id).maxByOrNull { LocationTracker.distanceM(h.first, h.second, it.lat, it.lng) }?.let { return it.lat to it.lng }
        return t.route.maxByOrNull { LocationTracker.distanceM(h.first, h.second, it.first, it.second) } ?: h
    }
    fun spotsOf(tripId: String): List<Spot> = spots.filter { it.tripId == tripId }
    fun spotsOf(kind: SpotKind): List<Spot> = spots.filter { it.kind == kind }

    /**
     * 들른 곳마다 경로를 끊을 위치. 방문 순서대로, 앞 장소 다음부터 찾는다
     * (왕복 경로라 돌아오는 길이 가까이 지나가도 엉뚱한 쪽에 붙지 않게). 30km 넘게 떨어지면 -1.
     */
    private fun cutsOf(t: Trip): List<Int> {
        val r = t.route
        var from = 0
        return spotsOf(t.id).map { s ->
            fun dist(i: Int) = LocationTracker.distanceM(r[i].first, r[i].second, s.lat, s.lng)
            // 처음으로 300m 안에 들어온 곳 (같은 곳을 나중에 한 번 더 지나가도 처음 들른 때로), 없으면 가장 가까운 곳
            val first = (from until r.size).firstOrNull { dist(it) < 300f }
            val best = first ?: (from until r.size).minByOrNull { dist(it) } ?: from
            if (dist(best) < 30_000f) { from = best; best } else -1
        }
    }

    /** 여행 경로를 들른 곳마다 끊어서 구간(leg) 목록으로 */
    fun legs(t: Trip): List<List<Pair<Double, Double>>> {
        val cuts = cutsOf(t).filter { it in 1 until t.route.size - 1 }.distinct().sorted()
        val bounds = listOf(0) + cuts + listOf(t.route.size - 1)
        return (0 until bounds.size - 1).map { k -> t.route.subList(bounds[k], bounds[k + 1] + 1) }
    }

    /** 들른 곳마다 "그곳까지 오는 구간"의 번호 → 핀·목록 색을 선 색과 맞춘다 */
    fun stopLegIndex(t: Trip): List<Int> {
        val raw = cutsOf(t)
        val cuts = raw.filter { it in 1 until t.route.size - 1 }.distinct().sorted()
        return raw.mapIndexed { i, c -> cuts.indexOf(c).takeIf { it >= 0 } ?: i }
    }
}
