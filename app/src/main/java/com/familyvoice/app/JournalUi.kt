package com.familyvoice.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ───────────── 탭: 전체 · 여행 · 음식점 · 관광지 · 휴게소 ───────────── */

val TABS = listOf("전체", "여행", "음식점", "관광지", "휴게소")

fun kindOfTab(tab: Int): SpotKind? = when (tab) {
    2 -> SpotKind.FOOD; 3 -> SpotKind.SIGHT; 4 -> SpotKind.REST; else -> null
}

/** 지도에 얹을 여행·장소 정보 (전체 탭이 아닐 때) */
data class MapJournal(
    val faint: List<List<Pair<Double, Double>>> = emptyList(),   // 흐린 선 (다른 여행들)
    val legs: List<List<Pair<Double, Double>>> = emptyList(),    // 고른 여행: 들른 곳마다 색이 바뀌는 선
    val spots: List<Spot> = emptyList(),                         // 핀
    val numbered: Boolean = false,                               // 핀에 순서 번호
    val legOf: Map<String, Int> = emptyMap(),                    // 핀 id → 그 핀까지 오는 구간 번호 (핀 색 = 선 색)
    val selectedSpot: String? = null,
    val labels: List<Trip> = emptyList(),                        // 여행 목적지 이름표
    val focus: List<Pair<Double, Double>>? = null,               // 카메라를 맞출 점들
    val key: String = ""
)

fun journalFor(tab: Int, tripId: String?, spotId: String?, region: String? = null): MapJournal {
    if (tab == 1) {
        val t = tripId?.let { Journal.trip(it) }
        // 여행 목록: 선은 그리지 않고 목적지 이름표만. 여행을 누르면 그 여행의 왕복 경로가 그려진다
        // 지역을 고르면 그 도 여행만, 그 도에 맞게 확대
        if (t == null) {
            val trips = if (region == null) Journal.trips.toList() else Journal.trips.filter { regionOfTrip(it) == region }
            val focus = region?.let { REGION_BOUNDS[it] } ?: trips.flatMap { it.route }
            return MapJournal(labels = trips, focus = focus, key = "trips-$region-${trips.size}")
        }
        return MapJournal(
            legs = Journal.legs(t), spots = Journal.spotsOf(t.id), numbered = true,
            legOf = Journal.spotsOf(t.id).zip(Journal.stopLegIndex(t)).associate { (s, k) -> s.id to k },
            selectedSpot = spotId, focus = t.route, key = "trip-${t.id}"
        )
    }
    if (tab == 0) {
        // 전체: 지금까지 들른 곳 전부를 사진 핀으로. 집까지 포함해 전국이 보이게 맞춘다
        val all = Journal.spots.toList()
        val focus = all.map { it.lat to it.lng } + listOfNotNull(TripPolicy.FIXED_HOME)
        return MapJournal(spots = all, selectedSpot = spotId, focus = focus, key = "all-${all.size}")
    }
    val k = kindOfTab(tab) ?: return MapJournal()
    val all = Journal.spotsOf(k)
    val list = if (region == null) all else all.filter { regionOf(it.lat, it.lng) == region }
    // 음식점·관광지·휴게소: 경로 선 없이 핀만. 지역을 고르면 그 도에 딱 맞게 확대한다
    // (key 에 개수·지역을 넣어, 새로 남기거나 지역을 바꾸면 지도도 다시 그린다)
    val focus = region?.let { REGION_BOUNDS[it] } ?: list.map { it.lat to it.lng }
    return MapJournal(spots = list, selectedSpot = spotId, focus = focus, key = "kind-$tab-${list.size}-$region")
}

/** 지도 구간 색 (LEG_COLORS) 을 Compose 색으로 — 핀 번호와 목록 번호를 선 색과 맞출 때 */
fun legColor(k: Int): Color = Color(android.graphics.Color.parseColor(LEG_COLORS[k.mod(LEG_COLORS.size)]))

/* ───────────── 지역 (도 단위) ───────────── */

