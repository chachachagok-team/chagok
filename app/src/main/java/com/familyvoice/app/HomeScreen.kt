package com.familyvoice.app

import android.annotation.SuppressLint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/* ───────────── 색 · 글꼴 (시안: 필름 앨범 + 푸른 계열) ───────────── */

object Fv {
    val Land = Color(0xFFECE8DF)
    val Panel = Color(0xF2FAF8F3)
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF22303C)
    val Sub = Color(0xFF5C6670)
    val Blue = Color(0xFF2F5D8A)
    val BlueSoft = Color(0xFFE3EBF4)
    val BlueLine = Color(0xFF8FA9C4)
    val Dusty = Color(0xFF6E8FA8)
    val Sand = Color(0xFFB09A74)
    val Chip = Color(0xFFF1EEE7)
    const val MAP_LAND = "#ECE8DF"
    const val MAP_WATER = "#D5E0E8"
    const val MAP_ROAD = "#DCD6CA"
    const val MAP_GREEN = "#E4E1D5"
    const val ROUTE = "#2F5D8A"
}

val Batang = FontFamily(
    Font(R.font.gowun_batang_regular, FontWeight.Normal),
    Font(R.font.gowun_batang_bold, FontWeight.Bold)
)
val Pen = FontFamily(Font(R.font.nanum_pen_script))
/** CarPlay(SF Pro) 느낌의 한글 산세리프: Pretendard (OFL 무료 글꼴) */
val Sans = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/* ───────────── 데이터 묶기: 300m 안의 기억은 같은 장소 ───────────── */

data class Place(val key: Long, val lat: Double, val lng: Double, val memories: List<Memory>) {
    val latest: Memory get() = memories.maxBy { it.id }
    val first: Memory get() = memories.minBy { it.id }
}

fun groupPlaces(all: List<Memory>): List<Place> {
    val groups = mutableListOf<MutableList<Memory>>()
    all.filter { it.lat != null && it.lng != null }.sortedBy { it.id }.forEach { m ->
        val g = groups.firstOrNull { g ->
            LocationTracker.distanceM(g[0].lat!!, g[0].lng!!, m.lat!!, m.lng!!) <= 300f
        }
        if (g != null) g.add(m) else groups.add(mutableListOf(m))
    }
    return groups.map { g -> Place(g[0].id, g[0].lat!!, g[0].lng!!, g) }.sortedByDescending { it.latest.id }
}

private val dayFmt = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
private val longFmt = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)
private val shortFmt = SimpleDateFormat("M/d", Locale.KOREA)
fun dayLabel(id: Long): String = dayFmt.format(Date(id))
fun longLabel(id: Long): String = longFmt.format(Date(id))

private fun kmFromHome(home: Pair<Double, Double>?, p: Place): String? {
    home ?: return null
    val km = LocationTracker.distanceM(home.first, home.second, p.lat, p.lng) / 1000f
    return if (km < 1f) "집 근처" else "집에서 ${km.roundToInt()}km"
}

private fun placeMeta(home: Pair<Double, Double>?, p: Place): String = listOfNotNull(
    if (p.latest.rainy) "비 오던 날" else "맑은 날",
    kmFromHome(home, p)
).joinToString(" · ")

private fun tintOf(p: Place, index: Int): Color = when {
    p.memories.size > 1 -> Fv.Sand
    index == 0 -> Fv.Dusty
    else -> Fv.Dusty.copy(alpha = 0.8f)
}

/* ───────────── "이맘때": 지난해 이맘때 다녀온 여행 ───────────── */

/** 이맘때 카드에 띄울 것 */
data class Throwback(val trip: Trip, val label: String, val photo: Spot?, val voices: List<Memory>)

private fun agoLabel(days: Int): String = when {
    days <= 0 -> "오늘"
    days < 14 -> "${days}일 전"
    days < 60 -> "${days / 7}주 전"
    days < 365 -> "${days / 30}달 전"
    else -> "${days / 365}년 전"
}

/**
 * 지난 여행 하나를 고른다.
 *  - 300일 넘게 지난 여행 중 날짜(월·일)가 오늘과 45일 안으로 가장 가까운 것 → "1년 전 이맘때"
 *  - 없으면 가장 최근 여행 → "지난 여행 · 2달 전"
 * 사진은 그 여행 관광지 사진 중 마지막 것(없으면 아무 사진), 목소리는 그 여행 날짜에 남긴 것.
 */
fun throwbackFor(now: Long, memories: List<Memory>): Throwback? {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    val dayMs = 86_400_000L
    val doy = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.DAY_OF_YEAR)
    val dated = Journal.trips.mapNotNull { t ->
        val start = (try { fmt.parse(t.sortKey)?.time } catch (_: Exception) { null }) ?: return@mapNotNull null
        if (start > now) null else t to start
    }
    if (dated.isEmpty()) return null
    fun gap(start: Long): Int {
        val d = kotlin.math.abs(java.util.Calendar.getInstance().apply { timeInMillis = start }.get(java.util.Calendar.DAY_OF_YEAR) - doy)
        return minOf(d, 365 - d)
    }
    // '이맘때'로 쳐 주는 날짜 거리: 한 계절(±90일). 가을 여행은 연말까지도 "작년 이맘때"로 꺼내 준다
    val yearAgo = dated.filter { (_, s) -> now - s >= 300 * dayMs && gap(s) <= 90 }
        .minWithOrNull(compareBy<Pair<Trip, Long>>({ gap(it.second) }, { -it.second }))
    val (t, start) = yearAgo ?: dated.maxBy { it.second }
    val days = ((now - start) / dayMs).toInt()
    val label = if (yearAgo != null) "${((days + 182) / 365).coerceAtLeast(1)}년 전 이맘때" else "지난 여행 · ${agoLabel(days)}"
    val withPhoto = Journal.spotsOf(t.id).filter { it.photo != null }
    val photo = withPhoto.lastOrNull { it.kind == SpotKind.SIGHT } ?: withPhoto.lastOrNull()
    val voices = memories.filter { it.id in (start - dayMs / 2) until (start + 4 * dayMs) }
    return Throwback(t, label, photo, voices)
}

/* ───────────── 홈: 기억 지도 ───────────── */

