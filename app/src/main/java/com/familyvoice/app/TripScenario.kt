package com.familyvoice.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 시연용 가상 여행 — 집(서울역) → 천안삼거리휴게소 → 대전 성심당 → 충주호 → 집
 *
 * 진행은 **기어**로만 한다. D 로 내리면 그 구간을 달리고(정해진 시간 동안), P 로 올리면 도착.
 * 실제 GPS 는 쓰지 않는다 — 에뮬레이터에서는 위치가 제때 들어오지 않아 음성 타이밍을 놓치기 때문.
 * 달리는 동안 위치를 일정한 속도로 넣어 주므로 지도에는 가는 길이 그대로 그려진다.
 */
object TripScenario {

    val home = TripPolicy.FIXED_HOME ?: (37.5547 to 126.9707)   // 집 (서울역)
    val cheonan = 36.7947 to 127.1629      // 천안삼거리휴게소 — 작년에 목소리를 남긴 곳 (Journal 의 e1 과 같아야 한다)
    val daejeon = 36.3277 to 127.4274         // 대전 성심당 (점심)
    val chungju = 36.9880 to 128.0010         // 충주호

    /** 각 구간을 달리는 데 걸리는 시간 (밀리초). 시연이라 짧게 */
    const val LEG1_MS = 15_000L             // 집 → 천안휴게소 3km 앞까지 (쭉 달리는 구간)
    // 마지막 3km — 소리가 흐르는 동안 천천히 (안내 3.7 + 작년 목소리 9.3 + "들렀다 갈까요?" 1.4 + 가족 반응 6.8 = 21.2초)
    const val LEG1_SLOW_MS = 21_000L
    const val SLOW_FROM_M = 3_000f          // 휴게소 몇 m 앞부터 느려지는지
    const val LEG2_MS = 15_000L             // 천안휴게소 → 대전 성심당
    const val LEG3_MS = 15_000L             // 대전 → 충주호
    const val LEG_HOME_MS = 12_000L         // 충주호 → 집

    /** 집 → 천안 구간에서 작년 목소리를 꺼낼 시점 (구간 시작부터 몇 밀리초 뒤) */
    const val LAST_YEAR_AT_MS = 6_000L

    /**
     * 오늘 가는 길 (경부선으로 천안·대전, 돌아올 때는 충주를 거쳐 중부내륙으로).
     * 갈 때와 올 때 길이 달라야 돌아오는 길에 천안휴게소를 다시 지나지 않는다.
     */
    private val realRoute: List<Pair<Double, Double>>
        get() = Journal.extraRoute("scenario").takeIf { it.size >= 10 } ?: fallbackRoute

    /** 실제 도로 경로가 없을 때 쓰는 대략 경로 (scripts/make_scenario_route.py 를 돌리면 안 쓰임) */
    private val fallbackRoute: List<Pair<Double, Double>> = listOf(
        home,
        37.4700 to 127.0380, 37.2820 to 127.1040, 37.0700 to 127.1300, 36.9480 to 127.1430,
        36.7947 to 127.1629,                                        // 천안삼거리휴게소
        36.6400 to 127.3100, 36.4700 to 127.4000,
        36.3277 to 127.4274,                                        // 대전 성심당
        36.5000 to 127.4300, 36.6600 to 127.5600, 36.8200 to 127.7900, 36.9700 to 127.9300,
        36.9880 to 128.0010,                                        // 충주호
        37.0300 to 127.9000, 37.1500 to 127.7000, 37.2700 to 127.5000, 37.4000 to 127.3000, 37.4800 to 127.1500,
        home
    )

    /** 경로에서 그 지점과 가장 가까운 점의 번호 */
    private fun nearestIndex(route: List<Pair<Double, Double>>, p: Pair<Double, Double>, from: Int = 0): Int =
        (from until route.size).minByOrNull {
            LocationTracker.distanceM(route[it].first, route[it].second, p.first, p.second)
        } ?: from

    /** from → to 구간. 실제 도로 경로가 있으면 그 사이를 잘라 쓴다 */
    private fun leg(from: Pair<Double, Double>, to: Pair<Double, Double>, backHalf: Boolean = false): List<Pair<Double, Double>> {
        val r = realRoute
        if (r.size >= 10) {
            // 왕복 경로라서, 돌아오는 길은 가장 먼 지점 뒤쪽에서 찾는다
            val far = nearestIndex(r, r.maxByOrNull { LocationTracker.distanceM(home.first, home.second, it.first, it.second) } ?: r.last())
            val a = if (backHalf) nearestIndex(r, from, far) else nearestIndex(r, from)
            val b = if (backHalf) nearestIndex(r, to, a) else nearestIndex(r, to, a)
            if (b > a + 1) {
                val slice = r.subList(a, b + 1).toList() + to
                // 실제 도로 경로는 이미 400m 간격이라 그대로 쓰고,
                // 대략 경로(점이 몇 개뿐)일 때만 잘게 쪼갠다 — "휴게소 3km 앞"을 집으려면 촘촘해야 한다
                return if (slice.size >= 40) slice else straight(slice, 20)
            }
        }
        return straight(listOf(from, to), 60)
    }

    private val legOut1All: List<Pair<Double, Double>> get() = leg(home, cheonan)