/** 지역 버튼을 눌렀을 때 지도를 맞출 범위 (남, 서, 북, 동). 광역시는 둘러싼 도에 넣었다 */
val REGION_BOUNDS: Map<String, List<Pair<Double, Double>>> = mapOf(
    "서울" to listOf(37.42 to 126.76, 37.70 to 127.19),
    "경기" to listOf(36.90 to 126.40, 38.30 to 127.85),
    "강원" to listOf(37.00 to 127.05, 38.60 to 129.40),
    "충북" to listOf(36.00 to 127.25, 37.25 to 128.65),
    "충남" to listOf(35.95 to 125.90, 37.05 to 127.65),
    "전북" to listOf(35.30 to 126.35, 36.15 to 127.90),
    "전남" to listOf(33.90 to 125.90, 35.50 to 127.90),
    "경북" to listOf(35.55 to 127.80, 37.55 to 129.60),
    "경남" to listOf(34.50 to 127.55, 35.95 to 129.50),
    "제주" to listOf(33.10 to 126.10, 33.60 to 127.00)
)

/** 도마다 대표 도시 좌표. 가장 가까운 곳의 도로 정한다 (인터넷 없이) */
private val REGION_ANCHORS = listOf(
    "경기" to (37.56 to 126.98), "경기" to (37.46 to 126.70), "경기" to (37.40 to 127.20), "경기" to (37.83 to 127.51),
    "경기" to (36.99 to 127.11), "경기" to (37.75 to 127.05), "경기" to (37.28 to 127.45), "경기" to (37.30 to 127.64),
    "강원" to (37.88 to 127.73), "강원" to (37.34 to 127.92), "강원" to (37.75 to 128.88), "강원" to (38.20 to 128.59),
    "강원" to (37.75 to 128.30), "강원" to (37.37 to 128.65),
    "충북" to (36.64 to 127.49), "충북" to (36.99 to 127.93), "충북" to (36.30 to 127.57),
    "충남" to (36.66 to 126.67), "충남" to (36.81 to 127.11), "충남" to (36.35 to 127.38), "충남" to (36.45 to 127.12),
    "충남" to (36.89 to 126.63), "충남" to (36.75 to 126.30), "충남" to (36.19 to 127.10),
    "전북" to (35.82 to 127.15), "전북" to (35.97 to 126.74), "전북" to (35.42 to 127.39),
    "전남" to (34.82 to 126.46), "전남" to (35.16 to 126.85), "전남" to (34.76 to 127.66),
    "경북" to (36.57 to 128.73), "경북" to (35.87 to 128.60), "경북" to (36.02 to 129.34), "경북" to (36.12 to 128.34),
    "경북" to (35.85 to 129.22), "경북" to (36.41 to 128.16), "경북" to (36.59 to 128.19), "경북" to (35.99 to 128.40),
    "경남" to (35.23 to 128.68), "경남" to (35.18 to 129.08), "경남" to (35.54 to 129.31), "경남" to (35.18 to 128.11),
    "경남" to (35.50 to 128.75),
    "제주" to (33.40 to 126.55)
)

fun regionOf(lat: Double, lng: Double): String {
    // 서울은 경기와 따로: 서울 범위 안이면 서울 (인천은 경기에 넣는다)
    if (lat in 37.43..37.70 && lng in 126.80..127.18) return "서울"
    return REGION_ANCHORS.minByOrNull { (_, p) -> LocationTracker.distanceM(lat, lng, p.first, p.second) }?.first ?: ""
}

/** 여행의 지역 = 목적지(집에서 가장 먼 곳)가 있는 도 */
fun regionOfTrip(t: Trip): String = Journal.destinationOf(t).let { regionOf(it.first, it.second) }

/* ───────────── 왼쪽 패널 내용 ───────────── */