@Composable
fun HomeScreen(
    lifecycle: Lifecycle,
    memories: List<Memory>,
    routes: List<DayRoute>,
    home: Pair<Double, Double>?,
    recordingToday: Boolean,
    live: LiveTrip? = null,                                          // 여행 기록 중: 집에서부터 온 길 + 쉬었다 간 곳
    zoomTo: Pair<Double, Double>? = null,                            // 이 자리로 지도를 확대 (휴게소에 다가갈 때)
    waypoint: Triple<Double, Double, String>? = null,                // 경유지로 넣은 곳 (지도에 핀만 찍는다)
    tripActive: Boolean = false,                                     // 여행 기록 중이면 메뉴바에 사진 QR 버튼
    fitKey: Int = 0,                                                 // 바뀌면 전체 탭으로 돌아가 여행 경로에 지도를 맞춤
    onOpenQr: () -> Unit = {},
    asking: Boolean,
    askReasons: List<String>,
    recPhase: Int,
    recQuestion: String,
    recHeard: String = "",                                           // Pleos STT 가 알아들은 말
    revisit: Memory?,
    revisitSameTrip: Boolean = false,                                // 들려주는 목소리가 이번 여행 것인지 ("아까 여기서")
    onAnswer: (Boolean) -> Unit,
    onRecordToday: () -> Unit,
    onPlay: (List<Memory>) -> Unit,
    onOpenDebug: () -> Unit,
    onCloseRevisit: () -> Unit = {},
    parked: Boolean = true,                                          // 차가 P 인지 (따로 남기기는 P 일 때만)
    suggestPlaceName: () -> String = { "" },                         // 따로 남길 때 미리 채울 이름 (네비 목적지)
    onAddSpot: (SpotKind, String, String, Int?, PlacePick?, Long?) -> String? = { _, _, _, _, _, _ -> null },  // 저장하고 새 장소 id
    onRecordSpotVoice: ((Long?) -> Unit) -> Unit = { it(null) },    // 남기기 창의 목소리 녹음 (끝나면 목소리 id, 실패면 null)
    onDiscardSpotVoice: (Long) -> Unit = {},                         // 저장 안 하고 버린 목소리 지우기
    placePicks: List<PlacePick> = emptyList(),                       // 네비 최근 목적지·즐겨찾기
    onOpenAdd: () -> Unit = {},                                      // 남기기 창을 열 때 (네비 목록 새로 받기)
    tripVoiceCount: Int = 0,                                         // 이번 여행에서 남긴 목소리 수
    onStopTrip: (Boolean) -> Unit = {}                               // 여행 그만하기 (true = 기록까지 지우기)
) {
    // 장소 카드에 이미 붙어 있는 목소리는 따로 핀을 그리지 않는다 (사진 핀에 마이크 배지로 표시)
    val places = remember(memories, Journal.spots.size) {
        val onSpots = Journal.spots.mapNotNull { it.voice }.toSet()
        groupPlaces(memories.filter { it.id !in onSpots })
    }
    var selected by remember { mutableStateOf<Long?>(null) }
    var filter by remember { mutableIntStateOf(0) } // 0 전체, 1 여행, 2 음식점, 3 관광지, 4 휴게소
    var tripSel by remember { mutableStateOf<String?>(null) }
    var spotSel by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<SpotKind?>(null) }   // 따로 남기는 중인 종류
    var stopping by remember { mutableStateOf(false) }           // 여행 그만하기 확인 카드
    val spotCount = Journal.spots.size
    var regionSel by remember { mutableStateOf<String?>(null) }   // 음식점·관광지·휴게소 탭에서 고른 지역
    val journal = remember(filter, tripSel, spotSel, spotCount, regionSel, places, tripActive) {
        journalFor(filter, tripSel, spotSel, regionSel).let { j ->
            when {
                // 여행 기록 중 전체 탭: 지난 여행 사진 핀은 숨기고 지금 여행 길만 (집은 그대로)
                filter == 0 && tripActive -> MapJournal(focus = listOfNotNull(home), key = "live")
                // 전체 탭: 목소리를 남긴 곳도 화면 안에 들어오게
                filter == 0 -> j.copy(focus = j.focus.orEmpty() + places.map { it.lat to it.lng })
                else -> j
            }
        }
    }
    val throwback = remember(memories, Journal.trips.size) { throwbackFor(System.currentTimeMillis(), memories) }
    // 쉬는 곳에서 앱을 다시 열면: 전체 탭에서 지금까지 온 길을 보여준다
    LaunchedEffect(fitKey) { if (fitKey > 0) { filter = 0; tripSel = null; spotSel = null; selected = null; regionSel = null } }
    // 여행 중에는 남긴 순서대로 아래로 쌓이게 (평소에는 원래 순서)
    val shown = if (tripActive) places.sortedBy { it.first.id } else places

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val fit = Density(constraints.maxWidth / 1280f, 1f)   // 화면 폭을 시안(1280) 기준으로 맞춤
    CompositionLocalProvider(
        LocalDensity provides fit,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Sans)   // 화면 전체 기본 글꼴
    ) {
    Box(Modifier.fillMaxSize().background(Fv.Land)) {
        MemoryMap(
            lifecycle = lifecycle,
            places = if (filter == 0 && !tripActive) shown else emptyList(),   // 여행 중엔 목소리 핀도 숨김
            routes = if (filter == 0) routes else emptyList(),
            live = if (filter == 0) live else null,
            zoomTo = if (filter == 0) zoomTo else null,
            waypoint = if (filter == 0) waypoint else null,
            fitKey = fitKey,
            home = home,
            selected = selected,
            onSelect = { selected = it; spotSel = null },
            journal = journal,
            onSpot = { spotSel = it.id; selected = null },
            onLabel = { tripSel = it; spotSel = null }
        )

        // 왼쪽 기억 패널
        Column(
            Modifier.padding(24.dp).width(316.dp).fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(18.dp), clip = false)
                .background(Fv.Panel, RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filter == 0) {
            // 제목을 길게 누르면 테스트 화면 (영상에 버튼이 안 보이게 숨김)
            Text("달리는 타임캡슐", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp, color = Fv.Ink,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenDebug() }) })
            Text("여행 ${Journal.trips.size}번 · 장소 ${Journal.spots.size}곳 · 목소리 ${memories.size}개", fontSize = 16.sp, color = Fv.Sub)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 여행 중에는 "1년 전 이맘때" 대신, 오늘 남긴 목소리가 하나씩 쌓인다
                if (tripActive) {
                    Text(
                        if (shown.isEmpty()) "오늘 남긴 이야기가 여기 쌓여요" else "오늘 남긴 이야기 ${shown.size}",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Fv.Sub
                    )
                } else {
                    throwback?.let { tb ->
                        ThrowbackCard(
                            tb,
                            onOpen = { filter = 1; tripSel = tb.trip.id; spotSel = null; selected = null }
                        )
                    }
                    if (memories.isEmpty()) EmptyCard()   // 목소리가 하나도 없을 때만 (장소 카드에 붙은 목소리는 places 에서 빠지므로 places 로 보면 안 됨)
                }
                shown.forEachIndexed { i, p ->
                    PlaceCard(p, tintOf(p, i), placeMeta(home, p), selected == p.key) {
                        selected = p.key
                    }
                }
            }
            } else JournalPanel(
                tab = filter, tripId = tripSel, spotId = spotSel,
                onTrip = { tripSel = it; spotSel = null },
                onBack = { tripSel = null; spotSel = null },
                onSpot = { spotSel = it },
                onAdd = { adding = it; onOpenAdd() },
                region = regionSel,
                onRegion = { regionSel = it; spotSel = null }
            )
        }

        // 위쪽 필터 칩 + 테스트 화면 버튼
        Row(Modifier.padding(start = 364.dp, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEachIndexed { i, label ->
                FilterChip(label, filter == i) { filter = i; tripSel = null; spotSel = null; selected = null; regionSel = null }
            }
            if (tripActive) QrChip(onOpenQr)       // 여행 기록 중에만: 동승자가 폰으로 찍어 사진을 올린다
        }

        // 선택한 장소의 기억 카드
        val sel = if (filter == 0) shown.firstOrNull { it.key == selected } else null
        val spot = spotSel?.let { id -> Journal.spots.firstOrNull { it.id == id } }
        if (spot != null) {
            SpotDetail(
                spot, Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 112.dp),
                showTrip = filter != 1,
                onTrip = { filter = 1; tripSel = spot.tripId },
                onClose = { spotSel = null }
                // "이야기 듣기" 버튼 없음: 화면에 글자가 다 보이니 정차 중엔 읽으면 된다. 소리는 운전 중(다시 지날 때)에만.
            )
        }
        if (sel != null) {
            MemoryDetail(
                p = sel, meta = placeMeta(home, sel),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 112.dp),
                onClose = { selected = null }
            )
        }

        // 오른쪽 아래: 오늘 여행 기록하기
        Row(
            Modifier.align(Alignment.BottomEnd).padding(28.dp)
                .shadow(12.dp, RoundedCornerShape(30.dp))
                .background(if (recordingToday) Fv.BlueSoft else Fv.Blue, RoundedCornerShape(30.dp))
                .clickable { if (recordingToday) stopping = true else onRecordToday() }
                .height(60.dp).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MicIcon(if (recordingToday) Fv.Blue else Color.White, 22.dp)
            Text(
                if (recordingToday) "여행 기록 중 · 끝내기" else "여행 시작",
                fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = if (recordingToday) Fv.Blue else Color.White
            )
        }

        adding?.let { k ->
            val hint = remember(k) { suggestPlaceName() }
            AddSpotOverlay(
                kind = k, initialName = hint, parked = parked, picks = placePicks,
                onRecord = onRecordSpotVoice, onDiscardVoice = onDiscardSpotVoice,
                onSave = { n, note, rating, pick, v -> onAddSpot(k, n, note, rating, pick, v)?.let { spotSel = it }; adding = null },
                onCancel = { adding = null }
            )
        }
        if (stopping && recordingToday) StopTripOverlay(
            parked = parked, voices = tripVoiceCount,
            onEnd = { onStopTrip(false); stopping = false },
            onDiscard = { onStopTrip(true); stopping = false },
            onDismiss = { stopping = false }
        )
        if (asking) AskTripOverlay(askReasons, onAnswer)
        if (recPhase in 1..4) RecordingOverlay(recPhase, recQuestion, recHeard)
        if (revisit != null) RevisitOverlay(revisit, revisitSameTrip, onCloseRevisit)
    }
    }
    }
}