    /** 집 → 천안삼거리휴게소 3km 앞 (빠르게) */
    val legOut1: List<Pair<Double, Double>>
        get() {
            val all = legOut1All
            val cut = all.indexOfFirst { LocationTracker.distanceM(it.first, it.second, cheonan.first, cheonan.second) <= SLOW_FROM_M }
            return if (cut <= 1) all else all.subList(0, cut).toList()
        }

    /** 마지막 3km (천천히 — 작년 목소리) */
    val legOut1Slow: List<Pair<Double, Double>>
        get() {
            val all = legOut1All
            val cut = all.indexOfFirst { LocationTracker.distanceM(it.first, it.second, cheonan.first, cheonan.second) <= SLOW_FROM_M }
            return if (cut <= 1) listOf(all.last()) else straight(all.subList(cut, all.size).toList(), 6)
        }
    val legOut2: List<Pair<Double, Double>> get() = leg(cheonan, daejeon)
    val legOut3: List<Pair<Double, Double>> get() = leg(daejeon, chungju)
    val legHome: List<Pair<Double, Double>> get() = leg(chungju, home, backHalf = true)

    /** 전체 경로 (여행 기록에 저장할 선) */
    val fullRoute: List<Pair<Double, Double>>
        get() = legOut1All + legOut2.drop(1) + legOut3.drop(1) + legHome.drop(1)

    /** 점들 사이를 잘게 쪼개 지도에 선이 그려지도록 한다 */
    private fun straight(points: List<Pair<Double, Double>>, per: Int = 6): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            for (k in 0 until per) {
                val t = k.toDouble() / per
                out += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        out += points.last()
        return out
    }

    /** 경로 길이 (km) */
    fun distanceKm(route: List<Pair<Double, Double>>): Int {
        var m = 0f
        for (i in 0 until route.size - 1) {
            m += LocationTracker.distanceM(route[i].first, route[i].second, route[i + 1].first, route[i + 1].second)
        }
        return (m / 1000f).toInt()
    }

    /** 이번 여행을 기록 한 건으로 만든다 (여행 탭에 쌓인다) */
    fun buildTrip(people: Int, rainy: Boolean): Pair<Trip, List<Spot>> {
        val now = Date()
        val id = "live-" + SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(now)
        val dateLabel = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(now)
        val sortKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(now)
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        val temp = when (month) {
            12, 1, 2 -> 3; 3, 4 -> 14; 5, 6 -> 23; 7, 8 -> 29; 9, 10 -> 19; else -> 11
        }
        val weather = if (rainy) "비 · ${temp - 2}°C" else "맑음 · $temp°C"

        val route = fullRoute
        val km = distanceKm(route)
        val hours = km / 85.0
        val driveTime = "${hours.toInt()}시간 ${((hours - hours.toInt()) * 60).toInt()}분"

        val trip = Trip(
            id = id,
            title = "대전·충주 여행",
            dateLabel = dateLabel,
            nights = "당일치기",
            weather = weather,
            distanceKm = km,
            driveTime = driveTime,
            people = people.coerceAtLeast(1),
            route = route,
            sortKey = sortKey
        )

        val fmt = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)
        val t0 = now.time
        val spots = listOf(
            Spot("$id-1", id, SpotKind.REST, "천안삼거리휴게소", cheonan.first, cheonan.second,
                fmt.format(Date(t0 - 5 * 3600_000L)), ""),
            Spot("$id-2", id, SpotKind.FOOD, "대전 성심당", daejeon.first, daejeon.second,
                fmt.format(Date(t0 - 3 * 3600_000L)), ""),
            Spot("$id-3", id, SpotKind.SIGHT, "충주호", chungju.first, chungju.second,
                fmt.format(Date(t0 - 2 * 3600_000L)), "")
        )
        return trip to spots
    }
}

/** 어떤 곳에 도착했을 때 뜨는 카드 (차가 서 있을 때만) */
@Composable
fun ArrivalCard(place: String, line: String, onClose: () -> Unit) = FvOverlay {
    FvCard(600.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("도착했어요", fontFamily = Pen, fontSize = 30.sp, color = Fv.Blue)
            Text(place, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Fv.Ink)
            Text(line, fontSize = 18.sp, color = Fv.Sub)
        }
        FvButton("확인", Modifier.fillMaxWidth(), onClick = onClose)
    }
}

/**
 * 예전 여행에서 들렀던 곳을 다시 지날 때 뜨는 카드.
 * 여행 탭에 쌓여 있는 지난 기록(Spot)을 근거로 말을 건다. 사진이 있으면 폴라로이드로.
 */
@Composable
fun PastSpotCard(spot: Spot, tripTitle: String, tripDate: String, onClose: () -> Unit) = FvOverlay {
    Row(
        Modifier.width(900.dp).shadow(28.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp)).padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (spot.photo != null) {
            Box(Modifier.rotate(-3f).shadow(10.dp).background(Fv.Paper).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 34.dp)) {
                SpotPhoto(spot, Modifier.size(260.dp, 220.dp), 0.dp, 40.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("여기, 와 본 곳이에요", fontFamily = Pen, fontSize = 32.sp, color = Fv.Blue)
            Text(spot.name, fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Fv.Ink)
            Text(listOf(tripDate, tripTitle).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 17.sp, color = Fv.Sub)
            if (spot.note.isNotEmpty()) Text("“${spot.note}”", fontFamily = Pen, fontSize = 28.sp, lineHeight = 37.sp, color = Color(0xFF34404B))
            FvButton("그랬지", onClick = onClose)
        }
    }
}