@Composable
fun JournalPanel(
    tab: Int, tripId: String?, spotId: String?,
    onTrip: (String) -> Unit, onBack: () -> Unit, onSpot: (String) -> Unit,
    onAdd: (SpotKind) -> Unit = {},
    region: String? = null,                  // 고른 지역 (null = 전체)
    onRegion: (String?) -> Unit = {}
) {
    val trip = tripId?.let { Journal.trip(it) }
    val kind = kindOfTab(tab)
    when {
        tab == 1 && trip == null -> {
            val all = Journal.trips.toList()
            val trips = if (region == null) all else all.filter { regionOfTrip(it) == region }
            PanelHeader("우리의 여행", "${trips.size}번의 여행 · 집 출발부터 도착까지")
            // 지역 버튼 (목적지 기준), 여행 많은 순서로
            val counts = all.groupingBy { regionOfTrip(it) }.eachCount().entries.sortedByDescending { it.value }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PickChip("전체", region == null, Fv.Blue) { onRegion(null) }
                counts.forEach { (r, _) -> PickChip(r, region == r, Fv.Blue) { onRegion(if (region == r) null else r) } }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                trips.forEachIndexed { i, t ->
                    val year = t.sortKey.take(4)
                    // 연도 제목도 세로선 위에 얹어서, 첫 여행부터 마지막 여행까지 선이 한 줄로 이어진다
                    if (i == 0 || trips[i - 1].sortKey.take(4) != year) YearRow(year, lineThrough = i > 0)
                    TimelineRow(t, topLine = i > 0, bottomLine = i < trips.lastIndex) { onTrip(t.id) }
                }
            }
        }
        tab == 1 && trip != null -> TripDetailPanel(trip, spotId, onBack, onSpot)
        kind != null -> {
            val all = Journal.spotsOf(kind)
            val list = if (region == null) all else all.filter { regionOf(it.lat, it.lng) == region }
            PanelHeader(kind.label, "${list.size}곳 · 여행에서 들른 곳과 따로 남긴 곳")
            // 지역 버튼: 장소가 있는 도만, 많은 순서로
            val counts = all.groupingBy { regionOf(it.lat, it.lng) }.eachCount().entries.sortedByDescending { it.value }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PickChip("전체", region == null, kind.color) { onRegion(null) }
                counts.forEach { (r, _) -> PickChip(r, region == r, kind.color) { onRegion(if (region == r) null else r) } }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddSpotCard(kind) { onAdd(kind) }
                list.forEach { s -> SpotCard(s, s.id == spotId) { onSpot(s.id) } }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, sub: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp, color = Fv.Ink)
    Text(sub, fontSize = 16.sp, color = Fv.Sub)
    Spacer(Modifier.height(4.dp))
}

/** 연도 제목 ("2025년") */
@Composable
private fun YearRow(year: String, lineThrough: Boolean) {
    Text("${year}년", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fv.Sub,
        modifier = Modifier.padding(top = if (lineThrough) 8.dp else 0.dp, bottom = 8.dp))
}

/** 여행 카드 한 장 (세로선 없이 카드만) */
@Composable
private fun TimelineRow(t: Trip, topLine: Boolean, bottomLine: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .shadow(3.dp, RoundedCornerShape(8.dp))
            .background(Fv.Paper, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("${t.dateLabel} · ${t.nights}", fontSize = 15.sp, color = Fv.Sub)
        Text(t.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Fv.Ink)
        Text("${t.distanceKm}km · ${t.weather.replace(" · ", " ")} · ${t.people}명", fontSize = 15.sp, color = Fv.Sub,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TripDetailPanel(t: Trip, spotId: String?, onBack: () -> Unit, onSpot: (String) -> Unit) {
    val stops = Journal.spotsOf(t.id)
    val legIdx = Journal.stopLegIndex(t)
    Text("‹ 여행 목록", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Fv.Blue,
        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp))
    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp, color = Fv.Ink)
    Text("${t.dateLabel} · ${t.nights}", fontSize = 16.sp, color = Fv.Sub)
    if (t.summary.isNotEmpty()) {
        Text("“${t.summary}”", fontFamily = Pen, fontSize = 23.sp, lineHeight = 31.sp, color = Color(0xFF34404B))
        Text(DEMO_SUMMARY_LABEL, fontSize = 12.sp, color = Fv.Sub)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox("날씨", t.weather, Modifier.weight(1f))
        InfoBox("거리", "${t.distanceKm}km", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox("운전", t.driveTime, Modifier.weight(1f))
        InfoBox("함께", if (t.riders.isNotEmpty()) t.riders.joinToString(", ") else "${t.people}명", Modifier.weight(1f))
    }
    Text("지나온 길", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Fv.Ink, modifier = Modifier.padding(top = 4.dp))
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StopRow(null, "집 출발", "우리 집에서", Fv.Ink, false) {}
        stops.forEachIndexed { i, s ->
            StopRow("${i + 1}", s.name, "${s.kind.label} · ${s.time}", legColor(legIdx.getOrElse(i) { i }), s.id == spotId) { onSpot(s.id) }
        }
        StopRow(null, "집 도착", "돌아오는 길", Fv.Ink, false) {}
    }
}

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Fv.BlueSoft, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, fontSize = 13.sp, color = Fv.Sub)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Fv.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StopRow(num: String?, title: String, sub: String, color: Color, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (on) Fv.BlueSoft else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(num ?: "집", fontSize = if (num == null) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Fv.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 14.sp, color = Fv.Sub, maxLines = 1)
        }
    }
}