/* ───────────── 지도 (MapLibre + OpenFreeMap) ───────────── */

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MemoryMap(
    lifecycle: Lifecycle,
    places: List<Place>,
    routes: List<DayRoute>,
    live: LiveTrip? = null,
    zoomTo: Pair<Double, Double>? = null,
    waypoint: Triple<Double, Double, String>? = null,
    fitKey: Int = 0,
    home: Pair<Double, Double>?,
    selected: Long?,
    onSelect: (Long) -> Unit,
    journal: MapJournal = MapJournal(),
    onSpot: (Spot) -> Unit = {},
    onLabel: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var mapError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { msg -> mapError = "지도를 불러오지 못했어요 (인터넷 확인): $msg" }
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false   // 작은 ⓘ 대신 지도 오른쪽 아래에 출처 글자를 직접 띄운다
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            // 마우스 휠 = 확대·축소 (마우스가 있는 곳을 중심으로) · 마우스 우클릭 = 한 단계 축소 (에뮬레이터 테스트용)
            mapView.setOnGenericMotionListener { _, e ->
                when {
                    e.actionMasked == android.view.MotionEvent.ACTION_SCROLL -> {
                        val v = e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL)
                        if (v != 0f) {
                            m.animateCamera(
                                CameraUpdateFactory.zoomBy(v * 0.6, android.graphics.Point(e.x.toInt(), e.y.toInt())), 120
                            )
                        }
                        true
                    }
                    e.actionMasked == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                        e.actionButton == android.view.MotionEvent.BUTTON_SECONDARY -> {
                        m.animateCamera(CameraUpdateFactory.zoomOut()); true
                    }
                    else -> false
                }
            }
            mapView.setOnTouchListener { _, e ->
                if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN &&
                    (e.buttonState and android.view.MotionEvent.BUTTON_SECONDARY) != 0
                ) { m.animateCamera(CameraUpdateFactory.zoomOut()); true } else false
            }
            m.addOnCameraMoveListener { tick++ }
            m.addOnCameraIdleListener { tick++ }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                restyle(style)
                styleReady = true
                tick++
            }
            map = m
        }
    }

    // 실제로 지나온 길 그리기
    val hasTracks = routes.any { it.points.size >= 2 } || (live?.points?.size ?: 0) >= 2
    LaunchedEffect(map, styleReady, routes, places, live) {
        val style = map?.style ?: return@LaunchedEffect
        if (styleReady) updateTracks(style, routes, places.flatMap { it.memories }, live)
    }

    // 여행·장소 탭: 여행 선 그리기
    LaunchedEffect(map, styleReady, journal.faint, journal.legs) {
        val style = map?.style ?: return@LaunchedEffect
        if (styleReady) updateJournal(style, journal)
    }

    // 핀이 하나씩 다다닥 떨어지게
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(journal.key, journal.spots.size) {
        revealed = 0
        for (i in journal.spots.indices) {
            delay(if (journal.numbered) 180L else 120L)
            revealed = i + 1
        }
    }

    // 장소를 고르면 그쪽으로 지도 이동
    LaunchedEffect(map, journal.selectedSpot) {
        val m = map ?: return@LaunchedEffect
        val s = journal.spots.firstOrNull { it.id == journal.selectedSpot } ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lng), maxOf(m.cameraPosition.zoom, 10.0)), 700)
    }

    // 경로 점선 + 카메라 맞추기 (지나온 길이 없을 때만 점선)
    LaunchedEffect(map, styleReady, places, home, routes.size, hasTracks, journal.focus, fitKey, live != null, live?.points?.size, zoomTo) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val route = buildList {
            home?.let { add(LatLng(it.first, it.second)) }
            places.sortedBy { it.first.id }.forEach { add(LatLng(it.lat, it.lng)) }
        }
        updateRoute(style, if (hasTracks) emptyList() else route)
        // 여행 기록 중이면 집에서부터 지금까지 온 길에 맞춘다
        val livePts = live?.points?.takeIf { it.size >= 2 }?.let { pts ->
            (pts.filterIndexed { i, _ -> i % 5 == 0 } + pts.last() + listOfNotNull(home)).map { LatLng(it.first, it.second) }
        }
        val fitPts = livePts ?: journal.focus?.map { LatLng(it.first, it.second) } ?: route + routes.flatMap { r -> r.points.filterIndexed { i, _ -> i % 5 == 0 }.map { LatLng(it.first, it.second) } }
        // 지도가 아직 화면에 자리를 못 잡았으면(폭 0) 카메라 이동이 먹히지 않는다.
        // 여행 중에는 이 효과를 다시 부르는 값이 거의 안 바뀌어서, 한 번 놓치면 세계 지도인 채로 남는다 → 자리 잡을 때까지 다시 시도
        fun fit(tries: Int = 0) {
            if ((mapView.width == 0 || mapView.height == 0) && tries < 20) {
                mapView.postDelayed({ fit(tries + 1) }, 100)
                return
            }
            // 한 자리로 확대하라고 했으면(휴게소에 다가가는 중) 거기만 크게 본다
            if (zoomTo != null) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(zoomTo.first, zoomTo.second), 12.5), 900)
                tick++
                return
            }
            val spread = fitPts.size >= 2 && fitPts.any { LocationTracker.distanceM(fitPts[0].latitude, fitPts[0].longitude, it.latitude, it.longitude) > 500f }
            // 맞출 점이 하나뿐일 때 쓸 자리 (여행을 막 시작해 지나온 길이 없을 때 등)
            val anchor = fitPts.firstOrNull() ?: route.firstOrNull() ?: home?.let { LatLng(it.first, it.second) }
            try {
                when {
                    spread -> {
                        val b = LatLngBounds.Builder().apply { fitPts.forEach { include(it) } }.build()
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(b, (420 * dens).toInt(), (190 * dens).toInt(), (150 * dens).toInt(), (170 * dens).toInt()))
                    }
                    anchor != null -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(anchor, 11.0))
                    else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(36.5, 127.9), 6.5))   // 전국
                }
            } catch (_: Exception) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(anchor ?: LatLng(36.5, 127.9), 10.0))
            }
            tick++
        }
        mapView.post { fit() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 지도 위 핀 (폴라로이드)
        val m = map
        if (m != null && styleReady) {
            val cameraTick = tick   // 지도가 움직이면 핀 위치를 다시 계산
            val proj = m.projection.also { if (cameraTick < 0) tick = 0 }
            home?.let { h ->
                val pt = proj.toScreenLocation(LatLng(h.first, h.second))
                Box(Modifier.anchorAt(pt.x, pt.y, 0.5f, 0.3f)) { HomePin() }
            }
            // 경유지로 넣은 곳 — 핀만 찍는다 (거기까지 가는 길은 그리지 않는다)
            waypoint?.let { (wLat, wLng, wName) ->
                val pt = proj.toScreenLocation(LatLng(wLat, wLng))
                Box(Modifier.anchorAt(pt.x, pt.y, 0.5f, 1f)) {
                    SpotPin(Spot("waypoint", "live", SpotKind.REST, wName, wLat, wLng, "", ""), null, big = true)
                }
            }
            places.forEachIndexed { i, p ->
                val pt = proj.toScreenLocation(LatLng(p.lat, p.lng))
                Box(Modifier.anchorAt(pt.x, pt.y, 0.5f, 1f).clickable { onSelect(p.key) }) {
                    PolaroidPin(p, tintOf(p, i), big = selected == p.key, tilt = if (i % 2 == 0) -3f else 3f)
                }
            }
            // 여행 목록: 목적지 이름표
            // 겹치면 위로 비켜 놓는다 (최근 여행이 먼저 자리를 잡는다)
            val lw = with(LocalDensity.current) { 150.dp.toPx() }
            val lh = with(LocalDensity.current) { 58.dp.toPx() }
            val placed = mutableListOf<Pair<Float, Float>>()
            journal.labels.forEach { t ->
                val end = Journal.destinationOf(t)
                val pt = proj.toScreenLocation(LatLng(end.first, end.second))
                var y = pt.y
                var tries = 0
                while (tries < 6 && placed.any { (px, py) -> kotlin.math.abs(px - pt.x) < lw && kotlin.math.abs(py - y) < lh }) { y -= lh; tries++ }
                placed += pt.x to y
                Box(Modifier.anchorAt(pt.x, y, 0.5f, 1f).clickable { onLabel(t.id) }) { TripLabel(t) }
            }
            // 장소 핀 (고른 핀은 맨 위로)
            journal.spots.take(revealed).withIndex()
                .sortedBy { it.value.id == journal.selectedSpot }
                .forEach { (i, s) ->
                    key(s.id) {
                        val pt = proj.toScreenLocation(LatLng(s.lat, s.lng))
                        Box(Modifier.anchorAt(pt.x, pt.y, 0.5f, 1f).clickable { onSpot(s) }) {
                            SpotPin(
                                s, if (journal.numbered) i + 1 else null, big = journal.selectedSpot == s.id,
                                color = journal.legOf[s.id]?.let { legColor(it) }
                            )
                        }
                    }
                }
        }
        // 확대 / 축소 버튼 (오른쪽 가운데) — 핀보다 뒤에 그려서 핀이 버튼을 덮지 않게
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(Fv.Panel, RoundedCornerShape(12.dp))
        ) {
            Box(Modifier.size(56.dp).clickable { map?.animateCamera(CameraUpdateFactory.zoomIn()) }, contentAlignment = Alignment.Center) {
                Text("+", fontSize = 28.sp, color = Fv.Ink)
            }
            Box(Modifier.width(56.dp).height(1.dp).background(Color(0xFFCFD6DD)))
            Box(Modifier.size(56.dp).clickable { map?.animateCamera(CameraUpdateFactory.zoomOut()) }, contentAlignment = Alignment.Center) {
                Text("−", fontSize = 28.sp, color = Fv.Ink)
            }
        }

        mapError?.let {
            Text(
                it, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                    .background(Fv.Panel, RoundedCornerShape(8.dp)).padding(12.dp),
                fontSize = 15.sp, color = Fv.Sub
            )
        }

        // 지도 출처 표시 (OpenFreeMap·OpenMapTiles·OpenStreetMap 사용 조건 — 지우지 말 것)
        Text(
            "© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp)
                .background(Color(0xB3FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp, color = Fv.Sub
        )
    }
}

/** 지도 원래 색을 시안 색(크림 땅·흐린 파랑 물·연한 도로)으로 바꾸고 글자는 큰 지명만 남긴다 */
private fun restyle(style: Style) {
    style.layers.forEach { layer ->
        val id = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(Fv.MAP_LAND))
            is FillLayer -> when {
                "water" in id -> layer.setProperties(PropertyFactory.fillColor(Fv.MAP_WATER))
                "building" in id -> layer.setProperties(PropertyFactory.visibility(Property.NONE))
                "park" in id || "wood" in id || "grass" in id || "landcover" in id || "landuse" in id ->
                    layer.setProperties(PropertyFactory.fillColor(Fv.MAP_GREEN), PropertyFactory.fillOpacity(0.6f))
                else -> layer.setProperties(PropertyFactory.fillColor(Fv.MAP_LAND))
            }
            is LineLayer -> when {
                "water" in id || "river" in id -> layer.setProperties(PropertyFactory.lineColor(Fv.MAP_WATER))
                "boundary" in id || "admin" in id -> layer.setProperties(PropertyFactory.lineColor("#D3CCBE"))
                else -> layer.setProperties(PropertyFactory.lineColor(Fv.MAP_ROAD))
            }
            is SymbolLayer -> {
                val keep = "place" in id && listOf("city", "town", "state", "capital").any { it in id }
                if (keep) layer.setProperties(
                    PropertyFactory.textColor("#8A8F94"),
                    PropertyFactory.textHaloColor(Fv.MAP_LAND)
                ) else layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
            else -> {}
        }
    }
}