@Composable
private fun SpotCard(s: Spot, on: Boolean, onClick: () -> Unit) {
    val trip = Journal.trip(s.tripId)
    Row(
        Modifier.fillMaxWidth()
            .shadow(if (on) 10.dp else 3.dp, RoundedCornerShape(8.dp))
            .background(Fv.Paper, RoundedCornerShape(8.dp))
            .border(if (on) 2.dp else 0.dp, if (on) Fv.Blue else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotPhoto(s, Modifier.size(72.dp), 3.dp, 24.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Fv.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            s.rating?.let { Stars(it, 16.sp) }
            Text("${trip?.title ?: "따로 남긴 곳"} · ${s.time}" + if (s.voice != null) " · 목소리" else "", fontSize = 14.sp, color = Fv.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.note.isNotEmpty()) Text(s.note, fontFamily = Pen, fontSize = 21.sp, color = Color(0xFF34404B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 사진이 있으면 사진, 없으면 종류 색 카드 */
@Composable
fun SpotPhoto(s: Spot, modifier: Modifier, corner: Dp, labelSize: androidx.compose.ui.unit.TextUnit) {
    val shape = RoundedCornerShape(corner)
    if (s.photo != null) {
        Image(painterResource(s.photo), contentDescription = s.name, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        Box(modifier.background(s.kind.color, shape), contentAlignment = Alignment.Center) {
            Text(s.kind.short, fontFamily = Pen, fontSize = labelSize, color = Color.White)
        }
    }
}

/** 카테고리 탭 맨 위: "+ 음식점 남기기" */
@Composable
private fun AddSpotCard(kind: SpotKind, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .border(1.5.dp, kind.color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .background(Fv.Paper.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(kind.color, CircleShape), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column {
            Text("${kind.label} 남기기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fv.Ink)
            Text("지금 있는 곳을 따로 기록해요", fontSize = 14.sp, color = Fv.Sub)
        }
    }
}

/** 별점 (★ 채운 수 = 평점) */
@Composable
fun Stars(n: Int, size: androidx.compose.ui.unit.TextUnit) {
    Text("★".repeat(n.coerceIn(0, 5)) + "☆".repeat(5 - n.coerceIn(0, 5)), fontSize = size, color = Color(0xFFE0A33A))
}

/** 네비에서 가져온 장소 하나 (최근 목적지·즐겨찾기) */
data class PlacePick(val name: String, val address: String, val lat: Double, val lng: Double)

/**
 * 따로 남기기 입력 화면.
 * 위치: [지금 있는 곳] 또는 네비 최근 목적지·즐겨찾기 중 하나. 이름·한 줄 기록·평점.
 * 키보드에 가리지 않게 화면 위쪽에 붙이고, 버튼은 제목 옆에 둔다. 주행 중(P 아님)에는 저장이 잠긴다.
 */
@Composable
fun AddSpotOverlay(
    kind: SpotKind, initialName: String, parked: Boolean, picks: List<PlacePick>,
    onSave: (name: String, note: String, rating: Int?, pick: PlacePick?, voice: Long?) -> Unit, onCancel: () -> Unit,
    onRecord: ((Long?) -> Unit) -> Unit = { it(null) },
    onDiscardVoice: (Long) -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var pick by remember { mutableStateOf<PlacePick?>(null) }
    var voiceId by remember { mutableStateOf<Long?>(null) }
    var recState by remember { mutableStateOf(0) }     // 0 아직, 1 듣는 중, 2 남김, 3 잘 안 들림
    val ok = parked && name.isNotBlank() && recState != 1
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = true, onClick = {}),   // 뒤 화면 눌림 막기
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.padding(top = 24.dp).width(680.dp).shadow(24.dp, RoundedCornerShape(12.dp))
                .background(Fv.Paper, RoundedCornerShape(12.dp)).padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${kind.label} 남기기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Fv.Ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(52.dp).border(1.5.dp, Color(0xFFCFD6DD), RoundedCornerShape(26.dp))
                        .clickable { voiceId?.let(onDiscardVoice); onCancel() }.padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) { Text("취소", fontSize = 17.sp, color = Fv.Ink) }
                Box(
                    Modifier.height(52.dp).background(if (ok) kind.color else Fv.BlueLine, RoundedCornerShape(26.dp))
                        .clickable(enabled = ok) { onSave(name.trim(), note.trim(), rating.takeIf { it > 0 }, pick, voiceId) }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) { Text("남기기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            if (!parked) Text("안전을 위해 차를 세우면(P) 남길 수 있어요", fontSize = 15.sp, color = Color(0xFFC2604F))

            // 위치 고르기: 지금 있는 곳 / 네비 최근 목적지·즐겨찾기
            Text("어디를 남길까요?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Fv.Sub)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickChip("지금 있는 곳", pick == null, kind.color) { pick = null }
                picks.take(10).forEach { p ->
                    PickChip(p.name, pick == p, kind.color) { pick = p; name = p.name }
                }
            }
            Text(
                pick?.let { "${it.name} · ${it.address}" }
                    ?: if (picks.isEmpty()) "지금 차가 있는 곳에 남겨요 · 네비에 최근 목적지가 생기면 여기서 고를 수 있어요"
                    else "지금 차가 있는 곳에 남겨요",
                fontSize = 14.sp, color = Fv.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("한 줄 기록 (선택)") },
                    singleLine = true, modifier = Modifier.weight(1.4f))
            }
            // 말로 남기기: 삐 → Pleos 음성 인식(STT) → 글자로 저장 (앱이 직접 녹음하지 않음)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canRec = parked && recState != 1
                Row(
                    Modifier.height(52.dp)
                        .background(if (recState == 1) Color(0xFFC2604F) else if (canRec) Fv.BlueSoft else Fv.Chip, RoundedCornerShape(26.dp))
                        .clickable(enabled = canRec) {
                            voiceId?.let(onDiscardVoice); voiceId = null; recState = 1
                            onRecord { id -> voiceId = id; recState = if (id != null) 2 else 3 }
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MicIcon(if (recState == 1) Color.White else Fv.Blue, 20.dp)
                    Text(
                        when (recState) { 1 -> "듣고 있어요…"; 2 -> "다시 말하기"; else -> "말로 남기기" },
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (recState == 1) Color.White else Fv.Blue
                    )
                }
                Text(
                    when (recState) {
                        1 -> "말이 끝나면 글자로 남겨요"
                        2 -> "말로 남겼어요. 다음에 이 근처를 지나면 차가 읽어 줘요"
                        3 -> "잘 못 알아들었어요. 다시 해 볼까요?"
                        else -> "한 줄 기록 대신 말로 남겨도 돼요"
                    },
                    fontSize = 15.sp, color = Fv.Sub, maxLines = 2
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("평점", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Fv.Sub, modifier = Modifier.padding(end = 8.dp))
                (1..5).forEach { k ->
                    Text(
                        if (k <= rating) "★" else "☆", fontSize = 34.sp, color = Color(0xFFE0A33A),
                        modifier = Modifier.clickable { rating = if (rating == k) 0 else k }.padding(horizontal = 4.dp)
                    )
                }
                if (rating > 0) Text("${rating}점", fontSize = 16.sp, color = Fv.Sub, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun PickChip(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        color = if (on) Color.White else Fv.Ink, maxLines = 1,
        modifier = Modifier
            .background(if (on) color else Fv.Chip, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

/* ───────────── 오른쪽 아래 장소 카드 ───────────── */

@Composable
fun SpotDetail(s: Spot, modifier: Modifier, showTrip: Boolean, onTrip: () -> Unit, onClose: () -> Unit) {
    val trip = Journal.trip(s.tripId)
    Column(
        modifier.width(360.dp)
            .shadow(18.dp, RoundedCornerShape(4.dp))
            .background(Fv.Paper, RoundedCornerShape(4.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpotPhoto(s, Modifier.fillMaxWidth().height(190.dp), 2.dp, 48.sp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${s.kind.label} · ${trip?.title ?: "따로 남긴 곳"}", fontSize = 15.sp, color = s.kind.color, fontWeight = FontWeight.SemiBold)
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = Fv.Ink)
            s.rating?.let { Stars(it, 20.sp) }
            Text(s.time, fontSize = 15.sp, color = Fv.Sub)
            trip?.riders?.takeIf { it.isNotEmpty() }?.let { Text("함께: " + it.joinToString(", "), fontSize = 15.sp, color = Fv.Sub) }
            Text(s.note, fontFamily = Pen, fontSize = 27.sp, lineHeight = 36.sp, color = Color(0xFF34404B))
        }
        // ("이야기 듣기" 버튼은 11/12 에 뺐다: 메모는 화면에서 글자로 읽는다)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showTrip && trip != null) {
                Box(
                    Modifier.weight(1f).height(54.dp).background(Fv.Blue, RoundedCornerShape(27.dp)).clickable { onTrip() },
                    contentAlignment = Alignment.Center
                ) { Text("그날 여행 보기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1) }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(50.dp).border(1.5.dp, Color(0xFFCFD6DD), RoundedCornerShape(25.dp))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) { Text("닫기", fontSize = 17.sp, color = Fv.Ink, maxLines = 1) }
    }
}

/* ───────────── 지도 위 핀 ───────────── */

/** 위에서 톡 떨어지며 꽂히는 핀 */
@Composable
fun SpotPin(s: Spot, number: Int?, big: Boolean, color: Color? = null) {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    val sc = if (big) 1.25f else 1f
    Column(
        Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleX = drop.value * sc
            scaleY = drop.value * sc
            translationY = (1f - drop.value) * -80f
            alpha = drop.value.coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (big) {
            Text(
                s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fv.Ink,
                modifier = Modifier.padding(bottom = 4.dp).background(Fv.Panel, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(46.dp).shadow(6.dp, CircleShape).background(Color.White, CircleShape).padding(3.dp)
                    .clip(CircleShape).background(color ?: s.kind.color),
                contentAlignment = Alignment.Center
            ) {
                when {
                    number != null -> Text("$number", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    s.photo != null -> Image(painterResource(s.photo), contentDescription = s.name, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
                    else -> Text(s.kind.short, fontFamily = Pen, fontSize = 20.sp, color = Color.White)
                }
            }
            // (마이크 배지 없음: 이야기는 글자로 저장되고 정차 중엔 글자로 읽는다)
        }
        Canvas(Modifier.size(14.dp, 9.dp).offset(y = (-1).dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color.White)
        }
    }
}

/** 여행 목록일 때 목적지에 붙는 이름표 */
@Composable
fun TripLabel(t: Trip) {
    Column(
        Modifier.shadow(6.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp))
            .border(1.5.dp, Fv.Ink, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fv.Ink)
        Text(t.dateLabel, fontFamily = Pen, fontSize = 20.sp, color = Color(0xFF34404B))
    }
}