private fun updateRoute(style: Style, pts: List<LatLng>) {
    if (pts.size < 2) {
        style.getLayer("route-line")?.setProperties(PropertyFactory.visibility(Property.NONE))
        return
    }
    val feature = Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) }))
    val src = style.getSourceAs<GeoJsonSource>("route")
    if (src == null) {
        style.addSource(GeoJsonSource("route", feature))
        style.addLayer(
            LineLayer("route-line", "route").withProperties(
                PropertyFactory.lineColor(Fv.ROUTE),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(0.1f, 3f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
    } else {
        src.setGeoJson(feature)
        style.getLayer("route-line")?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    }
}

/** 실제 지나온 길: 가장 최근 날은 진한 파랑(흰 테두리), 지난 날들은 흐린 파랑 */
/** 경유지(목소리를 남긴 곳)마다 바뀌는 선 색: 파랑 → 호박 → 초록 → 보라 → 코랄 */
val LEG_COLORS = listOf("#2F5D8A", "#C9853A", "#4E8C7A", "#8A6FB0", "#C2604F")

/** 여행 기록 중인 길: 집에서부터 지금까지 온 점들 + 쉬었다 간 곳 (여기서 선 색이 바뀐다) */
data class LiveTrip(
    val points: List<Pair<Double, Double>>,
    val stops: List<Pair<Double, Double>>,
    val driving: Boolean = false        // 지금 달리는 중인지 (달리는 구간만 점선, 지나온 구간은 실선)
)

/** 길을 들른 곳마다 끊는다. 순서대로, 앞 구간 뒤에서 500m 안으로 가장 가까이 지난 점에서 자른다 */
private fun splitAtStops(points: List<Pair<Double, Double>>, stops: List<Pair<Double, Double>>): List<List<Pair<Double, Double>>> {
    if (points.size < 2) return emptyList()
    val cuts = mutableListOf<Int>()
    var from = 0
    stops.forEach { (la, ln) ->
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in from until points.size) {
            val d = LocationTracker.distanceM(points[i].first, points[i].second, la, ln)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best > from && bestD <= 500f) { cuts.add(best); from = best }
    }
    val bounds = listOf(0) + cuts + listOf(points.size - 1)
    return (0 until bounds.size - 1).map { k -> points.subList(bounds[k], bounds[k + 1] + 1) }
}

private fun updateTracks(style: Style, routes: List<DayRoute>, memories: List<Memory>, live: LiveTrip? = null) {
    // 3km 넘게 순간이동한 구간(앱을 껐다 켰거나 위치가 끊긴 곳)은 잇지 않고 끊어서 그린다
    fun segmentsOf(r: DayRoute): List<List<Pair<Double, Double>>> {
        val out = mutableListOf<MutableList<Pair<Double, Double>>>()
        r.points.forEach { p ->
            val cur = out.lastOrNull()
            val prev = cur?.lastOrNull()
            if (cur == null || prev == null || LocationTracker.distanceM(prev.first, prev.second, p.first, p.second) > 3000f) out.add(mutableListOf(p))
            else cur.add(p)
        }
        return out.filter { it.size >= 2 }
    }
    fun linesOf(r: DayRoute) = segmentsOf(r).map { seg ->
        Feature.fromGeometry(LineString.fromLngLats(seg.map { Point.fromLngLat(it.second, it.first) }))
    }
    val valid = routes.filter { it.points.size >= 2 }
    // 가장 최근 날: 그날 목소리를 남긴 곳에서 선을 끊어 구간마다 색을 바꾼다
    fun legsOf(r: DayRoute): List<Feature> {
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val stops = memories.filter { it.lat != null && it.lng != null && dayFmt.format(Date(it.id)) == r.day }.sortedBy { it.id }
        val cuts = mutableListOf<Int>()
        var from = 0
        stops.forEach { m ->
            var best = -1
            var bestD = Float.MAX_VALUE
            for (i in from until r.points.size) {
                val d = LocationTracker.distanceM(r.points[i].first, r.points[i].second, m.lat!!, m.lng!!)
                if (d < bestD) { bestD = d; best = i }
            }
            if (best > from && bestD <= 500f) { cuts.add(best); from = best }
        }
        val bounds = listOf(0) + cuts + listOf(r.points.size - 1)
        return (0 until bounds.size - 1).flatMap { k ->
            val color = LEG_COLORS[k % LEG_COLORS.size]
            linesOf(DayRoute(r.day, r.points.subList(bounds[k], bounds[k + 1] + 1))).onEach { it.addStringProperty("color", color) }
        }
    }
    // 여행 기록 중: 쉬었다 간 곳마다 색을 바꾸고, 지금 달리는(마지막) 구간만 점선
    val liveFeatures = live?.takeIf { it.points.size >= 2 }?.let { lt ->
        val pieces = splitAtStops(lt.points, lt.stops)
        // 달리는 중이면 "방금 출발한 구간"만 점선. 쉬는 곳에 서면(P) 지나온 길은 전부 실선으로 굳는다.
        // 막 출발해 점이 아직 한두 개뿐인 구간도 그대로 지금 구간으로 본다
        // (점이 2개 이상인 구간을 찾으면, 직전 구간이 잡혀서 통째로 점선이 된다)
        val current = if (lt.driving) pieces.lastIndex else -1
        pieces.flatMapIndexed { k, pts ->
            linesOf(DayRoute("", pts)).onEach {
                it.addStringProperty("color", LEG_COLORS[k % LEG_COLORS.size])
                it.addBooleanProperty("dash", k == current)
            }
        }
    }
    // 여행 기록 중이면 이번 여행 길만 그린다. 아직 출발 전이라 점이 없어도, 오늘 앞서 다닌 길이나 지난 날 길은 섞지 않는다
    val recent = FeatureCollection.fromFeatures(
        if (live != null) liveFeatures.orEmpty()
        else valid.take(1).flatMap { legsOf(it) }.onEach { it.addBooleanProperty("dash", false) }
    )
    val old = FeatureCollection.fromFeatures(if (live != null) emptyList() else valid.drop(1).flatMap { linesOf(it) })
    // 여행 기록 중: 지금 있는 자리에 점 하나 (지나온 길 끝)
    val now = FeatureCollection.fromFeatures(
        live?.points?.lastOrNull()?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.second, it.first))) } ?: emptyList()
    )
    val rs = style.getSourceAs<GeoJsonSource>("track-recent")
    if (rs == null) {
        style.addSource(GeoJsonSource("track-old", old))
        style.addSource(GeoJsonSource("track-recent", recent))
        style.addSource(GeoJsonSource("track-now", now))
        style.addLayer(
            LineLayer("track-old-line", "track-old").withProperties(
                PropertyFactory.lineColor("#8FA9C4"), PropertyFactory.lineWidth(3.5f), PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        style.addLayer(
            LineLayer("track-recent-casing", "track-recent").withProperties(
                PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        style.addLayer(
            LineLayer("track-recent-line", "track-recent").withProperties(
                PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(5f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            ).apply { setFilter(Expression.eq(Expression.get("dash"), Expression.literal(false))) }
        )
        // 지금 있는 자리 (흰 테두리 + 파란 점)
        style.addLayer(
            CircleLayer("track-now-halo", "track-now").withProperties(
                PropertyFactory.circleRadius(13f), PropertyFactory.circleColor("#FFFFFF"), PropertyFactory.circleOpacity(0.95f)
            )
        )
        style.addLayer(
            CircleLayer("track-now-dot", "track-now").withProperties(
                PropertyFactory.circleRadius(8f), PropertyFactory.circleColor("#2E6FB7")
            )
        )
        // 지금 달리는 구간: 동글동글한 점선 (길이 0에 가까운 선 + 둥근 끝 = 점)
        style.addLayer(
            LineLayer("track-live-line", "track-recent").withProperties(
                PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(6f),
                PropertyFactory.lineDasharray(arrayOf(0.01f, 2.2f)),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            ).apply { setFilter(Expression.eq(Expression.get("dash"), Expression.literal(true))) }
        )
    } else {
        rs.setGeoJson(recent)
        style.getSourceAs<GeoJsonSource>("track-old")?.setGeoJson(old)
        style.getSourceAs<GeoJsonSource>("track-now")?.setGeoJson(now)
    }
}

/** 여행 탭 선: 다른 여행은 흐린 파랑, 고른 여행은 들른 곳마다 색이 바뀌는 굵은 선 */
private fun updateJournal(style: Style, j: MapJournal) {
    fun line(pts: List<Pair<Double, Double>>) =
        Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.second, it.first) }))
    val faint = FeatureCollection.fromFeatures(j.faint.filter { it.size >= 2 }.map { line(it) })
    val legs = FeatureCollection.fromFeatures(j.legs.mapIndexedNotNull { k, l ->
        if (l.size < 2) null else line(l).also { it.addStringProperty("color", LEG_COLORS[k % LEG_COLORS.size]) }
    })
    val fs = style.getSourceAs<GeoJsonSource>("jr-faint")
    if (fs == null) {
        style.addSource(GeoJsonSource("jr-faint", faint))
        style.addSource(GeoJsonSource("jr-legs", legs))
        style.addLayer(
            LineLayer("jr-faint-line", "jr-faint").withProperties(
                PropertyFactory.lineColor("#8FA9C4"), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.75f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        style.addLayer(
            LineLayer("jr-legs-casing", "jr-legs").withProperties(
                PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(10f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
        style.addLayer(
            LineLayer("jr-legs-line", "jr-legs").withProperties(
                PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(6f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
            )
        )
    } else {
        fs.setGeoJson(faint)
        style.getSourceAs<GeoJsonSource>("jr-legs")?.setGeoJson(legs)
    }
}

/** (x, y) 픽셀 위치에 요소를 붙인다. ax, ay는 요소의 어느 점을 그 위치에 맞출지 (0~1) */
private fun Modifier.anchorAt(x: Float, y: Float, ax: Float, ay: Float) = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place((x - p.width * ax).roundToInt(), (y - p.height * ay).roundToInt()) }
}

/* ───────────── 작은 부품들 ───────────── */

/** 메뉴바 맨 오른쪽: 여행 기록 중에만 보이는 사진 QR 버튼 */
@Composable
private fun QrChip(onClick: () -> Unit) {
    Row(
        Modifier.height(48.dp)
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .background(Fv.Blue, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(Modifier.size(20.dp)) {   // 작은 QR 모양 (네 귀퉁이 중 세 곳에 네모)
            val s = size.width
            val w = s * 0.12f
            listOf(Offset(0f, 0f), Offset(s * 0.58f, 0f), Offset(0f, s * 0.58f)).forEach { o ->
                drawRect(Color.White, o + Offset(w / 2, w / 2), Size(s * 0.42f - w, s * 0.42f - w), style = Stroke(w))
            }
            drawRect(Color.White, Offset(s * 0.62f, s * 0.62f), Size(s * 0.16f, s * 0.16f))
            drawRect(Color.White, Offset(s * 0.84f, s * 0.84f), Size(s * 0.16f, s * 0.16f))
        }
        Text("사진 QR", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp)
            .shadow(if (on) 6.dp else 0.dp, RoundedCornerShape(24.dp))
            .background(if (on) Fv.Ink else Fv.Panel, RoundedCornerShape(24.dp))
            .border(if (on) 0.dp else 1.dp, Color(0xFFCFD6DD), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (on) Color.White else Fv.Ink) }
}

/** 왼쪽 패널 맨 위: 지난해 이맘때 여행 (폴라로이드) */
@Composable
private fun ThrowbackCard(tb: Throwback, onOpen: () -> Unit) {
    val t = tb.trip
    val who = if (t.people >= 5) "${t.people}명이 다 같이" else "${t.people}명이 함께"
    Column(
        Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth().rotate(-1.2f)
            .shadow(10.dp, RoundedCornerShape(3.dp))
            .background(Fv.Paper, RoundedCornerShape(3.dp))
            .clickable { onOpen() }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            if (tb.photo != null) SpotPhoto(tb.photo, Modifier.fillMaxWidth().height(190.dp), 2.dp, 28.sp)
            else Box(Modifier.fillMaxWidth().height(190.dp).background(Fv.Dusty, RoundedCornerShape(2.dp)))
            Text(
                tb.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(10.dp).background(Fv.Ink.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Fv.Ink)
            Text(
                listOfNotNull(who, t.nights, tb.photo?.name).joinToString(" · "),
                fontSize = 15.sp, color = Fv.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        tb.photo?.note?.takeIf { it.isNotEmpty() }?.let { note ->
            Text("“$note”", fontFamily = Pen, fontSize = 23.sp, color = Color(0xFF34404B), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyCard() {
    Column(
        Modifier.fillMaxWidth().background(Fv.BlueSoft, RoundedCornerShape(8.dp))
            .border(1.5.dp, Fv.BlueLine, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("아직 남긴 목소리가 없어요", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Fv.Ink)
        Text("여행 중에 멈췄다 출발하면 차가 먼저 물어볼게요", fontSize = 15.sp, color = Fv.Sub)
    }
}

@Composable
private fun PlaceCard(p: Place, tint: Color, meta: String, on: Boolean, onClick: () -> Unit) {
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
        Box(Modifier.size(72.dp).background(tint, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Text("“", fontFamily = Pen, fontSize = 64.sp, color = Color.White, modifier = Modifier.offset(y = 12.dp))   // 소리 파형 대신 따옴표 = 남긴 이야기
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (p.memories.size > 1) "같은 곳 ${p.memories.size}번" else "${shortFmt.format(Date(p.latest.id))}의 이야기",
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Fv.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(meta, fontSize = 15.sp, color = Fv.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.latest.text.ifBlank { longLabel(p.latest.id) }, fontFamily = Pen, fontSize = 22.sp, color = Color(0xFF34404B),
                lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PolaroidPin(p: Place, tint: Color, big: Boolean, tilt: Float) {
    Column(
        Modifier.rotate(tilt).scale(if (big) 1.2f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.shadow(8.dp, RoundedCornerShape(2.dp)).background(Fv.Paper, RoundedCornerShape(2.dp))
                .padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 18.dp)
        ) {
            Box(Modifier.size(70.dp, 64.dp).background(tint), contentAlignment = Alignment.Center) {
                Text("“", fontFamily = Pen, fontSize = 60.sp, color = Color.White, modifier = Modifier.offset(y = 11.dp))
            }
        }
        Text(
            shortFmt.format(Date(p.latest.id)) + if (p.memories.size > 1) " ×${p.memories.size}" else "",
            fontFamily = Pen, fontSize = 24.sp, color = Color(0xFF34404B),
            modifier = Modifier.background(Color(0xD9FAF8F3), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun HomePin() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(52.dp).shadow(8.dp, CircleShape).background(Fv.Ink, CircleShape), contentAlignment = Alignment.Center) {
            HouseIcon(Color.White, 24.dp)
        }
        Text(
            "우리 집", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fv.Ink,
            modifier = Modifier.background(Fv.Panel, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MemoryDetail(p: Place, meta: String, modifier: Modifier, onClose: () -> Unit) {
    // 듣기 버튼 없음: 남긴 이야기를 글자로 바로 보여 준다 (정차 중 화면). 소리는 운전 중 다시 지날 때만.
    val list = p.memories.sortedBy { it.id }.filter { it.text.isNotBlank() }
    Column(
        modifier.width(360.dp)
            .shadow(18.dp, RoundedCornerShape(4.dp))
            .background(Fv.Paper, RoundedCornerShape(4.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (p.memories.size > 1) "같은 곳, ${p.memories.size}번의 날" else "이날 남긴 이야기",
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Fv.Ink
            )
            Text(meta, fontSize = 16.sp, color = Fv.Sub)
        }
        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) Text("남긴 글이 없어요", fontSize = 16.sp, color = Fv.Sub)
            list.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(longLabel(m.id), fontSize = 14.sp, color = Fv.Sub)
                    Text("“${m.text}”", fontFamily = Pen, fontSize = 24.sp, lineHeight = 32.sp, color = Color(0xFF34404B))
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(50.dp).border(1.5.dp, Color(0xFFCFD6DD), RoundedCornerShape(25.dp))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) { Text("닫기", fontSize = 17.sp, color = Fv.Ink) }
    }
}

/* ───────────── 전체 화면 위에 뜨는 카드들 ───────────── */

@Composable
private fun Dim(onTap: () -> Unit = {}, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB8F3F0EA)).clickable(enabled = true, onClick = onTap),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * 여행 그만하기: [여기서 끝내기] 지금까지 기록은 남기고 마감 · [기록 취소] 이번 여행 길·목소리 지우기
 * 주행 중에는 버튼이 잠긴다.
 */
@Composable
private fun StopTripOverlay(parked: Boolean, voices: Int, onEnd: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) = Dim(onDismiss) {
    Column(
        Modifier.width(620.dp).shadow(28.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp))
            .clickable(enabled = true, onClick = {})          // 카드 안을 눌러도 닫히지 않게
            .padding(horizontal = 44.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("여행 기록을 그만할까요?", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Fv.Ink)
        Text(
            "여기서 끝내면 지금까지 지나온 길과 목소리는 그대로 남아요." +
                if (voices > 0) " 기록을 취소하면 이번 여행의 길과 목소리 ${voices}개가 지워져요." else " 기록을 취소하면 이번 여행의 길이 지워져요.",
            fontSize = 17.sp, color = Fv.Sub
        )
        if (!parked) Text("안전을 위해 차를 세우면(P) 할 수 있어요", fontSize = 16.sp, color = Color(0xFFC2604F))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(68.dp).background(if (parked) Fv.Blue else Fv.BlueLine, RoundedCornerShape(34.dp))
                    .clickable(enabled = parked) { onEnd() },
                contentAlignment = Alignment.Center
            ) { Text("여기서 끝내기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Box(
                Modifier.weight(1f).height(68.dp).border(1.5.dp, if (parked) Color(0xFFC2604F) else Color(0xFFCFD6DD), RoundedCornerShape(34.dp))
                    .clickable(enabled = parked) { onDiscard() },
                contentAlignment = Alignment.Center
            ) { Text("기록 취소", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = if (parked) Color(0xFFC2604F) else Fv.Sub) }
        }
        Text("계속 기록하려면 바깥을 누르세요", fontSize = 15.sp, color = Fv.Sub,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AskTripOverlay(reasons: List<String>, onAnswer: (Boolean) -> Unit) = Dim {
    Column(
        Modifier.width(660.dp).shadow(28.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp))
            .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("여행 중이신 것 같아요", fontFamily = Pen, fontSize = 34.sp, color = Fv.Blue)
            Text("이번 여행, 기록해 둘까요?", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 42.sp, color = Fv.Ink)
        }
        if (reasons.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            reasons.take(4).forEach { r ->
                Text(
                    r, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Fv.Ink,
                    modifier = Modifier.background(Fv.Chip, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.weight(1f).height(76.dp).background(Fv.Blue, RoundedCornerShape(38.dp)).clickable { onAnswer(true) },
                contentAlignment = Alignment.Center
            ) { Text("기록할게요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Box(
                Modifier.weight(1f).height(76.dp).border(1.5.dp, Color(0xFFCFD6DD), RoundedCornerShape(38.dp)).clickable { onAnswer(false) },
                contentAlignment = Alignment.Center
            ) { Text("여행 아니에요", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Fv.Ink) }
        }
        Text("집에 돌아올 때까지 한 번만 물어봐요 · 기록한 여행만 목소리를 남겨요", fontSize = 16.sp, color = Fv.Sub,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecordingOverlay(phase: Int, question: String, heard: String = "") = Dim {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        initialValue = 0.9f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (phase) { 1 -> "차가 묻고 있어요"; 2 -> "듣고 있어요"; 4 -> "잘 못 알아들었어요"; else -> "이렇게 남겨 둘게요" },
                fontSize = 20.sp, color = Fv.Sub
            )
            Text(
                when (phase) { 3 -> if (heard.isNotBlank()) "“$heard”" else "이 장소에 이야기를 담았어요"; 4 -> "다음에 다시 여쭤볼게요"; else -> question },
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Fv.Ink,
                lineHeight = 54.sp,          // 안 주면 두 줄 이상일 때 줄끼리 겹쳐 보인다
                textAlign = TextAlign.Center, modifier = Modifier.width(900.dp)
            )
        }
        Box(
            Modifier.size(280.dp).scale(if (phase == 2) pulse else 1f).background(Fv.Blue.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(220.dp).background(Fv.Blue.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(160.dp).shadow(16.dp, CircleShape).background(Fv.Blue, CircleShape), contentAlignment = Alignment.Center) {
                    MicIcon(Color.White, 60.dp)
                }
            }
        }
        Text(
            when (phase) { 1 -> "삐 소리가 나면 한마디 남겨 주세요"; 2 -> "말이 끝나면 글자로 남겨요 (Pleos 음성 인식)"; 4 -> "다음 정차 때 다시 여쭤볼게요"; else -> "다음에 여기 오면 차가 읽어 드릴게요" },
            fontSize = 19.sp, color = Fv.Sub
        )
    }
}

/** 이 목소리를 남긴 그 여행에서, 같은 곳(300m)을 찍은 사진이 있는 장소 (없으면 null) */
private fun photoSpotOf(m: Memory): Spot? {
    val lat = m.lat ?: return null
    val lng = m.lng ?: return null
    Journal.spots.firstOrNull { it.photo != null && it.voice == m.id }?.let { return it }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    return Journal.spots.firstOrNull { s ->
        if (s.photo == null || LocationTracker.distanceM(lat, lng, s.lat, s.lng) > 300f) return@firstOrNull false
        val start = Journal.trip(s.tripId)?.sortKey?.let { k -> try { fmt.parse(k)?.time } catch (_: Exception) { null } }
            ?: return@firstOrNull false
        m.id in (start - 43_200_000L) until (start + 6 * 86_400_000L)    // 그 여행 기간에 남긴 목소리
    }
}

@Composable
private fun RevisitOverlay(m: Memory, sameTrip: Boolean, onClose: () -> Unit) = Dim(onTap = onClose) {
    // 그 여행에서 찍은 사진이 있으면 파형 대신 사진 (예: 작년 경주 여행 때 들른 천안삼거리휴게소)
    val spot = remember(m.id, sameTrip) { if (sameTrip) null else photoSpotOf(m) }
    val trip = spot?.let { Journal.trip(it.tripId) }
    Row(
        Modifier.width(880.dp).shadow(28.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp)).padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.rotate(-3f).shadow(10.dp).background(Fv.Paper).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 34.dp)
        ) {
            if (spot != null) SpotPhoto(spot, Modifier.size(260.dp, 220.dp), 0.dp, 40.sp)
            else Box(Modifier.size(260.dp, 220.dp).background(Fv.Dusty), contentAlignment = Alignment.Center) {
                Waveform(m.id, bars = 22, width = 220.dp, height = 160.dp, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (sameTrip) "아까 여기서" else "예전에 여기 왔을 때", fontFamily = Pen, fontSize = 34.sp, color = Fv.Blue)
            Text(
                if (m.text.isNotBlank()) "“${m.text}”" else if (sameTrip) "조금 전 나눈 이야기를\n다시 들려드릴게요" else "그날 남긴 이야기를\n들려드릴게요",
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = if (m.text.length > 30) 32.sp else 40.sp, color = Fv.Ink, lineHeight = 46.sp,
                modifier = Modifier.width(480.dp)
            )
            Text(
                listOfNotNull(trip?.title, longLabel(m.id), trip?.riders?.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = " 함께"))
                    .joinToString(" · ") + if (m.rainy) " · 그날은 비가 왔어요" else "",
                fontSize = 18.sp, color = Fv.Sub
            )
        }
    }
}

/* ───────────── 파형 · 아이콘 (Canvas로 직접 그림) ───────────── */

@Composable
private fun Waveform(seed: Long, bars: Int, width: Dp, height: Dp, color: Color) {
    val hs = remember(seed, bars) {
        var s = seed % 233280L
        List(bars) { i ->
            s = (s * 9301L + 49297L) % 233280L
            val r = s / 233280f
            val env = sin(Math.PI * (i + 0.5) / bars).toFloat()
            (0.15f + env * (0.35f + r * 0.5f)).coerceAtMost(1f)
        }
    }
    Canvas(Modifier.size(width, height)) {
        val gap = size.width / bars
        val bw = (gap * 0.45f).coerceAtLeast(2f)
        hs.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(
                color, topLeft = Offset(i * gap + (gap - bw) / 2, (size.height - bh) / 2),
                size = Size(bw, bh), cornerRadius = CornerRadius(bw / 2, bw / 2)
            )
        }
    }
}

@Composable
fun MicIcon(color: Color, s: Dp) = Canvas(Modifier.size(s)) {
    val u = size.width / 24f
    val st = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(color, Offset(9 * u, 3 * u), Size(6 * u, 11 * u), CornerRadius(3 * u, 3 * u), style = st)
    drawArc(color, 0f, 180f, false, Offset(5 * u, 4 * u), Size(14 * u, 14 * u), style = st)
    drawLine(color, Offset(12 * u, 18 * u), Offset(12 * u, 21 * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
}

@Composable
private fun HouseIcon(color: Color, s: Dp) = Canvas(Modifier.size(s)) {
    val u = size.width / 24f
    val st = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply { moveTo(3 * u, 11 * u); lineTo(12 * u, 4 * u); lineTo(21 * u, 11 * u) }, color, style = st)
    drawPath(Path().apply { moveTo(5 * u, 10 * u); lineTo(5 * u, 20 * u); lineTo(19 * u, 20 * u); lineTo(19 * u, 10 * u) }, color, style = st)
}

@Composable
private fun PlayIcon(color: Color, s: Dp) = Canvas(Modifier.size(s)) {
    val u = size.width / 24f
    drawPath(Path().apply { moveTo(7 * u, 4 * u); lineTo(20 * u, 12 * u); lineTo(7 * u, 20 * u); close() }, color)
}
