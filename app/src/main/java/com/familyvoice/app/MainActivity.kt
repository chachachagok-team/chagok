package com.familyvoice.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familyvoice.app.ui.theme.FamilyVoiceTheme

/**
 * 차곡차곡 메인 화면. 기어(P→D)에 맞춰 질문하고, 대답을 그 장소에 글자로 남기고,
 * 다시 지날 때 들려준다. 예시 여행 시나리오(집 → 천안 → 대전 → 충주호 → 집)와 테스트 화면도 여기서 띄운다.
 */
class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null
    private val sdkStatus = mutableStateOf("Vehicle SDK 준비 중…")
    private val gear = mutableStateOf("-")
    private val odometer = mutableStateOf("-")
    private val tripTime = mutableStateOf("-")
    private val wiper = mutableStateOf("-")
    private val updatedAt = mutableStateOf("")

    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    // 목소리 테스트
    private val voiceStatus = mutableStateOf("목소리 테스트 준비 중…")
    private lateinit var prompts: PromptPlayer
    private lateinit var store: MemoryStore
    private lateinit var routes: RouteStore
    private lateinit var pleos: PleosVoice
    private val pleosStatus = mutableStateOf("Pleos 음성 SDK 준비 중…")
    private val dayRoutes = mutableStateOf<List<DayRoute>>(emptyList())
    private val liveTrip = mutableStateOf<LiveTrip?>(null)       // 여행 기록 중: 집에서부터 지금까지 온 길 + 쉬었다 간 곳
    private var legArrived = false                                  // 이번 구간을 끝까지 달렸는지 (P 를 넣어도 다 못 갔으면 도착이 아니다)
    private val legLeftKm = mutableStateOf(0)                       // 목적지까지 남은 거리 (km)
    private var legLeftM = 0.0                                      // 목적지까지 남은 거리 (m) — 도착 판정용
    private var snapLeg: (() -> Unit)? = null                       // 마지막 구간을 바로 끝내기 (목적지 코앞에서 P 를 넣었을 때)
    private val speedKmh = mutableStateOf(0)                        // 시연 주행 속도 (앱이 만들어 쓴다)
    private val livePoints = mutableListOf<Pair<Double, Double>>()   // 이번 여행에서 지나온 점 (파일 대신 메모리 — 점마다 디스크를 읽으면 느려진다)
    private val liveTripOn = mutableStateOf(false)               // 실제 여행 기록 중인지 (메뉴바 QR 버튼)
    private val anyTripOn = mutableStateOf(false)                // 예시 여행 포함, 여행 중인지 (지나온 길은 여행 중에만 그림)
    private val mapFitKey = mutableStateOf(0)                    // 앱을 다시 열 때마다 +1 → 지도를 여행 경로에 맞춤
    private val memories = mutableStateOf<List<Memory>>(emptyList())
    private val flowStatus = mutableStateOf("P에서 잠시 쉬었다가 D로 출발하면 차가 먼저 물어봐요")
    private var lastGear: VehicleGear? = null
    private var parkedSince = 0L
    private var flowRunning = false
    private var isRainy = false
    // 위치·재방문
    private var tracker: LocationTracker? = null
    private val locationText = mutableStateOf("위치 권한을 기다리는 중…")
    private val revisitStatus = mutableStateOf("예전에 목소리를 남긴 곳 근처(300m)에 오면 차가 들려줘요")
    private val revisitRadiusM = VoiceRules.REPLAY_RADIUS_M
    private val playedThisTrip = mutableSetOf<Long>()   // 이번 여행에서 이미 들려준 목소리 (한 여행에 한 번만)
    private var playedTripKey = ""
    private val leftSpot = mutableSetOf<Long>()         // 남긴 뒤 그 자리를 한 번 벗어난 목소리 (같은 여행 것은 갔다 와야 들려줌)
    private var parkedAt: Pair<Double, Double>? = null  // 마지막으로 P를 넣은 자리 = 대답을 남길 자리
    private var departPending = false                   // P 에서 쉬었고 아직 출발(D) 전 (중간에 R·N 을 거쳐도 유지)
    private val revisitSameTrip = mutableStateOf(false) // 지금 들려주는 목소리가 이번 여행 것인지 (카드 문구)
    private val summaryUi = mutableStateOf<SummaryUi?>(null)   // 집 도착 때 "이야기 정리 중" 화면
    private val summarizer: TripSummarizer by lazy { DemoSummarizer(this) }   // SDK 열리면 바꿔 끼우기
    private lateinit var focus: AudioFocus              // 말하는 동안 음악 줄이기·멈추기
    private val startupPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startLocation()
        else locationText.value = "위치 권한이 거부됐어요"
    }
    // 언제 물어볼지 (여행 모드 + 자동 판단)
    private lateinit var policy: TripPolicy
    private val tripMode = mutableStateOf(false)
    private val answerText = mutableStateOf("")
    private val reasonText = mutableStateOf("")
    private val askingTrip = mutableStateOf(false)
    // 홈(기억 지도) 화면 상태
    private val showDebug = mutableStateOf(false)
    private val recPhase = mutableStateOf(0)            // 0 없음, 1 질문 중, 2 듣는 중, 3 저장됨
    private val revisitMemory = mutableStateOf<Memory?>(null)
    private val homeState = mutableStateOf<Pair<Double, Double>?>(null)
    private val tripReasons = mutableStateOf<List<String>>(emptyList())
    private val recordingToday = mutableStateOf(false)
    // 상황별 질문
    private val recQuestion = mutableStateOf("방금 들른 곳, 어땠어요?")
    private val recHeard = mutableStateOf("")                       // Pleos STT 가 알아들은 말 (저장 카드에 보여 줌)
    private var ttsBroken = false                                   // Pleos TTS 가 한 번 실패하면 이번 실행 동안은 대체로
    private var sttBroken = false
    private var lastDepartAt = 0L                  // 마지막으로 출발(P→D)한 시각
    private var lastDriveMs = 0L                   // 방금 정차하기 전까지 달린 시간
    private val longDriveMs = VoiceRules.LONG_DRIVE_MS   // 시연 2분 · 실제 1시간 30분 (VoiceRules.DEMO 로 전환)
    private var passengers: Int? = null
    private val minStopMs = VoiceRules.MIN_STOP_MS     // 시연 5초 · 실제 10분 (VoiceRules.DEMO 로 전환)
    // 출발 전 탑승자 고르기 + 사진 QR (메인 화면에서 바로 쓰는 흐름)
    private lateinit var ridersStore: RiderStore
    private val riderPicker = mutableStateOf(false)
    private val ridersPicked = mutableStateOf<Set<String>>(emptySet())
    private val riderNames = mutableStateOf("")
    private val qrCard = mutableStateOf(false)
    private var pickerForScenario = false               // 탑승자 카드를 테스트 화면의 "예시 여행"에서 열었는지
    private var pickerAuto = false                      // 차가 먼저 띄운 카드인지 (이때 "나중에" = 이번엔 여행 아님)
    private val pickerNote = mutableStateOf("")         // 카드 위에 보여 줄 한 줄 (예: "충주호에 가시네요")

    // 네비 (Pleos NaviHelper): 목적지를 보고 출발 전에 여행인지 알아챈다
    private var navi: NaviHelper? = null
    private val naviStatus = mutableStateOf("네비 연결 전")
    private var recentDests: List<RecentDestinationInfo> = emptyList()
    private var naviTrip: Pair<String, List<String>>? = null
    private var naviDest: DestinationInfo? = null                // 마지막으로 받은 네비 목적지 (따로 남길 때 이름 채우기)
    private val parkedState = mutableStateOf(true)               // 화면용: 지금 P 인지
    private var lastNaviLoc: Location? = null                    // 네비가 알려 준 지금 위치 (주행 중엔 GPS 대신 이걸 쓴다)
    private var naviPolling = false
    private val naviLocText = mutableStateOf("네비 위치: 아직 못 받음")
    private val nearMemText = mutableStateOf("예전 목소리까지: 아직")   // 왜 안 들리는지 확인용
    private var locCount = 0
    private var firstLoc: Pair<Double, Double>? = null   // 앱을 켠 뒤 처음 받은 위치
    private var movedSinceLaunch = false                 // 그 뒤로 실제로 움직인 적이 있는지
    private val parkedText = mutableStateOf("아직 모름")              // 서 있는 자리 (여기서 출발하면 묻는지 판단)
    private var fused: FusedLocation? = null
    private val fusedStatus = mutableStateOf("Pleos 위치 SDK: 아직 확인 안 함")
    private val recentPicks = mutableStateOf<List<PlacePick>>(emptyList())     // 네비 최근 목적지
    private val bookmarkPicks = mutableStateOf<List<PlacePick>>(emptyList())   // 네비 즐겨찾기 (집·회사 빼고)
    private lateinit var spotStore: SpotStore
    private lateinit var tripStore: TripStore
    private val pendingSpotText = mutableMapOf<Long, String>()   // 남기기 창에서 말로 남겼지만 아직 저장 안 한 글자 (id → 글자)
    private var lastOdoKm: Float? = null                        // 속도 계산용: 직전 주행거리계 값
    private var lastOdoAt = 0L
    private var realSpeed = 0                                   // 주행거리 변화로 계산한 실제 속도 (km/h)
    private var realSpeedAt = 0L
    private var odometerKm: Float? = null                       // 차 주행거리계 (여행 거리 계산)
    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = runOnUiThread {
            naviStatus.value = "경로 안내 시작: ${routeStartInfo.name}"
            safeNavi { navi?.getDestinationInfo() }
        }
        override fun onDestinationChanged() = runOnUiThread { safeNavi { navi?.getDestinationInfo() } }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = runOnUiThread { onNaviDestination(destinationInfo) }
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = runOnUiThread {
            recentDests = recentDestinationInfoList
            recentPicks.value = recentDestinationInfoList.map { PlacePick(it.name, it.address, it.latitude, it.longitude) }
        }
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = runOnUiThread { onNaviBookmarks(bookmarkInfoList) }
        // 경유지를 넣은 뒤 네비가 알려 주는 지금 경유지 목록
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = runOnUiThread {
            naviStatus.value = "네비 경유지: " + (waypointInfoList.joinToString(", ") { it.toString().take(60) }.ifBlank { "없음" })
            cue("🧭 네비 경유지 응답 ${waypointInfoList.size}개")
        }
        // 지금 위치의 주소 → 방금 멈춘 곳 이름에 쓴다 (예: "청주 청원구")
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = runOnUiThread {
            val st = policy.tripStops().lastOrNull()
            if (st != null && currentLocationInfo.address.isNotBlank() &&
                LocationTracker.distanceM(st.lat, st.lng, currentLocationInfo.latitude, currentLocationInfo.longitude) < 2000f
            ) policy.setLastStopAddress(currentLocationInfo.address)
            onNaviLocation(currentLocationInfo.latitude, currentLocationInfo.longitude)
        }
    }

    // 가상 여행 시나리오 (메인 화면에서 기어로 진행)
    // 0 꺼짐 / 1 집(출발 대기) / 2 →천안 / 3 천안 / 4 →대전 / 5 대전 / 6 →충주호 / 7 충주호 / 8 →집 / 9 집 도착
    private val zoomTo = mutableStateOf<Pair<Double, Double>?>(null)   // 지도를 한 자리로 확대 (휴게소에 다가갈 때)
    private val waypoint = mutableStateOf<Triple<Double, Double, String>?>(null)   // 경유지로 넣은 곳 (지도에 핀만)
    private val scenarioStage = mutableStateOf(0)
    private val arrivalPlace = mutableStateOf<Pair<String, String>?>(null)  // 도착 카드 (장소, 한 줄)
    private val pastSpot = mutableStateOf<Spot?>(null)  // 예전에 들렀던 곳 회상 카드
    private var pastSpotShown = false

    private var demoLoc: Location? = null             // 예시 여행 중에는 이 위치를 "지금 위치"로 쓴다

    // 사진 업로드 QR (차곡차곡 Apps Script 서버)
    private val tripLink = mutableStateOf<TripLink?>(null)
    private val tripQrBitmap = mutableStateOf<Bitmap?>(null)
    private val tripUploadStatus = mutableStateOf("")
    private val tripUploadBusy = mutableStateOf(false)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = ui { this@MainActivity.gear.value = gearLabel(gear); onGearChanged(gear) }
        override fun onFailed(e: Exception) = ui { gear.value = "오류: ${e.message}" }
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = ui {
            odometer.value = kmLabel(value)
            if (value != null) {
                // 주행거리가 늘어난 양 ÷ 지난 시간 = 지금 속도 (SDK 에 속도 API 가 없어서 이렇게 구한다)
                val now = System.currentTimeMillis()
                val prev = lastOdoKm
                if (prev != null && now > lastOdoAt && value > prev) {
                    val kmh = ((value - prev) * 3_600_000.0 / (now - lastOdoAt)).toInt()
                    if (kmh in 1..300) { realSpeed = kmh; realSpeedAt = now }
                }
                lastOdoKm = value
                lastOdoAt = now
                odometerKm = value
            }
        }
        override fun onFailed(e: Exception) = ui { odometer.value = "오류: ${e.message}" }
    }
    private val tripTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) = ui { tripTime.value = timeLabel(value) }
        override fun onFailed(e: Exception) = ui { tripTime.value = "오류: ${e.message}" }
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) = ui {
            wiper.value = wiperLabel(value); isRainy = value == WiperState.ON
            if (isRainy && policy.tripActive) policy.tripRained = true    // 여행 카드 날씨 "비"
        }
        override fun onFailed(e: Exception) = ui { wiper.value = "오류: ${e.message}" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prompts = PromptPlayer(this)
        focus = AudioFocus(this)
        store = MemoryStore(this)
        routes = RouteStore(this)
        pleos = PleosVoice(this) { msg -> runOnUiThread { pleosStatus.value = (listOf(msg) + pleosStatus.value.lines()).take(10).joinToString("\n") } }  // 최근 10줄 보여주기
        routes.cleanup()
        dayRoutes.value = routes.load()
        policy = TripPolicy(this)
        spotStore = SpotStore(this)
        Journal.restore(spotStore.load())     // 따로 남긴 곳 불러오기
        tripStore = TripStore(this)
        tripStore.load().let { (ts, ss) -> Journal.restoreTrips(ts, ss) }   // 실제로 다녀온 여행 카드 불러오기
        Journal.loadDetailedRoutes(this)      // 예시 여행을 실제 고속도로 경로로 (파일이 있을 때만)
        ridersStore = RiderStore(this)
        ridersStore.todayRiders?.let {
            ridersPicked.value = it
            riderNames.value = ridersStore.namesOf(it)
        }
        refreshPolicyText()
        memories.value = store.load()
        if (VoiceRules.DEMO_FRESH_START) resetForDemo()   // 시연: 지난 테스트 기록을 지우고 늘 같은 상태로 시작
        seedDemoMemories()                    // 예시 여행의 목소리 (res/raw 에 파일이 있는 장소만)
        startupPermissions.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.STT_SERVICE",
            "pleos.car.permission.FUSED_LOCATION"
        ))
        pleos.initTts()
        pleos.initStt()
        pleos.initLlm()
        SimSpeed.start() // 시연: 시뮬레이터에서 현재 속도 읽기 (실패하면 조용히 꺼진다)
        startVehicle()   // 다른 준비가 끝난 뒤에 차량 연결 (기어 이벤트가 바로 올 수 있어서)
        startNavi()
        if (policy.isExpired()) { finishRealTrip(); policy.endTrip(); refreshPolicyText() }   // 집에 안 온 채 너무 오래된 여행은 자동 마감
        setContent {
            FamilyVoiceTheme {
                if (showDebug.value) DebugScreen()
                else Box(modifier = Modifier.fillMaxSize()) {
                    HomeArea()
                    // 도착 카드
                    // (집 도착은 사진 QR 카드에 합쳐서 한 장으로)
                    arrivalPlace.value?.takeUnless { (qrCard.value || summaryUi.value != null) && it.first == "집" }?.let { (place, line) ->
                        ArrivalCard(place = place, line = line, onClose = { arrivalPlace.value = null })
                    }
                    // 예전에 들렀던 곳 회상 카드
                    pastSpot.value?.let { sp ->
                        val t = Journal.trip(sp.tripId)
                        PastSpotCard(
                            spot = sp,
                            tripTitle = t?.title ?: "지난 여행",
                            tripDate = t?.dateLabel ?: "",
                            onClose = { pastSpot.value = null }
                        )
                    }
                    // 출발 전: 오늘 누구와 가는지 고르기
                    if (riderPicker.value) RiderPickerCard(
                        riders = ridersStore.roster,
                        selected = ridersPicked.value,
                        onToggle = { id ->
                            val cur = ridersPicked.value.toMutableSet()
                            if (!cur.add(id)) cur.remove(id)
                            ridersPicked.value = cur
                        },
                        onStart = { startTripWithRiders() },
                        onSkip = { skipRiderPicker() },
                        note = pickerNote.value
                    )
                    // 사진 모으기 QR
                    if (qrCard.value) TripQrCard(
                        riderNames = riderNames.value,
                        qr = tripQrBitmap.value,
                        status = tripUploadStatus.value,
                        arrival = arrivalPlace.value?.takeIf { it.first == "집" }?.second,
                        onClose = {
                            qrCard.value = false
                            if (arrivalPlace.value?.first == "집") arrivalPlace.value = null
                            // 집에 도착해 여행이 끝났으면, 다음 여행을 시작할 수 있게 되돌린다
                            if (scenarioStage.value == 9) resetScenario()
                        }
                    )
                    // 집 도착: 오늘 이야기 정리 중 (AI 요약 자리 · 지금은 시연용 예시)
                    summaryUi.value?.let { SummaryOverlay(it) }
                    // 상태 한 줄 — 왜 물었는지/왜 안 물었는지 바로 보이게 (촬영 때는 VoiceRules.SHOW_STATUS = false)
                    if (VoiceRules.SHOW_STATUS) androidx.compose.material3.Text(
                        "${scenarioStage.value}단계 · ${gear.value} · ${speedKmh.value}km/h · 남은 ${legLeftKm.value}km · ${flowStatus.value}",
                        fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(androidx.compose.ui.graphics.Color(0x99000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    /** 기억 지도 화면 (평소 앱 화면). 무대 모드에서는 이 위에 시연 띠만 얹는다. */
    @Composable
    private fun HomeArea() {
        HomeScreen(
            lifecycle = lifecycle,
            memories = memories.value,
            // 여행이 끝나거나 취소되면 지나온 길은 지도에서 내린다 (끝낸 여행은 여행 탭 카드에 남음)
            routes = if (anyTripOn.value) dayRoutes.value else emptyList(),
            live = liveTrip.value,
            zoomTo = zoomTo.value,
            waypoint = waypoint.value,
            tripActive = liveTripOn.value,
            fitKey = mapFitKey.value,
            onOpenQr = { if (tripQrBitmap.value == null && !tripUploadBusy.value) requestTripQr(); qrCard.value = true },
            home = homeState.value,
            recordingToday = recordingToday.value,
            asking = askingTrip.value,
            askReasons = tripReasons.value,
            recPhase = recPhase.value,
            recQuestion = recQuestion.value,
            recHeard = recHeard.value,
            revisit = revisitMemory.value,
            revisitSameTrip = revisitSameTrip.value,
            onAnswer = { answerTrip(it) },
            // 시연 모드면 "여행 시작"이 바로 기어 시나리오로 들어간다 (VoiceRules.DEMO_SCENARIO)
            onRecordToday = { if (!policy.tripActive) openRiderPicker(scenario = VoiceRules.DEMO_SCENARIO) },
            parked = parkedState.value,
            suggestPlaceName = { suggestPlaceName() },
            onAddSpot = { k, n, note, rating, pick, v -> addSoloSpot(k, n, note, rating, pick, v) },
            onRecordSpotVoice = { done -> recordSpotVoice(done) },
            onDiscardSpotVoice = { id -> discardSpotVoice(id) },
            placePicks = (recentPicks.value + bookmarkPicks.value).distinctBy { it.name },
            onOpenAdd = { safeNavi { navi?.getRecentDestinationInfo() }; safeNavi { navi?.getBookmarkInfo() } },
            tripVoiceCount = policy.tripStartedAt.let { since -> if (since > 0L) memories.value.count { it.id >= since } else 0 },
            onStopTrip = { discard -> stopTripByUser(discard) },
            onPlay = { playMemories(it) },
            onOpenDebug = { showDebug.value = true },
            onCloseRevisit = {
                prompts.stop(); pleos.stopSpeaking()
                revisitMemory.value = null; flowRunning = false; focus.release()
            }
        )
    }

    /** 개발·시연 보조용 테스트 화면 (영상에는 안 나오는 곳) */
    @Composable
    private fun DebugScreen() {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showDebug.value = false }) { Text("← 기억 지도로") }
                    OutlinedButton(onClick = {
                        showDebug.value = false
                        resetScenario()
                        openRiderPicker(scenario = true)
                    }) { Text("예시 여행 (천안·대전·충주호)") }
                }

                DebugSection("차량 · 네비")
                Text(sdkStatus.value, style = MaterialTheme.typography.bodyMedium)
                InfoRow("기어", gear.value)
                InfoRow("누적 주행 거리", odometer.value)
                InfoRow("앞 와이퍼", wiper.value)
                InfoRow("네비", naviStatus.value)
                InfoRow("네비 위치 (주행 중)", naviLocText.value)
                InfoRow("예전 목소리", nearMemText.value)
                InfoRow("서 있는 자리", parkedText.value)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { fusedStatus.value = SimSpeed.status() + " / " + CarSpeed.status(this@MainActivity) }) { Text("차 속도 읽기 확인") }
                    OutlinedButton(onClick = { checkFusedLocation() }) { Text("Pleos 위치 SDK 확인") }
                    OutlinedButton(onClick = { safeNavi { navi?.getCurrentLocationInfo() } }) { Text("네비에 지금 위치 묻기") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 소리가 나오는지만 바로 확인하는 버튼 (주행·기어 없이)
                    OutlinedButton(onClick = { flowRunning = false; lastYearPlayed = false; playLastYearVoice() }) { Text("작년 이야기 지금 들려주기") }
                }
                Text(fusedStatus.value, style = MaterialTheme.typography.bodyMedium)

                DebugSection("여행")
                Text(answerText.value, style = MaterialTheme.typography.bodyLarge)
                if (reasonText.value.isNotEmpty()) Text(reasonText.value, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showDebug.value = false; openRiderPicker() }, enabled = !recordingToday.value) { Text("여행 시작") }
                    OutlinedButton(onClick = {
                        policy.endTrip()
                        policy.clearSkipPlaces()     // "여행 아니에요" 했던 곳도 잊기 (리허설 다시 할 때)
                        askingTrip.value = false
                        naviTrip = null
                        refreshPolicyText()
                        flowStatus.value = "여행 상태를 처음으로 돌렸어요"
                    }) { Text("여행 상태 초기화") }
                    Button(onClick = { resetForDemo() }) { Text("시연 준비 (기록 싹 지우기)") }
                    OutlinedButton(onClick = { pretendAtHome() }) { Text("지금 집에 있는 걸로 치기") }
                }
                Text(
                    if (VoiceRules.DEMO_FRESH_START) "시연 모드: 앱을 켤 때마다 지난 기록을 지우고 시작해요 (VoiceRules.DEMO_FRESH_START)"
                    else "기록이 앱에 그대로 남아요 (실제 서비스 설정)",
                    style = MaterialTheme.typography.bodySmall
                )

                DebugSection("차가 묻는다")
                Text(flowStatus.value, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { startMemoryFlow() }) { Text("질문·대답 바로 해 보기") }
                Text(
                    "현재 위치 ${locationText.value} · 지나온 길 ${dayRoutes.value.size}일치 (가장 최근 ${dayRoutes.value.firstOrNull()?.points?.size ?: 0}점)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(revisitStatus.value, style = MaterialTheme.typography.bodyMedium)
                Text("남긴 이야기 ${memories.value.size}개", style = MaterialTheme.typography.titleMedium)
                memories.value.takeLast(5).reversed().forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(m.id)) +
                                (if (m.rainy) " · 비 오던 날" else "") +
                                (if (m.lat != null && m.lng != null) String.format(java.util.Locale.KOREA, " · %.4f, %.4f", m.lat, m.lng) else " · 위치 없음") +
                                (if (m.text.isNotBlank()) "\n\"${m.text}\"" else " · (글자 없음)"),
                            modifier = Modifier.width(420.dp)
                        )
                        OutlinedButton(onClick = { say(m.text, clipFor(m)) {} }) { Text("읽어 주기") }
                    }
                }

                DebugSection("사진 모으기 (QR)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { requestTripQr() }, enabled = !tripUploadBusy.value) {
                        Text(if (tripUploadBusy.value) "만드는 중…" else "QR 만들기")
                    }
                    if (tripUploadStatus.value.isNotEmpty()) Text(tripUploadStatus.value, style = MaterialTheme.typography.bodyMedium)
                }
                tripQrBitmap.value?.let { bmp ->
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "사진 업로드 QR", modifier = Modifier.width(240.dp))
                }

                DebugSection("Pleos 음성 SDK (TTS·STT)")
                Text("에뮬레이터엔 엔진이 없고(ON_DEVICE_ENGINE_ERROR) 서버는 Coming Soon → 시연은 DemoSpeech 예시로 대신해요", style = MaterialTheme.typography.bodySmall)
                Text(pleosStatus.value, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pleos.speak("이번 여행, 기록해 둘까요?") }) { Text("차가 말하기") }
                    OutlinedButton(onClick = {
                        pleos.listen { heard, _ ->
                            runOnUiThread {
                                val yn = pleos.yesNo(heard)
                                pleosStatus.value = (listOf("들은 말: \"$heard\" → " + when (yn) { true -> "응"; false -> "아니"; null -> "잘 모르겠어요" }) + pleosStatus.value.lines()).take(6).joinToString("\n")
                            }
                        }
                    }) { Text("말로 대답하기") }
                    OutlinedButton(onClick = { pleos.initTts(); pleos.initStt() }) { Text("다시 연결") }
                }
                // 11/11 시험: 앱 등록 없이 차 안 엔진(ON_DEVICE)으로 되는지
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pleos.initStt(ai.pleos.playground.stt.constant.Mode.ON_DEVICE) }) { Text("STT 연결 (ON_DEVICE)") }
                    Button(onClick = { pleos.initStt(ai.pleos.playground.stt.constant.Mode.HYBRID) }) { Text("STT 연결 (HYBRID)") }
                    Button(onClick = { pleos.initTtsOnDevice() }) { Text("TTS 연결 (ON_DEVICE)") }
                }
                // 11/11: Pleos LLM SDK — 휴게소 제안 문장 만들기 시험
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pleos.initLlm(ai.pleos.playground.llm.constant.Mode.SERVER) }) { Text("LLM 연결 (SERVER)") }
                    Button(onClick = { pleos.initLlm(ai.pleos.playground.llm.constant.Mode.ON_DEVICE) }) { Text("LLM 연결 (ON_DEVICE)") }
                    OutlinedButton(onClick = {
                        pleos.generate(suggestPrompt("천안삼거리휴게소", "작년 10월", DemoSpeech.LAST_YEAR_TEXT)) { r ->
                            runOnUiThread { pleosStatus.value = (listOf("LLM 시험 → " + (r ?: "실패 (정해 둔 문장 틀을 씀)")) + pleosStatus.value.lines()).take(10).joinToString("\n") }
                        }
                    }) { Text("LLM 제안 문장 시험") }
                }
            }
        }
    }

    /**
     * 탑승자를 고르고 출발할 때.
     * 오늘을 기록하는 날로 켜고, 탑승 인원을 기억하고, 사진 모으기 QR을 바로 띄운다.
     */
    private fun startTripWithRiders() {
        val picked = ridersPicked.value
        ridersStore.todayRiders = picked
        ridersStore.lastRiders = picked
        riderNames.value = ridersStore.namesOf(picked)
        passengers = picked.size
        val wasActive = policy.tripActive
        // 집 위치를 아직 모르는데, 네비 목적지를 보고 출발 전에 시작한 여행이면 → 지금 자리를 집으로
        if (pickerAuto && policy.home == null) hereNow()?.let { policy.setHome(it.latitude, it.longitude) }
        policy.startTrip(odometerKm)
        livePoints.clear()
        refreshPolicyText()
        riderPicker.value = false
        if (pickerForScenario) {
            scenarioStage.value = 1          // 기어를 D로 내리면 출발
            scenarioLastGear = VehicleGear.GEAR_PARK
            lastYearPlayed = false
            lastYearWaited = 0L
            pastSpotShown = false
            jumpTo(TripScenario.home)        // 시나리오는 집에서 출발한다
            parkedAt = TripScenario.home     // 집 앞이라 첫 출발 때는 묻지 않는다
            if (!tripUploadBusy.value) requestTripQr()   // 사진 QR은 출발할 때 새로 만든다 → 집 도착 때 기다리지 않고 바로 뜬다
            flowStatus.value = "${riderNames.value}와 함께 출발해요. 기어를 D로 내리면 출발해요"
        } else {
            // 출발하는 자리를 지금 자리로 다시 적는다 (예전 값이 남아 있으면 집 앞인데도 물어본다)
            parkedAt = hereNow()?.let { it.latitude to it.longitude } ?: policy.home
            // 시연인데 집이 아닌 데서 시작하면 뒤에 다 어긋난다 → 바로 알려 준다
            val away = parkedAt?.let { (la, ln) -> policy.home?.let { h -> LocationTracker.distanceM(la, ln, h.first, h.second) } } ?: 0f
            if (away > 5000f) flowStatus.value =
                "⚠ 지금 자리가 집에서 ${(away / 1000).toInt()}km 떨어져 있어요. 시연이라면 먼저 'adb emu geo fix 126.9707 37.5547' 로 서울역으로 옮기고 시작하세요"
            if (!wasActive) { prompts.play("v_trip_on"); requestTripQr() }   // 사진 QR은 미리 만들어 메뉴바 버튼에 둔다
            flowStatus.value = "${riderNames.value}와 함께하는 여행을 기록해요. 집에 돌아오면 마무리할게요"
        }
        pickerForScenario = false
        pickerAuto = false
    }

    // ── 가상 여행 시나리오 ──────────────────────────────
    //  기어로만 진행한다. D = 달린다, P = 그 구간의 목적지에 도착한 것으로 본다.
    //  주행 중에는 화면이 잠기므로, 눌러야 하는 카드는 전부 P 일 때만 띄운다.

    /** 기어가 바뀔 때 시나리오를 한 칸 움직인다 */
    private var scenarioLastGear: VehicleGear? = null

    private fun scenarioOnGear(g: VehicleGear?) {
        if (scenarioStage.value == 0) return
        if (g == scenarioLastGear) return          // 같은 기어 신호가 두 번 오면 무시 (한 단계에 두 번 움직이지 않게)
        scenarioLastGear = g
        val driving = g == VehicleGear.GEAR_DRIVE
        val parked = g == VehicleGear.GEAR_PARK
        // 아직 그 구간을 다 못 갔는데 P 를 넣었으면 도착이 아니다 (다음 장소로 순간이동하지 않게)
        if (parked && !legArrived && scenarioStage.value in 2..8) {
            flowStatus.value = "아직 도착 전이에요 · ${legLeftKm.value}km 남았어요 (D 로 더 달리세요)"
            return
        }
        when (scenarioStage.value) {
            1 -> if (driving) {                       // 집에서 출발 → 천안삼거리휴게소로
                scenarioStage.value = 2
                flowStatus.value = "천안삼거리휴게소로 가는 중…"
                // ① 휴게소 3km 앞까지 쭉 달린다
                driveAlong(TripScenario.legOut1, TripScenario.LEG1_MS, pauseWhileTalking = false, finalLeg = false) {
                    if (scenarioStage.value != 2) return@driveAlong
                    // ② 지도를 휴게소로 확대하고 작년 목소리를 꺼낸다
                    zoomTo.value = TripScenario.cheonan
                    flowStatus.value = "천안삼거리휴게소 3km 앞 · 작년 목소리"
                    playLastYearVoice()
                    // ③ 마지막 3km 는 천천히 — 목소리가 끝날 때쯤 휴게소에 닿는다
                    driveAlong(TripScenario.legOut1Slow, TripScenario.LEG1_SLOW_MS, pauseWhileTalking = false) { arriveLeg("천안삼거리휴게소") }
                }
            }
            2 -> if (parked) {                        // 천안삼거리휴게소 도착
                scenarioStage.value = 3
                jumpTo(TripScenario.cheonan)
                arrivalPlace.value = "천안삼거리휴게소" to "작년에 그 호두과자를 사 먹은 곳이에요. 쉬었다 가요."
            }
            3 -> if (driving) {                       // 대전 성심당으로
                scenarioStage.value = 4
                zoomTo.value = null                   // 확대 풀고 다시 길 전체를 본다
                arrivalPlace.value = null
                flowStatus.value = "대전 성심당으로 가는 중…"
                driveAlong(TripScenario.legOut2, TripScenario.LEG2_MS) { arriveLeg("대전 성심당") }
            }
            4 -> if (parked) {                        // 대전 성심당 도착
                scenarioStage.value = 5
                jumpTo(TripScenario.daejeon)
                arrivalPlace.value = "대전 성심당" to "오늘 점심은 여기예요. 줄은 좀 서야 한대요."
            }
            5 -> if (driving) {                       // 충주호로
                scenarioStage.value = 6
                arrivalPlace.value = null
                flowStatus.value = "충주호로 가는 중…"
                driveAlong(TripScenario.legOut3, TripScenario.LEG3_MS) { arriveLeg("충주호") }
            }
            6 -> if (parked) {                        // 충주호 도착
                scenarioStage.value = 7
                jumpTo(TripScenario.chungju)
                arrivalPlace.value = "충주호" to "오늘의 마지막 코스예요. 천천히 둘러보세요."
            }
            7 -> if (driving) {                       // 집으로 출발 (돌아오는 길)
                scenarioStage.value = 8
                arrivalPlace.value = null
                pastSpotShown = false
                flowStatus.value = "집으로 돌아가는 중…"
                driveAlong(TripScenario.legHome, TripScenario.LEG_HOME_MS) { arriveLeg("집") }
            }
            8 -> if (parked) {                        // 집 도착 → 여행 기록 + 요약 + 사진 QR
                scenarioStage.value = 9
                jumpTo(TripScenario.home)
                finishScenarioTrip()
            }
        }
    }

    /**
     * 촬영용 신호 (화면에는 안 나옴). 터미널에서 `adb logcat -s FV_CUE` 로 보면서
     * "언제 속도를 0 으로 줄이고 P 를 넣을지" 맞춘다 — SHOW_STATUS 를 꺼도 보인다.
     */
    private fun cue(msg: String) { android.util.Log.i("FV_CUE", msg) }

    private fun arriveLeg(place: String) {
        cue("✅ $place 도착 — 이제 속도 0 → P")
        legArrived = true
        legLeftKm.value = 0
        flowStatus.value = "$place 에 거의 다 왔어요 · P 로 바꾸면 도착이에요"
    }

    /**
     * 경로를 따라 위치를 일정한 속도로 넣어 준다 (지도에 가는 길이 그려진다).
     * durationMs 동안 route 를 다 지나간다. lastYearAtMs 가 있으면 그 시점에 작년 목소리를 꺼낸다.
     * 차가 말하거나 들려주는 중에는 가상 차를 잠깐 세운다 (말이 끊기지 않게).
     */
    private fun driveAlong(route: List<Pair<Double, Double>>, durationMs: Long = 10_000L, pauseWhileTalking: Boolean = true,
                           finalLeg: Boolean = true, onEnd: (() -> Unit)? = null) {
        snapLeg = null
        if (route.isEmpty()) { onEnd?.invoke(); return }
        val tickMs = 100L
        // 점이 너무 많으면(실제 도로 경로는 400m마다라 수백 개) 솎아 낸다 — 정한 시간 안에 지나가도록
        val maxPts = (durationMs / tickMs).toInt().coerceAtLeast(2)
        val pts = if (route.size <= maxPts) route
                  else List(maxPts) { k -> route[k * (route.size - 1) / (maxPts - 1)] }
        legArrived = false                                            // 새 구간 시작 — 다 달리기 전엔 도착이 아니다
        // 남은 거리를 미리 재 둔다 (화면에 "N km 남았어요")
        val leftM = DoubleArray(pts.size)
        for (k in pts.size - 2 downTo 0) {
            leftM[k] = leftM[k + 1] + LocationTracker.distanceM(pts[k].first, pts[k].second, pts[k + 1].first, pts[k + 1].second)
        }
        val cruise = VoiceRules.CRUISE_KMH
        val decelPts = (pts.size * 0.15).toInt().coerceAtLeast(3)      // 도착 15% 전부터 속도를 줄인다
        var i = 0
        var ms = 0L
        var progress = 0.0
        // 가속하는 동안 느려지는 만큼 미리 보태서, 구간 시간이 대체로 지켜지게 한다
        val perTick = pts.size.toDouble() / ((durationMs - VoiceRules.ACCEL_MS / 2) / tickMs).coerceAtLeast(1L)
        var cuedKm = -1                                                // 촬영 신호: 남은 거리가 바뀔 때만 한 번씩
        var finished = false
        legLeftM = leftM.firstOrNull() ?: 0.0

        fun feed(p: Pair<Double, Double>) {
            demoLoc = Location("scenario").apply {
                latitude = p.first; longitude = p.second; time = System.currentTimeMillis(); accuracy = 5f
            }
            onLocation(demoLoc!!)
        }
        fun finish() {
            if (finished) return
            finished = true
            snapLeg = null
            speedKmh.value = 0
            onEnd?.invoke()
        }
        // 시뮬레이터 속도를 따르면, 목적지 앞에서 감속하는 동안(30km/h 아래) 차가 몇백 m 앞에서 멈춘다.
        // 그때 P 를 넣으면 남은 길을 한 번에 채우고 도착으로 친다 (onGearChanged 가 부른다)
        if (finalLeg) snapLeg = {
            while (i < pts.size) { feed(pts[i]); i++ }
            legLeftM = 0.0
            finish()
        }

        /**
         * 지금 속도(km/h). 주행거리계가 늘어나면 그걸로 구한 실제 속도를 쓰고,
         * 안 늘어나면(지금 에뮬레이터가 그렇다) 앱이 만든 곡선을 쓴다:
         * 출발하면 올라가고, 도착이 가까우면 줄어든다.
         */
        fun speedNow(): Int {
            SimSpeed.kmh()?.let { return it }                       // ① 시뮬레이터가 알려 주는 속도 (0 이면 멈춰 있는 것)
            CarSpeed.kmh(this@MainActivity)?.let { return it }      // ② 차에서 바로
            if (System.currentTimeMillis() - realSpeedAt < VoiceRules.REAL_SPEED_HOLD_MS) return realSpeed   // ② 주행거리로 계산
            val left = pts.size - i
            return when {
                ms < VoiceRules.ACCEL_MS -> (cruise * ms / VoiceRules.ACCEL_MS).toInt()
                left <= decelPts -> (cruise * left / decelPts).coerceAtLeast(VoiceRules.DRAW_FROM_KMH)
                else -> cruise
            }
        }

        fun tick() {
            if (finished) return
            if (scenarioStage.value == 0) { speedKmh.value = 0; snapLeg = null; return }
            if (i >= pts.size) { finish(); return }
            // 묻고 듣는 동안에는 가상 차를 세운다. 작년 목소리는 달리면서 들려주므로 세우지 않는다
            if (pauseWhileTalking && flowRunning) { speedKmh.value = 0; window.decorView.postDelayed({ tick() }, 200L); return }
            val sp = speedNow()
            speedKmh.value = sp
            legLeftM = leftM[i.coerceAtMost(pts.size - 1)]
            legLeftKm.value = (legLeftM / 1000.0 + 0.5).toInt()
            if (legLeftKm.value != cuedKm && legLeftKm.value in 1..5) { cuedKm = legLeftKm.value; cue("남은 ${legLeftKm.value}km") }
            ms += tickMs
            // 30km/h 를 넘어야 경로가 그려지기 시작하고, 그 뒤로는 속도에 비례해서 나아간다
            if (sp >= VoiceRules.DRAW_FROM_KMH) {
                progress += perTick * sp / cruise
                val upto = progress.toInt().coerceAtMost(pts.size)
                while (i < upto) { feed(pts[i]); i++ }
            }
            window.decorView.postDelayed({ tick() }, tickMs)
        }
        tick()
    }

    /**
     * 작년에 남긴 목소리를 꺼내 들려준다 (시연: 천안삼거리휴게소 가는 길).
     * 위치가 아니라 시간으로 부르기 때문에 에뮬레이터에서도 확실하게 나온다. 들려줄 게 없으면 false
     */
    private var lastYearPlayed = false
    private var lastYearWaited = 0L

    private fun playLastYearVoice(): Boolean {
        if (lastYearPlayed) return false           // 한 여행에 한 번만
        if (flowRunning) {                         // 차가 말하는 중이면 끝날 때까지 기다린다 (겹치지 않게)
            if (lastYearWaited < 15_000L) {
                lastYearWaited += 300L
                window.decorView.postDelayed({ if (scenarioStage.value in 2..3) playLastYearVoice() }, 300L)
            }
            return false
        }
        // 작년 그날 이야기를 찾는다 (천안삼거리휴게소에 가장 가까운, 이번 여행 전에 남긴 글자)
        val since = tripSince()
        val target = memories.value.filter { it.id < since && it.lat != null && it.lng != null && it.text.isNotBlank() }
            .minByOrNull { LocationTracker.distanceM(TripScenario.cheonan.first, TripScenario.cheonan.second, it.lat!!, it.lng!!) }
        if (target == null) { flowStatus.value = "작년 이야기가 없어요 (seedDemoMemories 확인)"; return false }
        revisitSameTrip.value = false; revisitMemory.value = target     // 작년 사진 + 그때 한 말 카드
        lastYearPlayed = true
        cue("🔊 작년 목소리 시작 — 속도 60~80 유지, 끝까지 달리기")
        flowRunning = true
        focus.duck()
        prompts.stop(); pleos.stopSpeaking()       // 남아 있는 소리가 있으면 끊고 들려준다
        revisitStatus.value = "작년 천안삼거리휴게소 · 그때 이야기를 읽어 주는 중…"
        var released = false
        val done = {
            if (!released) {
                released = true
                if (!VoiceRules.SUGGEST_STOP) revisitStatus.value = "읽어 드렸어요 · 작년 10월 4일 천안삼거리휴게소에서 남긴 이야기"
                flowStatus.value = "작년 이야기를 들려줬어요 · 천안삼거리휴게소로 가는 중…"
                revisitMemory.value = null
                flowRunning = false
                focus.release()
            }
        }
        // 차가 먼저 제안: "곧 천안삼거리휴게소예요. 작년에 여기서 호두과자가… 들렀다 갈까요?"
        //  → 삐 → 가족 "응!" → 네비에 경유지 추가 → "좋아요. 천안삼거리휴게소를 경유지로 넣었어요."
        if (VoiceRules.SUGGEST_STOP) {
            suggestStop(target, "천안삼거리휴게소", TripScenario.cheonan.first, TripScenario.cheonan.second) { done() }
        } else {
            val sentence = replaySentence(target, sameTrip = false)
            say(sentence, if (sentence == DemoSpeech.LAST_YEAR_SENTENCE) DemoSpeech.LAST_YEAR_CLIP else null) { done() }
        }
        // 안전장치: 소리가 안 끝나도 45초 뒤엔 풀어 준다 (질문이 막히지 않게)
        window.decorView.postDelayed({ done() }, 45_000L)
        return true
    }

    private fun jumpTo(p: Pair<Double, Double>) {
        demoLoc = Location("scenario").apply {
            latitude = p.first; longitude = p.second; time = System.currentTimeMillis(); accuracy = 5f
        }
        onLocation(demoLoc!!)
    }

    /**
     * 돌아오는 길에 예전 여행에서 들렀던 곳을 지나면 그때 기록을 꺼내 말해 준다.
     * (여행 탭에 쌓여 있는 지난 기록을 근거로 한다 — 예: 작년 강릉 여행의 덕평휴게소 호두과자)
     */
    private fun checkPastSpot(lat: Double, lng: Double) {
        if (scenarioStage.value != 8 || pastSpotShown || flowRunning) return
        val near = Journal.spots.firstOrNull { sp ->
            sp.tripId != "live" && !sp.tripId.startsWith("live-") &&
                LocationTracker.distanceM(lat, lng, sp.lat, sp.lng) <= 3000f
        } ?: return
        pastSpotShown = true
        pastSpot.value = near
        flowStatus.value = "${near.name} · 예전에 들렀던 곳이에요"
        // 소리는 내지 않는다: 이 카드엔 들려줄 목소리가 없어서 "이런 말을 남기셨어요." 뒤가 비어 버린다 (11/10)
    }

    /** 집에 도착: 이번 여행을 기록으로 남기고, 사진 모으기 QR을 띄운다 */
    private fun finishScenarioTrip() {
        val (trip, raw) = TripScenario.buildTrip(passengers ?: ridersPicked.value.size, isRainy)
        // 그 자리에서 남긴 이야기(글자)를 장소 카드에 붙인다 → 집 도착 정리 화면과 장소 카드에 같이 보인다
        val since = tripSince()
        val spots = raw.map { sp ->
            val v = memories.value.filter { m ->
                m.id >= since && m.lat != null && m.lng != null &&
                    LocationTracker.distanceM(sp.lat, sp.lng, m.lat!!, m.lng!!) <= 1000f
            }.maxByOrNull { it.id }?.id
            if (v == null) sp else sp.copy(voice = v)
        }
        Journal.addTrip(trip, spots)
        policy.endTrip()
        refreshPolicyText()
        flowStatus.value = "오늘 여행을 기록했어요 · ${trip.distanceKm}km · ${trip.driveTime}"
        arrivalPlace.value = "집" to "오늘 ${spots.size}곳, ${trip.distanceKm}km 다녀왔어요. 여행 탭에 기록을 남겼어요."
        if (prompts.has("v_welcome_home")) prompts.play("v_welcome_home")
        if (tripQrBitmap.value == null && !tripUploadBusy.value) requestTripQr()   // 출발할 때 만든 QR 그대로 (실패했으면 다시)
        if (!summarizeTrip(trip)) qrCard.value = true    // 정리 화면 → 끝나면 QR (정리할 게 없으면 바로 QR)
    }

    /** 시나리오를 처음으로 */
    private fun resetScenario() {
        if (policy.tripActive) { policy.endTrip(); refreshPolicyText() }
        pickerForScenario = false
        pickerAuto = false
        scenarioStage.value = 0
        livePoints.clear()
        scenarioLastGear = null
        waypoint.value = null
        legArrived = false
        legLeftKm.value = 0
        legLeftM = 0.0
        snapLeg = null
        zoomTo.value = null
        lastYearPlayed = false
        lastYearWaited = 0L
        arrivalPlace.value = null
        pastSpot.value = null
        pastSpotShown = false
        qrCard.value = false
        demoLoc = null
        ridersStore.clearToday()
        ridersPicked.value = emptySet()
        riderNames.value = ""
    }

    // ── 여행 시작·끝 ─────────────────────────────────────────
    //  ① 출발 전 P: 네비 목적지가 여행 같으면 → 탑승자 고르기 카드 (onNaviDestination)
    //  ② 네비 없이 출발: 집에서 30km 넘게 와서 처음 P → "여행 중이신 것 같아요" (maybeAskTrip)
    //  ③ 버튼: 오른쪽 아래 "여행 시작"
    //  끝: 집 근처(300m)에서 P (endTripIfHome), 또는 7일 넘게 집에 안 오면 자동 마감
    //  "아니요/나중에"라고 하면 집에 돌아올 때까지 다시 안 묻는다

    /** 탑승자 고르기 카드를 연다. 지난번에 같이 간 사람을 미리 골라 둔다 */
    private fun openRiderPicker(note: String = "", scenario: Boolean = false, auto: Boolean = false) {
        pickerForScenario = scenario
        pickerAuto = auto
        pickerNote.value = note
        ridersPicked.value = ridersStore.lastRiders
        riderPicker.value = true
    }

    /** 탑승자 카드에서 "나중에" */
    private fun skipRiderPicker() {
        riderPicker.value = false
        if (pickerAuto && !policy.tripActive) {
            policy.declined = true          // 차가 먼저 물어본 거면: 이번엔 여행 아님 → 집에 올 때까지 안 묻기
            refreshPolicyText()
            flowStatus.value = "알겠어요. 집에 돌아올 때까지 묻지 않을게요"
        }
        pickerForScenario = false
        pickerAuto = false
    }

    /** 네비(Pleos NaviHelper) 연결. 연결되면 집·회사 즐겨찾기와 최근 목적지를 받아 둔다 */
    private fun startNavi() {
        try {
            val n = NaviHelper(applicationContext)
            navi = n
            n.addListener(naviListener)
            n.initialize(
                onConnected = {
                    runOnUiThread {
                        naviStatus.value = "네비 연결됨"
                        safeNavi { n.getBookmarkInfo() }
                        safeNavi { n.getRecentDestinationInfo() }
                        safeNavi { n.getDestinationInfo() }   // 앱보다 먼저 목적지를 찍어 둔 경우
                        startNaviLocationPolling()
                    }
                },
                onFailed = { e -> runOnUiThread { naviStatus.value = "네비 연결 실패: ${e.message}" } }
            )
        } catch (e: Exception) {
            naviStatus.value = "네비 연결 실패: ${e.message}"
        }
    }

    /**
     * 주행 중 위치 받기 (방법 2).
     * 앱이 뒤에 있으면 안드로이드가 GPS를 몇 분에 한 번으로 줄여서, 휴게소를 지나가며
     * 작년 목소리를 들려줄 타이밍을 놓친다. 네비는 차 서비스라 뒤에 있어도 답해 주므로,
     * 여행 중에는 네비에 "지금 어디예요?"를 주기적으로 물어본다.
     */
    private fun startNaviLocationPolling() {
        if (naviPolling) return
        naviPolling = true
        fun tick() {
            if (isFinishing || isDestroyed) { naviPolling = false; return }
            if (policy.tripActive || scenarioStage.value != 0) safeNavi { navi?.getCurrentLocationInfo() }
            window.decorView.postDelayed({ tick() }, VoiceRules.NAVI_POLL_MS)
        }
        tick()
    }

    /** 네비가 알려 준 지금 위치를 GPS 위치와 똑같이 처리한다 (지나온 길 + 예전 목소리 들려주기) */
    private fun onNaviLocation(lat: Double, lng: Double) {
        if (demoLoc != null) return                       // 시연 모드에서는 넣어 준 위치를 쓴다
        if (lat == 0.0 && lng == 0.0) return
        naviLocText.value = String.format(java.util.Locale.KOREA, "네비 위치: %.5f, %.5f (%s)", lat, lng,
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date()))
        val l = Location("navi").apply {
            latitude = lat; longitude = lng; time = System.currentTimeMillis(); accuracy = 20f
        }
        lastNaviLoc = l
        onLocation(l)
    }

    /**
     * Pleos 위치 SDK(FusedLocation)를 쓸 수 있는지 확인 (방법 1).
     * 쓸 수 있으면 ProximityAlert 로 "천안삼거리휴게소 1km 안에 들어오면 알려 줘"를 차에 맡길 수 있다
     * → 위치를 계속 받지 않아도 되고, 앱이 뒤에 있어도 확실하다.
     */
    @SuppressLint("MissingPermission")
    private fun checkFusedLocation() {
        val lines = mutableListOf<String>()
        fun show() = runOnUiThread { fusedStatus.value = lines.joinToString("\n") }
        try {
            val f = fused ?: FusedLocation(applicationContext).also { fused = it }
            val ok = f.isSupported()
            lines += if (ok) "isSupported = true (이 차에 위치 서비스가 있어요)" else "isSupported = false (이 차엔 없어요 → 방법 2로)"
            show()
            if (!ok) return
            f.initialize()
            f.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    lines += if (location == null) "마지막 위치: 없음" else "마지막 위치: ${location.latitude}, ${location.longitude}"
                    show()
                }
                override fun onError(exception: Exception) { lines += "위치 오류: ${exception.message}"; show() }
            })
            // 천안삼거리휴게소(작년 목소리 자리) 1km 반경에 들어오면 알려 달라고 걸어 본다
            val e1 = Journal.spots.firstOrNull { it.id == "e0" }
            if (e1 != null) f.addProximityAlert(
                ProximityAlertRequest(e1.lat, e1.lng, 1000f, setOf(ProximityTransition.ENTER)),
                object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) { lines += "천안삼거리휴게소 1km 알림 등록됨 ($alertId) → 방법 1 쓸 수 있어요"; show() }
                    override fun onTransition(event: ProximityAlertEvent) {
                        lines += "알림 왔어요: ${event.transition}"; show()
                        runOnUiThread { onNaviLocation(e1.lat, e1.lng) }
                    }
                    override fun onError(exception: Exception) { lines += "알림 등록 실패: ${exception.message}"; show() }
                }
            )
        } catch (e: Exception) {
            lines += "확인 실패: ${e.message}"
            show()
        }
    }

    private fun safeNavi(block: () -> Unit) {
        try { block() } catch (e: Exception) { naviStatus.value = "네비 호출 오류: ${e.message}" }
    }

    /** 네비 즐겨찾기: 집은 (아직 없으면) 집 위치로, 회사는 "안 묻는 곳"으로 */
    private fun onNaviBookmarks(list: List<BookmarkInfo>) {
        list.firstOrNull { it.category == BookmarkType.HOME }?.let { h ->
            if (policy.home == null) { policy.setHome(h.latitude, h.longitude); refreshPolicyText() }
        }
        list.firstOrNull { it.category == BookmarkType.OFFICE }?.let { o -> policy.office = o.latitude to o.longitude }
        bookmarkPicks.value = list.filter { it.category == BookmarkType.DEFAULT }.map { PlacePick(it.name, it.address, it.latitude, it.longitude) }
    }

    /** ① 네비 목적지를 보고 여행인지 판단한다 */
    private fun onNaviDestination(d: DestinationInfo) {
        naviStatus.value = "네비 목적지: ${d.name}"
        naviDest = d
        if (scenarioStage.value != 0) return     // 예시 여행 중에는 끼어들지 않는다
        if (policy.tripActive || policy.declined || riderPicker.value) return
        val firstTime = recentDests.none { LocationTracker.distanceM(it.latitude, it.longitude, d.latitude, d.longitude) <= 300f }
        val reasons = policy.naviReasons(d.latitude, d.longitude, hereNow(), firstTime, passengers)
        reasonText.value = "네비 목적지 ${d.name}: " + (if (reasons.isEmpty()) "여행 신호 없음" else reasons.joinToString(" · "))
        tripReasons.value = reasons
        if (!policy.isNaviTrip(reasons)) { naviTrip = null; return }
        naviTrip = d.name to reasons
        // 차가 서 있으면 바로, 달리는 중이면 다음에 P 에 놓을 때 묻는다 (주행 중엔 화면이 잠김)
        if (lastGear == VehicleGear.GEAR_PARK) showNaviTripPicker()
    }

    private fun showNaviTripPicker() {
        val t = naviTrip ?: return
        naviTrip = null
        if (policy.tripActive || policy.declined) return
        openRiderPicker(note = "${t.first}에 가시네요. 여행이면 함께 가는 사람을 골라 주세요", auto = true)
    }

    /** 따로 남길 때 미리 채울 이름: 네비 목적지 근처(1km)에 서 있으면 그 이름 */
    private fun suggestPlaceName(): String {
        val d = naviDest ?: return ""
        val here = hereNow() ?: return ""
        return if (LocationTracker.distanceM(here.latitude, here.longitude, d.latitude, d.longitude) <= 1000f) d.name else ""
    }

    /** 남기기 창의 [말로 남기기]: 삐 → Pleos STT 로 듣기 → 글자. 끝나면 임시 id, 못 알아들으면 null */
    private fun recordSpotVoice(onDone: (Long?) -> Unit) {
        if (flowRunning) { onDone(null); return }       // 차가 질문·읽어 주는 중이면 겹치지 않게
        flowRunning = true
        focus.pause()                       // 듣는 동안 음악 잠깐 멈춤
        val id = System.currentTimeMillis()
        prompts.play("v_beep") {
            listenFamily(DemoSpeech.SPOT_TEXT, null) { heard ->
                flowRunning = false
                focus.release()
                if (heard.isBlank()) onDone(null) else { pendingSpotText[id] = heard; onDone(id) }
            }
        }
    }

    /** 말로 남겼지만 저장하지 않은 글자 버리기 (다시 말하기, 취소) */
    private fun discardSpotVoice(id: Long) { pendingSpotText.remove(id) }

    /** 여행과 상관없이 지금 있는 곳을 따로 남긴다. 새 장소 id (위치를 모르면 null) */
    private fun addSoloSpot(kind: SpotKind, name: String, note: String, rating: Int?, pick: PlacePick?, voiceId: Long? = null): String? {
        // 네비에서 고른 곳이면 그 위치, 아니면 지금 차가 있는 곳
        val here = pick?.let { p -> Location("navi").apply { latitude = p.lat; longitude = p.lng } } ?: hereNow()
            ?: run { flowStatus.value = "아직 위치를 몰라서 남기지 못했어요"; return null }
        val now = java.util.Date()
        val s = Spot(
            id = "solo-${now.time}", tripId = Journal.SOLO, kind = kind, name = name,
            lat = here.latitude, lng = here.longitude,
            time = java.text.SimpleDateFormat("M월 d일 a h:mm", java.util.Locale.KOREA).format(now),
            note = note, photo = null, rating = rating, voice = voiceId
        )
        // 말로 남긴 글자는 그 장소 위치로 이야기 목록에 넣는다 → 다음에 근처를 지나면 차가 읽어 준다
        if (voiceId != null) {
            val text = pendingSpotText.remove(voiceId).orEmpty()
            if (text.isNotBlank()) memories.value = store.add(Memory(voiceId, "", isRainy, 0, here.latitude, here.longitude, "spot", text))
        }
        Journal.addSpot(s)
        spotStore.save(Journal.spots.filter { it.tripId == Journal.SOLO })
        flowStatus.value = "${kind.label} '${name}'을(를) 남겼어요"
        return s.id
    }

    /**
     * 사람이 직접 여행을 그만할 때 (오른쪽 아래 "여행 기록 중 · 끝내기").
     * discard = false: 지금까지 기록은 남기고 마감 · true: 이번 여행의 길과 목소리를 지우고 취소
     * 어느 쪽이든 집에 돌아올 때까지 다시 묻지 않는다.
     */
    private fun stopTripByUser(discard: Boolean) {
        if (scenarioStage.value != 0) { resetScenario(); flowStatus.value = "예시 여행을 멈췄어요"; return }
        val since = policy.tripStartedAt
        val trip = if (discard) null else finishRealTrip()
        policy.endTrip()
        policy.declined = true
        ridersStore.clearToday()
        if (discard) {
            routes.dropToday()
            dayRoutes.value = routes.load()
            if (since > 0L) memories.value = store.removeSince(since)
            flowStatus.value = "이번 여행 기록을 지웠어요. 집에 돌아올 때까지 다시 묻지 않을게요"
        } else {
            flowStatus.value = "여행을 마쳤어요" + (trip?.let { " · 여행 탭에 '${it.title}'을 남겼어요" } ?: "") + ". 집에 돌아올 때까지 다시 묻지 않을게요"
        }
        refreshPolicyText()
    }

    // ── 실제 여행 카드 ─────────────────────────────────────────

    /** 여행 중 P 를 넣으면: 달린 시간을 더하고, 멈춘 곳을 남기고, 네비에 지금 주소를 물어본다 */
    private fun recordTripStop() {
        // 시연 시나리오에서도 기록한다 — 쉬었다 간 자리가 있어야 지나온 길이 구간별로 나뉘고(색·실선) 점선이 끊긴다
        if (!policy.tripActive) return
        policy.addDriveMs(lastDriveMs)
        val here = hereNow() ?: return
        if (policy.isNearHome(here.latitude, here.longitude, 500f)) return
        policy.addStop(here.latitude, here.longitude, System.currentTimeMillis())
        safeNavi { navi?.getCurrentLocationInfo() }
    }

    /** 주소 → 도시 이름 ("대구광역시 중구 …" → "대구", "충청북도 청주시 …" → "청주") */
    private fun cityOf(addr: String): String? {
        val p = addr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (p.isEmpty()) return null
        val metro = Regex("(특별시|광역시|특별자치시)$")
        return if (metro.containsMatchIn(p[0])) p[0].replace(metro, "")
        else p.getOrNull(1)?.replace(Regex("(시|군)$"), "")?.takeIf { it.isNotEmpty() } ?: p[0]
    }

    /** 주소 → 짧은 장소 이름 ("대구 중구", "청주 청원구") */
    private fun shortPlace(addr: String): String? {
        val p = addr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val c = cityOf(addr) ?: return null
        val next = if (Regex("(특별시|광역시|특별자치시)$").containsMatchIn(p[0])) p.getOrNull(1) else p.getOrNull(2)
        return listOfNotNull(c, next).joinToString(" ")
    }

    /** 멈춘 곳 이름과 종류: 남긴 곳 → 예전 장소 → 네비 목적지·최근 목적지 → 주소 → "정차한 곳 N" */
    private fun nameStop(st: TripStop, n: Int): Pair<String, SpotKind> {
        fun near(lat: Double, lng: Double, m: Float) = LocationTracker.distanceM(st.lat, st.lng, lat, lng) <= m
        Journal.spots.firstOrNull { it.tripId == Journal.SOLO && near(it.lat, it.lng, 300f) }?.let { return it.name to it.kind }
        Journal.spots.firstOrNull { !it.tripId.startsWith("trip-") && near(it.lat, it.lng, 500f) }?.let { return it.name to it.kind }
        val navName = naviDest?.takeIf { near(it.latitude, it.longitude, 1000f) }?.name
            ?: recentPicks.value.firstOrNull { near(it.lat, it.lng, 1000f) }?.name
        val name = navName ?: shortPlace(st.address) ?: "정차한 곳 $n"
        return name to (if (name.contains("휴게소")) SpotKind.REST else SpotKind.STOP)
    }

    /**
     * 여행을 마칠 때: 지나온 길·멈춘 곳·거리·운전 시간·날씨·인원으로 여행 카드를 만들어 여행 탭에 넣고 저장한다.
     * 길이 거의 없으면(집 앞만 돌았으면) 만들지 않는다.
     */
    private fun finishRealTrip(): Trip? {
        val start = policy.tripStartedAt
        if (start <= 0L || scenarioStage.value != 0) return null
        val route = routes.pointsSince(start)
        if (route.size < 2) return null
        val now = System.currentTimeMillis()
        val id = "trip-$start"
        val timeFmt = java.text.SimpleDateFormat("M월 d일 a h:mm", java.util.Locale.KOREA)
        val stops = policy.tripStops().filter { !policy.isNearHome(it.lat, it.lng, 500f) }
        val named = stops.mapIndexed { k, st -> st to nameStop(st, k + 1) }
        val spots = named.mapIndexed { k, (st, nk) ->
            // 그 자리에서 남긴 목소리를 붙인다 (남긴 위치 = P 를 넣은 자리 = 정차한 곳). 여러 개면 마지막 것
            val v = memories.value.filter { m ->
                m.id >= start && m.lat != null && m.lng != null &&
                    LocationTracker.distanceM(st.lat, st.lng, m.lat, m.lng) <= 300f
            }.maxByOrNull { it.id }?.id
            Spot("$id-${k + 1}", id, nk.second, nk.first, st.lat, st.lng, timeFmt.format(java.util.Date(st.time)), "", null, voice = v)
        }
        // 거리: 차 주행거리계 차이, 없으면(에뮬레이터 등) 지나온 길 길이
        val routeKm = (1 until route.size).sumOf { i ->
            LocationTracker.distanceM(route[i - 1].first, route[i - 1].second, route[i].first, route[i].second).toDouble()
        } / 1000.0
        val odoKm = policy.tripStartOdo?.let { s -> odometerKm?.let { (it - s).toDouble() } }
        val km = if (odoKm != null && odoKm > 1.0) odoKm else routeKm
        if (km < 1.0) return null
        // 운전 시간: 기어 D 로 달린 시간. 가상 주행처럼 말도 안 되게 빠르면 거리로 어림 (시속 80km)
        val measuredH = policy.tripDriveMs / 3_600_000.0
        val h = if (measuredH > 0.0 && km / measuredH <= 130.0) measuredH else km / 80.0
        val driveTime = if (h < 1.0) "${(h * 60).toInt()}분" else "${h.toInt()}시간 ${((h - h.toInt()) * 60).toInt()}분"
        val dayMs = 86_400_000L
        val tz = java.util.TimeZone.getDefault()
        val days = ((now + tz.getOffset(now)) / dayMs - (start + tz.getOffset(start)) / dayMs).toInt()
        val nights = if (days <= 0) "당일치기" else "${days}박 ${days + 1}일"
        val people = ridersStore.todayRiders?.size?.takeIf { it > 0 } ?: passengers?.takeIf { it > 0 } ?: 1
        // 제목: 집에서 가장 먼 곳의 도시 이름 + "여행"
        val h0 = route.first()
        val far = named.maxByOrNull { (st, _) -> LocationTracker.distanceM(h0.first, h0.second, st.lat, st.lng) }
        val place = far?.let { (st, nk) -> cityOf(st.address) ?: nk.first.takeIf { nk.second != SpotKind.REST && !it.startsWith("정차한 곳") } }
        val title = (place ?: java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(start))) + " 여행"
        val trip = Trip(
            id = id, title = title,
            dateLabel = java.text.SimpleDateFormat("yyyy년 M월 d일", java.util.Locale.KOREA).format(java.util.Date(start)),
            nights = nights, weather = if (policy.tripRained) "비" else "맑음",
            distanceKm = (km + 0.5).toInt(), driveTime = driveTime, people = people, route = route,
            sortKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date(start)),
            riders = ridersStore.todayRiders?.let { ids -> ridersStore.roster.filter { it.id in ids }.map { it.name } } ?: emptyList()
        )
        Journal.addTrip(trip, spots)
        tripStore.save(Journal.trips.filter { it.id.startsWith("trip-") }, Journal.spots.filter { it.tripId.startsWith("trip-") })
        return trip
    }

    /** 집 근처에서 P → 여행 마감. 마감했으면 true */
    private fun endTripIfHome(): Boolean {
        if (scenarioStage.value != 0) return false
        val here = hereNow() ?: return false
        if (!policy.isNearHome(here.latitude, here.longitude)) return false
        if (!policy.tripActive) {
            // "아니요" 했던 나들이도 집에 오면 끝 → 다음엔 다시 물어본다
            if (policy.declined) { policy.declined = false; policy.todayAnswer = null; refreshPolicyText() }
            return false
        }
        if (!policy.leftHome) return false      // 아직 집을 떠난 적이 없음 (출발 전)
        val trip = finishRealTrip()
        policy.endTrip()
        ridersStore.clearToday()                // 다음 여행 인원에 섞이지 않게 (카드에는 이미 들어감)
        refreshPolicyText()
        flowStatus.value = "집에 도착했어요. 이번 여행을 마무리했어요"
        arrivalPlace.value = "집" to (trip?.let { "잘 다녀오셨어요. 여행 탭에 '${it.title}'을 남겼어요." } ?: "잘 다녀오셨어요. 다녀온 길을 지도에 남겼어요.")
        if (prompts.has("v_welcome_home")) prompts.play("v_welcome_home")
        // 여행 카드가 만들어졌으면 사진 올리기 QR 도 띄운다 (예시 여행과 같은 흐름)
        if (trip != null) {
            if (tripQrBitmap.value == null && !tripUploadBusy.value) requestTripQr()   // 여행 중에 만든 QR 그대로
            if (!summarizeTrip(trip)) qrCard.value = true                            // 요약할 게 있으면 요약 화면 → 끝나면 QR
        }
        return true
    }

    /**
     * 집 도착: 이번 여행에서 목소리를 남긴 곳들을 요약해 여행 카드에 넣는다.
     * "정리하고 있어요…" 화면에서 장소가 하나씩 체크되고, 끝나면 사진 QR 카드로 넘어간다.
     * 지금 요약 문장은 시연용 예시(assets/demo_summaries.json). 요약할 게 없으면 false
     */
    private fun summarizeTrip(trip: Trip): Boolean {
        val stops = Journal.spotsOf(trip.id).filter { it.voice != null }
        if (stops.isEmpty()) return false
        val result = summarizer.summarize(stops) ?: return false
        val names = stops.map { it.name }
        val lines = stops.map { result.perStop[it.id].orEmpty() }
        summaryUi.value = SummaryUi(names, lines, 0)
        fun step(i: Int) {
            window.decorView.postDelayed({
                if (summaryUi.value == null) return@postDelayed
                summaryUi.value = SummaryUi(names, lines, i + 1)
                if (i + 1 < names.size) step(i + 1)
                // 세 줄 요약을 다 읽을 시간을 준 뒤에 사진 QR 로 넘어간다
                else window.decorView.postDelayed({
                    applySummary(trip.id, result)
                    summaryUi.value = null
                    qrCard.value = true
                }, 6_800L)
            }, 1_400L)
        }
        step(0)
        return true
    }

    /** 요약을 카드에 넣고 저장한다 (장소 카드의 한 줄 기록 + 여행 카드의 한 줄) */
    private fun applySummary(tripId: String, r: TripSummaryResult) {
        r.perStop.forEach { (id, line) ->
            val i = Journal.spots.indexOfFirst { it.id == id }
            if (i >= 0) Journal.spots[i] = Journal.spots[i].copy(note = line)
        }
        val ti = Journal.trips.indexOfFirst { it.id == tripId }
        if (ti >= 0 && r.trip.isNotBlank()) Journal.trips[ti] = Journal.trips[ti].copy(summary = r.trip)
        tripStore.save(Journal.trips.filter { it.id.startsWith("trip-") }, Journal.spots.filter { it.tripId.startsWith("trip-") })
    }

    /**
     * 시연 준비용: "지금 집(서울역)에 서 있는 걸로" 쳐 준다.
     * 에뮬레이터 위치가 아직 엉뚱한 곳이어도 집에서 출발하는 흐름을 그대로 볼 수 있다.
     * (제대로 하려면 터미널에서  adb emu geo fix 126.9707 37.5547  로 에뮬레이터를 옮기는 게 맞다)
     */
    private fun pretendAtHome() {
        val h = policy.home ?: return
        parkedAt = h
        firstLoc = h
        movedSinceLaunch = false
        askingTrip.value = false
        val l = Location("demo").apply {
            latitude = h.first; longitude = h.second; time = System.currentTimeMillis(); accuracy = 5f
        }
        demoLoc = l
        onLocation(l)
        demoLoc = null          // 한 번만 넣고 실제 위치로 돌아간다
        flowStatus.value = "집(서울역)에 서 있는 걸로 쳤어요. 이제 여행 시작 → 기어 D"
    }

    /** 지금 위치. 시연 중에는 주입한 가짜 위치를, 평소엔 실제 GPS 를 쓴다. */
    private fun hereNow(): Location? =
        demoLoc ?: listOfNotNull(tracker?.last, lastNaviLoc).maxByOrNull { it.time }


    /**
     * 시연 준비: 지난 테스트에서 남은 기록을 싹 지우고 처음 상태로 되돌린다.
     * 영상에서 준비한 목소리가 늘 같은 순서(demo_rec_1 → 2 → 3)로 나오게 하려면 이게 필요하다.
     * 예시 여행 8개와 거기에 심어 둔 목소리(seedDemoMemories)는 그대로 남는다.
     */
    private fun resetForDemo() {
        policy.endTrip()                      // 여행 중 상태, "이번엔 여행 아님", 오늘 대답
        policy.clearSkipPlaces()
        ridersStore.clearToday()
        ridersStore.lastRiders = emptySet()   // 시연: 탑승자 카드가 아무도 안 고른 상태로 열리게
        ridersPicked.value = emptySet()
        riderNames.value = ""
        routes.clearAll()                     // 지나온 길
        dayRoutes.value = routes.load()
        memories.value = store.removeSince(0L)   // 녹음한 목소리 (예시 여행 것은 바로 뒤 seedDemoMemories 가 다시 넣는다)
        playedPrefs.edit().clear().apply()
        playedThisTrip.clear(); playedTripKey = ""; leftSpot.clear()
        parkedAt = null
        // 테스트로 만들어진 여행 카드·장소만 지운다 (예시 여행은 코드에 있어서 다시 살아난다)
        Journal.trips.removeAll { it.id.startsWith("trip-") || it.id.startsWith("live-") }
        Journal.spots.removeAll { it.tripId.startsWith("trip-") || it.tripId.startsWith("live-") || it.tripId == Journal.SOLO }
        tripStore.save(emptyList(), emptyList())
        spotStore.save(emptyList())
        tripQrBitmap.value = null; tripLink.value = null; tripUploadStatus.value = ""
        refreshPolicyText()
        flowStatus.value = "시연 준비 완료 — 지난 기록을 지웠어요"
    }

    /**
     * 예시 여행의 어느 장소에 "그때 남긴 이야기"를 심을지 정하는 표시 이름.
     * res/raw 에 이 이름의 파일이 있는 장소만 심는다 (소리는 이제 틀지 않고 파일이 있는지만 본다 — 지우면 그 장소 이야기도 빠진다).
     *
     *  demo_mem_<장소 id>      예: demo_mem_b2  →  해운대 해수욕장
     *  천안삼거리휴게소(e0)만 demo_2025_cheonan (글자는 DemoSpeech.LAST_YEAR_TEXT)
     *
     * 남긴 시각은 그 장소 카드에 적힌 날짜·시각 그대로 (여행 연도 + "9월 3일 오후 2:30").
     */
    private fun rawNameOf(spotId: String) = if (spotId == "e0") "demo_2025_cheonan" else "demo_mem_" + spotId

    /** 장소 카드의 "9월 3일 오후 2:30" + 여행 연도 → 밀리초. 못 읽으면 null */
    private fun spotTimeMillis(s: Spot): Long? {
        val year = Journal.trip(s.tripId)?.sortKey?.take(4)?.toIntOrNull() ?: return null
        val m = Regex("(\\d+)월\\s*(\\d+)일\\s*(오전|오후)\\s*(\\d+):(\\d+)").find(s.time) ?: return null
        val (mo, da, ampm, h, mi) = m.destructured
        val hour = (h.toInt() % 12) + (if (ampm == "오후") 12 else 0)
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"))
        c.clear()
        c.set(year, mo.toInt() - 1, da.toInt(), hour, mi.toInt(), 0)
        return c.timeInMillis
    }

    /**
     * 예시 여행의 "그때 남긴 이야기"를 심는다 (res/raw 에 예시 목소리 파일이 있던 장소만 — 예전과 같은 자리).
     * 이제 소리 파일은 안 쓰고 글자만: 천안삼거리휴게소(e0)는 DemoSpeech.LAST_YEAR_TEXT, 나머지는 장소 메모.
     */
    private fun seedDemoMemories() {
        var list = memories.value
        Journal.spots.indices.forEach { i ->
            val s = Journal.spots[i]
            if (s.tripId == Journal.SOLO || s.tripId.startsWith("live") || s.tripId.startsWith("trip-")) return@forEach
            if (resources.getIdentifier(rawNameOf(s.id), "raw", packageName) == 0) return@forEach
            val id = spotTimeMillis(s) ?: return@forEach
            val text = if (s.id == "e0") DemoSpeech.LAST_YEAR_TEXT else s.note
            if (text.isBlank()) return@forEach
            // 장소 카드에 붙인다 → 카드에 그때 이야기, 다시 지나갈 때 그날 사진
            if (s.voice != id) Journal.spots[i] = s.copy(voice = id)
            val old = list.firstOrNull { it.id == id }
            if (old == null || old.text != text) list = store.upsert(Memory(id, "", false, 0, s.lat, s.lng, "family", text))
        }
        memories.value = list
    }

    /** 기억 여러 개를 차례로 읽어 준다 (Pleos TTS, 시연·엔진 없음 → 글자만 보여 주고 넘어감) */
    private fun playMemories(list: List<Memory>) {
        val m = list.firstOrNull() ?: return
        if (m.text.isBlank()) { playMemories(list.drop(1)); return }
        flowStatus.value = "읽어 주는 중: \"${m.text}\""
        say(m.text, clipFor(m)) { runOnUiThread { playMemories(list.drop(1)) } }
    }

    /**
     * 시연에서 TTS 대신 틀 소리 이름 (scripts/demo_tts_lines.txt → make_voices.sh 가 만든 v_tts_*).
     * 시연 대답이면 v_tts_ansN. 없으면 null (장소별 소리는 11/12 에 뺐다 — 정차 중 화면은 글자로 읽는다)
     */
    private fun clipFor(m: Memory): String? =
        DemoSpeech.ANSWERS.indexOf(m.text).takeIf { it >= 0 }?.let { "v_tts_ans${it + 1}" }

    /** "작년 10월, 여기 오셨을 때 이런 이야기를 남기셨어요. …" — 차가 읽어 줄 문장 */
    private fun replaySentence(m: Memory, sameTrip: Boolean): String {
        if (sameTrip) return "아까 여기서 이런 이야기를 하셨어요. ${m.text}"
        return "${whenLabel(m)}, 여기 오셨을 때 이런 이야기를 남기셨어요. ${m.text}"
    }

    /** "올해 10월" / "작년 10월" / "2023년 10월" */
    private fun whenLabel(m: Memory): String {
        val then = java.util.Calendar.getInstance().apply { timeInMillis = m.id }
        val now = java.util.Calendar.getInstance()
        val y = then.get(java.util.Calendar.YEAR); val mo = then.get(java.util.Calendar.MONTH) + 1
        return when (now.get(java.util.Calendar.YEAR) - y) {
            0 -> "올해 ${mo}월"
            1 -> "작년 ${mo}월"
            else -> "${y}년 ${mo}월"
        }
    }

    // ───────── 다가갈 때 먼저 제안하기: "작년에 여기서 ○○라고 하셨죠. 들렀다 갈까요?" ─────────
    //  ① 시점: 앱 (예전 여행에서 이야기를 남긴 곳 몇 km 앞)
    //  ② 문장: Pleos LLM SDK (그때 남긴 말을 넣어 만든다) — 안 되면 정해 둔 틀
    //  ③ 말하기: Pleos TTS   ④ 대답: Pleos STT   ⑤ "응" → Pleos NaviHelper 경유지 추가

    private fun hasBatchim(w: String): Boolean {
        val c = w.trim().lastOrNull() ?: return false
        return c in '가'..'힣' && (c - '가') % 28 != 0
    }

    /** 이 이야기를 남긴 장소 이름 (여행 기록의 장소 카드에서) */
    private fun placeNameOf(m: Memory): String? {
        Journal.spots.firstOrNull { it.voice == m.id }?.let { return it.name }
        val la = m.lat ?: return null; val ln = m.lng ?: return null
        val near = Journal.spots.minByOrNull { LocationTracker.distanceM(la, ln, it.lat, it.lng) } ?: return null
        return near.name.takeIf { LocationTracker.distanceM(la, ln, near.lat, near.lng) < 500f }
    }

    /** LLM 이 안 될 때 쓰는 문장 틀 */
    private fun suggestTemplate(place: String?, whenL: String, text: String): String {
        val head = if (place != null) "곧 ${place}${if (hasBatchim(place)) "이에요" else "예요"}." else "곧 예전에 들렀던 곳이에요."
        val said = text.trim().let { if (it.isNotEmpty() && it.last() !in ".!?~") "$it." else it }
        return "$head $whenL, 여기서 이런 이야기를 남기셨어요. $said 들렀다 갈까요?"
    }

    /** LLM 에 넣는 부탁 (그때 남긴 짧은 글만 넣는다 — 이름·연락처 같은 건 안 넣음) */
    private fun suggestPrompt(place: String?, whenL: String, text: String): String =
        "너는 가족 여행 중인 자동차의 음성 안내야. 이 가족은 ${whenL}에 '${place ?: "이 장소"}'에서 이런 말을 남겼어: \"$text\". " +
            "곧 그 장소에 도착해. 그때 한 말을 자연스럽게 짚어 주고 '들렀다 갈까요?'로 끝나는 한국어 두 문장을 말해 줘. " +
            "70자 이내로, 따옴표나 이모지 없이 말할 문장만."

    /** ② 제안 문장 만들기 → (문장, 시연용 소리) */
    private fun makeSuggestLine(target: Memory, place: String?, onLine: (String, String?) -> Unit) {
        val whenL = whenLabel(target)
        val template = suggestTemplate(place, whenL, target.text)
        // 시연: 에뮬레이터엔 LLM 이 없어서, LLM 이 만들 법한 문장을 예시로 대신한다 (영상 자막에 표시)
        if (VoiceRules.DEMO_RECORDINGS && target.text == DemoSpeech.LAST_YEAR_TEXT) {
            onLine(DemoSpeech.SUGGEST_LINE, DemoSpeech.SUGGEST_CLIP); return
        }
        if (!pleos.llmReady) { onLine(template, null); return }
        var done = false
        pleos.generate(suggestPrompt(place, whenL, target.text)) { r ->
            runOnUiThread {
                if (done) return@runOnUiThread
                done = true
                val line = r?.trim()?.trim('"', '“', '”')?.takeIf { it.length in 8..140 }
                onLine(line ?: template, null)
            }
        }
        // 안전장치: 5초 안에 못 만들면 문장 틀로 (운전 중이라 오래 기다리지 않는다)
        window.decorView.postDelayed({ if (!done) { done = true; onLine(template, null) } }, 5_000L)
    }

    /** ④ "응/아니" 알아듣기. 모르겠으면 null */
    private fun stopAnswer(heard: String): Boolean? {
        val t = heard.replace(" ", "")
        if (t.isBlank()) return null
        if (listOf("아니", "싫", "그냥가", "패스", "다음에", "됐어", "괜찮").any { it in t }) return false
        if (listOf("가자", "들르", "들러", "좋아", "그래", "응", "네", "콜", "오케이").any { it in t }) return true
        return pleos.yesNo(heard)
    }

    /** ⑤ 네비에 경유지로 넣기 (Pleos NaviHelper addWaypoint). 요청을 보냈으면 true */
    private fun addStopWaypoint(place: String?, lat: Double, lng: Double): Boolean {
        waypoint.value = Triple(lat, lng, place ?: "경유지")      // 우리 지도에는 어느 쪽이든 핀을 찍는다
        // 촬영용: 네비 화면이 앞으로 나오면 흐름이 끊겨서, 말과 표시만 하고 네비는 안 건드린다
        if (!VoiceRules.ADD_WAYPOINT) {
            naviStatus.value = "경유지 추가 생략 (VoiceRules.ADD_WAYPOINT = false)"
            cue("🧭 경유지 추가 생략 — 촬영용 설정")
            return true
        }
        val n = navi ?: run { naviStatus.value = "네비 연결 안 됨 · 경유지를 못 넣었어요"; return false }
        return try {
            n.addWaypoint(RequestWaypointInfo(lat, lng, place ?: "차곡차곡 추억 장소", WaypointIndex.FIRST, "", "", "0"))
            naviStatus.value = "경유지 추가 요청: ${place ?: "추억 장소"}"
            cue("🧭 경유지 추가 요청: ${place ?: "추억 장소"}")
            safeNavi { n.getWaypointInfo() }
            true
        } catch (e: Exception) { naviStatus.value = "경유지 추가 오류: ${e.message}"; false }
    }

    /** ①~⑤ 한 번에: 제안 → 삐 → 대답 → (응) 경유지 추가. 끝나면 onDone */
    private fun suggestStop(target: Memory, place: String?, lat: Double, lng: Double, onDone: () -> Unit) {
        makeSuggestLine(target, place) { line, clip ->
            revisitStatus.value = "“$line”"
            cue("🔊 제안: 들렀다 갈까요?")
            say(line, clip) {
                prompts.play("v_beep") {
                    runOnUiThread {
                        revisitStatus.value = "듣고 있어요… 들렀다 갈까요?"
                        val demo = target.text == DemoSpeech.LAST_YEAR_TEXT
                        listenFamily(if (demo) DemoSpeech.STOP_YES else null, if (demo) DemoSpeech.STOP_YES_CLIP else null) { heard ->
                            val yes = stopAnswer(heard)
                            if (yes == true) {
                                val sent = addStopWaypoint(place, lat, lng)
                                // 시연에서는 들은 말 대신 결과만 (예시 대답 소리와 글자가 어긋나 보이지 않게)
                                val said = if (VoiceRules.DEMO_RECORDINGS) "들렀다 가요" else "“$heard”"
                                revisitStatus.value = "$said → " + if (sent) "${place ?: "이곳"} 경유지로 추가" else "경유지 추가 실패 (네비 연결 확인)"
                                val reply = if (place != null) "좋아요. ${place}${if (hasBatchim(place)) "을" else "를"} 경유지로 넣었어요." else "좋아요. 경유지로 넣었어요."
                                say(reply, if (reply == DemoSpeech.STOP_ADDED) DemoSpeech.STOP_ADDED_CLIP else null) { onDone() }
                            } else {
                                revisitStatus.value = if (heard.isBlank()) "대답을 못 들었어요 · 경로는 그대로" else "“$heard” → 그냥 지나가요"
                                say(DemoSpeech.STOP_SKIP, DemoSpeech.STOP_SKIP_CLIP) { onDone() }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 차가 말한다 (Pleos TextToSpeech SDK — 문서에 안내된 방식).
     *  - 시연(DEMO_RECORDINGS): clip 이 있으면 그 소리로 대신 (결과가 늘 같게)
     *  - Pleos TTS 준비됨: TTS 로 읽기. 실패하면(에뮬레이터: 엔진 없음·401) 이번 실행 동안은 clip → 글자만
     *  - 아무것도 없으면: 소리 없이 글자 길이만큼 기다렸다가 다음으로
     */
    private fun say(text: String, clip: String? = null, onDone: () -> Unit) {
        val hasClip = clip != null && prompts.has(clip)
        var finished = false
        val once = { if (!finished) { finished = true; onDone() } }
        fun fallback() {
            if (hasClip) prompts.play(clip!!) { once() }
            else window.decorView.postDelayed({ once() }, (1_500L + text.length * 110L).coerceAtMost(9_000L))
        }
        if (VoiceRules.DEMO_RECORDINGS && hasClip) { fallback(); return }
        if (pleos.ttsReady && !ttsBroken) {
            var answered = false
            pleos.speak(text) { ok -> runOnUiThread { answered = true; if (ok) once() else { ttsBroken = true; fallback() } } }
            // 안전장치: TTS 가 아무 대답도 없으면 넘어간다 (말이 멈춰서 흐름이 막히지 않게)
            window.decorView.postDelayed({ if (!answered) { ttsBroken = true; fallback() } }, 4_000L + text.length * 150L)
        } else fallback()
    }

    /**
     * 가족의 대답을 듣는다 (Pleos SpeechToText SDK — 문서에 안내된 방식). 결과는 글자 (못 알아들으면 "").
     *  - 시연(DEMO_RECORDINGS): demoClip(가족이 말하는 소리)을 틀고, demoText 를 알아들은 것으로 친다
     *  - 그 밖: Pleos STT. 엔진·권한 오류면 이번 실행 동안은 "" (못 들음)
     * 앱이 마이크를 직접 열거나 녹음 파일을 만들지 않는다.
     */
    private fun listenFamily(demoText: String?, demoClip: String?, onResult: (String) -> Unit) {
        if (VoiceRules.DEMO_RECORDINGS && demoText != null) {
            flowStatus.value = "듣고 있어요… (시연: Pleos STT 결과를 예시로 대신)"
            if (demoClip != null && prompts.has(demoClip)) prompts.play(demoClip) { runOnUiThread { onResult(demoText) } }
            else window.decorView.postDelayed({ onResult(demoText) }, 2_000L)
            return
        }
        if (pleos.sttReady && !sttBroken) {
            var answered = false
            pleos.listen { heard, ok -> runOnUiThread { if (answered) return@runOnUiThread; answered = true; if (!ok) sttBroken = true; onResult(heard.trim()) } }
            // 안전장치: 20초 안에 결과가 없으면 못 들은 것으로
            window.decorView.postDelayed({ if (!answered) { answered = true; onResult("") } }, 20_000L)
        } else onResult("")
    }

    private fun startLocation() {
        val t = LocationTracker(this) { loc -> runOnUiThread { onLocation(loc) } }
        tracker = t
        locationText.value = try { t.start() } catch (e: Exception) { "위치 시작 실패: ${e.message}" }
    }

    /** 위치가 바뀔 때마다: 예전에 목소리를 남긴 곳 근처면 그때 목소리를 들려준다 */
    private fun onLocation(loc: Location) {
        // 시연 시나리오 중에는 시나리오가 넣어 주는 위치만 받는다.
        // (에뮬레이터 실제 GPS 는 서울역이 아니라서, 그게 들어오면 "집 근처"가 깨지고 출발할 때 질문이 끼어든다)
        if (scenarioStage.value != 0 && loc !== demoLoc) return
        locationText.value = String.format(java.util.Locale.KOREA, "%.5f, %.5f", loc.latitude, loc.longitude)
        // 확인용: 위치가 몇 번 들어왔는지 + 예전에 남긴 목소리까지 몇 m 인지 (1000m 안이면 들려줘야 한다)
        locCount++
        // 서 있는 동안(P)에는 위치가 들어올 때마다 "서 있는 자리"를 갱신한다.
        // P 가 읽힌 순간에 아직 위치를 못 받았거나, 그 뒤에 위치가 바뀐 경우(시연 스크립트가 집으로 옮길 때 등) 대비
        if (parkedState.value) parkedAt = loc.latitude to loc.longitude
        // 앱을 켜자마자 "여행 중이신 것 같아요"가 뜨는 걸 막는다.
        // 에뮬레이터는 켤 때 엉뚱한 곳(기본 위치)에 있을 수 있어서, 그걸 "집에서 멀리 왔다"로 오해한다.
        val f = firstLoc
        if (f == null) firstLoc = loc.latitude to loc.longitude
        else if (!movedSinceLaunch && LocationTracker.distanceM(f.first, f.second, loc.latitude, loc.longitude) > 1000f) movedSinceLaunch = true
        val old = memories.value.filter { it.lat != null && it.lng != null && it.id < tripSince() }
        val near = old.minByOrNull { LocationTracker.distanceM(loc.latitude, loc.longitude, it.lat!!, it.lng!!) }
        parkedText.value = parkedAt?.let { (la, ln) ->
            String.format(java.util.Locale.KOREA, "%.4f, %.4f · 집까지 %dm%s", la, ln,
                (policy.home?.let { h -> LocationTracker.distanceM(la, ln, h.first, h.second) } ?: -1f).toInt(),
                if (policy.isNearHome(la, ln, VoiceRules.NO_ASK_HOME_M)) " (집 근처 → 안 물어봄)" else "")
        } ?: "아직 모름"
        nearMemText.value = when {
            old.isEmpty() -> "예전 목소리가 하나도 없어요 (작년 목소리가 안 심겼는지 확인) · 위치 ${locCount}번"
            else -> "예전 목소리까지 ${LocationTracker.distanceM(loc.latitude, loc.longitude, near!!.lat!!, near.lng!!).toInt()}m " +
                "(${VoiceRules.REPLAY_RADIUS_M.toInt()}m 안이면 재생) · 예전 목소리 ${old.size}개 · 위치 ${locCount}번"
        }
        // 하루 코스: '오늘은 괜찮아요'라고 한 날이 아니면 지나온 길을 임시로 모은다
        if (policy.todayAnswer != false && routes.add(loc.latitude, loc.longitude)) {
            if (policy.tripActive) { livePoints += loc.latitude to loc.longitude; refreshLive() }   // 여행 중엔 메모리만 (지도는 live 로 그린다)
            else dayRoutes.value = routes.load()
        }
        // 여행 중 집에서 1km 넘게 멀어지면 "집을 나갔다" (그래야 다시 집에 오면 마감)
        if (policy.tripActive && !policy.leftHome && policy.home != null && !policy.isNearHome(loc.latitude, loc.longitude, policy.leftHomeM)) policy.leftHome = true
        // 같은 여행에서 남긴 목소리: 그 자리를 충분히 벗어난 적이 있는지 적어 둔다
        val since = tripSince()
        memories.value.forEach { m ->
            if (m.id >= since && m.lat != null && m.lng != null && m.id !in leftSpot &&
                LocationTracker.distanceM(loc.latitude, loc.longitude, m.lat, m.lng) > VoiceRules.SAME_TRIP_LEAVE_M) leftSpot += m.id
        }
        if (flowRunning) return
        // 시연 시나리오는 시간에 맞춰 직접 들려준다 (위치로는 에뮬레이터에서 타이밍을 놓친다)
        if (scenarioStage.value != 0) return
        // 같은 목소리는 한 여행에 한 번만
        val key = tripKey()
        if (key != playedTripKey) loadPlayed(key)
        val nearby = memories.value.filter { m ->
            m.lat != null && m.lng != null &&
                m.id !in playedThisTrip &&
                (m.id < since || (VoiceRules.REPLAY_SAME_TRIP && m.id in leftSpot)) &&   // 기본은 예전 여행 목소리만
                LocationTracker.distanceM(loc.latitude, loc.longitude, m.lat, m.lng) <= revisitRadiusM
        }
        val target = nearby.maxByOrNull { it.id } ?: return
        playRevisit(target, sameTrip = target.id >= since)
    }

    /**
     * 그때 목소리를 들려준다.
     *  - 예전 여행: "예전에 여기 오셨을 때, 이런 말을 남기셨어요." → 목소리
     *  - 같은 여행: "아까 여기서, 이런 이야기를 하셨어요." → 목소리
     * 한 여행에 같은 목소리는 한 번만. 들려주는 동안 음악은 줄인다.
     */
    private fun playRevisit(target: Memory, sameTrip: Boolean) {
        if (flowRunning) return
        if (target.text.isBlank()) return                   // 글자가 없는 예전 기록은 읽어 줄 게 없다
        playedThisTrip += target.id
        savePlayed()
        revisitSameTrip.value = sameTrip
        flowRunning = true
        focus.duck()                                        // 음악 소리를 줄이고 들려준다
        revisitStatus.value = (if (sameTrip) "아까 이야기를 남긴 곳이에요" else "예전에 남긴 곳이에요") + " · 그때 이야기를 읽어 주는 중…"
        revisitMemory.value = target
        val finish = {
            revisitStatus.value = "읽어 드렸어요 · ${java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(target.id))}에 남긴 이야기"
            flowRunning = false
            revisitMemory.value = null
            focus.release()
        }
        // 안전장치: 소리가 중간에 끊겨도 40초 뒤엔 카드를 닫는다
        window.decorView.postDelayed({
            if (revisitMemory.value?.id == target.id) { revisitMemory.value = null; flowRunning = false; focus.release() }
        }, 40_000L)
        // 차(TTS)가 그때 한 말을 읽어 준다: "작년 10월, 여기 오셨을 때 이런 이야기를 남기셨어요. …"
        // 예전 여행에서 남긴 곳이면: 읽어 주는 대신 그때 말을 짚으며 "들렀다 갈까요?" → "응" 이면 경유지 추가
        if (!sameTrip && VoiceRules.SUGGEST_STOP && target.lat != null && target.lng != null) {
            suggestStop(target, placeNameOf(target), target.lat, target.lng) { finish() }
            return
        }
        val sentence = replaySentence(target, sameTrip)
        say(sentence, if (sentence == DemoSpeech.LAST_YEAR_SENTENCE) DemoSpeech.LAST_YEAR_CLIP else null) {
            if (!sameTrip && VoiceRules.CLOSING_PROMPTS) prompts.play("v_revisit_stop") { finish() } else finish()
        }
    }

    /** 이번 여행이 시작된 시각. 여행 중이 아니면 오늘 0시. 이 뒤에 남긴 목소리 = "같은 여행" */
    private fun tripSince(): Long {
        if (policy.tripActive) return policy.tripStartedAt
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun tripKey(): String = "trip-" + tripSince()

    // 이번 여행에 이미 들려준 목소리 — 앱을 껐다 켜도 한 여행에 한 번만 틀도록 저장해 둔다
    private val playedPrefs by lazy { getSharedPreferences("voice_played", MODE_PRIVATE) }

    private fun loadPlayed(key: String) {
        playedTripKey = key
        playedThisTrip.clear()
        if (playedPrefs.getString("key", null) == key) {
            playedPrefs.getStringSet("ids", emptySet())?.mapNotNullTo(playedThisTrip) { it.toLongOrNull() }
        }
    }

    private fun savePlayed() {
        playedPrefs.edit().putString("key", playedTripKey)
            .putStringSet("ids", playedThisTrip.map { it.toString() }.toSet()).apply()
    }

    /** 기어 변화로 정차·출발을 판단한다 */
    private fun onGearChanged(g: VehicleGear?) {
        val prev = lastGear
        lastGear = g
        parkedState.value = g == VehicleGear.GEAR_PARK
        // 목적지 코앞에서 감속해 섰는데 P 를 넣었다 → 남은 길을 채우고 도착으로 (midLeg 판단보다 먼저)
        if (g == VehicleGear.GEAR_PARK && scenarioStage.value in 2..8 && !legArrived && legLeftM <= VoiceRules.ARRIVE_SNAP_M) {
            snapLeg?.let { cue("🅿 목적지 ${legLeftM.toInt()}m 앞에서 P — 도착으로 처리"); it() }
        }
        // 시연 구간을 아직 다 못 갔는데 섰다 가는 중인지 — scenarioOnGear 가 새 구간을 시작하기 '전에' 판단해야 한다
        val midLeg = scenarioStage.value in 2..8 && !legArrived
        scenarioOnGear(g)   // 메인 화면 가상 여행 진행
        // 가는 길에 잠깐 섰다 다시 출발한 것뿐 → 쉬었다 간 곳이 아니니 기록도 질문도 하지 않는다
        if (midLeg) { refreshLive(); return }
        if (g == VehicleGear.GEAR_PARK) {
            // 같은 P 신호가 두 번 오면(처음 값 읽기 + 리스너) 두 번째는 무시 — 정차 시간·운전 시간이 다시 계산되지 않게
            if (prev == VehicleGear.GEAR_PARK) return
            parkedSince = System.currentTimeMillis()
            lastDriveMs = if (lastDepartAt > 0L) parkedSince - lastDepartAt else 0L
            departPending = true
            recordTripStop()
            refreshLive()
            parkedAt = hereNow()?.let { it.latitude to it.longitude }   // 대답은 이 자리에 남긴다
            countPassengers()
            if (policy.isExpired()) { finishRealTrip(); policy.endTrip(); refreshPolicyText() }
            if (endTripIfHome()) return
            if (scenarioStage.value == 0 && naviTrip != null) showNaviTripPicker()   // ① 달리면서 찍은 목적지
            else if (scenarioStage.value == 0) maybeAskTrip()                                         // ② 네비 없이 출발
            if (!askingTrip.value) flowStatus.value =
                if (policy.shouldRecord()) "정차 중… 쉬었다가 D로 출발하면 차가 물어볼게요"
                else "정차 중… 여행 중이 아니라서 기록하지 않아요"
            return
        }
        if (g == VehicleGear.GEAR_DRIVE && prev != VehicleGear.GEAR_DRIVE && lastDepartAt == 0L) lastDepartAt = System.currentTimeMillis()
        // P 에서 쉬다가 D 로 출발할 때. 중간에 R(후진으로 빠져나오기)·N 을 거쳐도 출발로 본다
        if (g == VehicleGear.GEAR_DRIVE && departPending) {
            departPending = false
            askingTrip.value = false
            refreshLive()                    // 다시 출발 → 지금 가는 길은 점선으로
            val stopped = System.currentTimeMillis() - parkedSince
            lastDepartAt = System.currentTimeMillis()
            // 시연 시나리오는 기어만 보고 움직인다 → 몇 초 서 있었는지 따지지 않는다
            val scenario = scenarioStage.value != 0
            // 잠깐 섰다 바로 출발 → 쉬었다 간 곳이 아니니 정차 기록에서 뺀다 (지도 구간 색·여행 카드에 안 나옴)
            if (!scenario && policy.tripActive && parkedSince > 0L && stopped < minStopMs && policy.dropStopsSince(parkedSince)) refreshLive()
            when {
                !policy.shouldRecord() -> flowStatus.value = "여행 중이 아니라서 묻지 않았어요"
                !scenario && (parkedSince == 0L || stopped < minStopMs) -> flowStatus.value = "너무 짧게 서 있어서 이번엔 묻지 않았어요 (${stopped / 1000}초)"
                else -> noAskReason()?.let { flowStatus.value = it } ?: askWhenFree()
            }
        }
    }

    /**
     * 차가 예전 목소리를 들려주는 중에 출발했으면, 다 들려준 뒤에 묻는다 (최대 40초 기다림).
     * 예: 휴게소 1km 앞에서 작년 목소리가 나오는 동안 휴게소에 들어갔다가 바로 출발
     */
    private fun askWhenFree(waitedMs: Long = 0L) {
        if (!flowRunning) { startMemoryFlow(); return }
        if (waitedMs >= 40_000L) { flowStatus.value = "목소리를 들려주는 중이라 이번엔 묻지 않았어요"; return }
        flowStatus.value = "들려주던 목소리가 끝나면 물어볼게요"
        window.decorView.postDelayed({ askWhenFree(waitedMs + 500L) }, 500L)
    }

    /**
     * 출발할 때 묻지 않는 경우. 이유를 돌려준다 (물어도 되면 null)
     *  - 집 근처에서 출발 (여행 시작하자마자 묻지 않기)
     *  - 이번 여행에서 이미 이야기를 남긴 곳 (같은 곳에서 두 번 쉬어도 한 번만)
     */
    private fun noAskReason(): String? {
        // 어디서 출발하는지 모르면 묻지 않는다. (P 를 넣을 때 아직 위치를 못 받았으면 parkedAt 이 비어 있다)
        val (lat, lng) = parkedAt
            ?: hereNow()?.let { it.latitude to it.longitude }
            ?: return "아직 위치를 몰라서 이번엔 묻지 않았어요"
        if (policy.isNearHome(lat, lng, VoiceRules.NO_ASK_HOME_M)) return "집 근처라서 묻지 않았어요"
        val since = tripSince()
        val already = memories.value.any { m ->
            m.id >= since && m.lat != null && m.lng != null &&
                LocationTracker.distanceM(lat, lng, m.lat, m.lng) <= VoiceRules.NO_ASK_SAME_TRIP_M
        }
        return if (already) "이번 여행에서 이미 이야기를 남긴 곳이라 다시 묻지 않았어요" else null
    }

    /** 특별한 여행이면 오늘 첫 정차 때 한 번만 물어본다 */
    /**
     * ② 네비 없이 출발했을 때 대비: 집에서 30km 넘게 와서 처음 P 에 놓으면 "여행 중이신 것 같아요"라고 묻는다.
     * (집 위치를 모르면 예전 방식: 신호 2개 이상)
     */
    private fun maybeAskTrip() {
        if (!policy.canAskToday()) return
        if (!movedSinceLaunch) return      // 앱을 켠 뒤 아직 움직인 적이 없으면 묻지 않는다 (켜자마자 뜨는 것 방지)
        val here = hereNow()
        if (here != null && (policy.isSkipPlace(here.latitude, here.longitude) || policy.isNearHome(here.latitude, here.longitude))) return
        val reasons = policy.reasons(here, memories.value, passengers)
        reasonText.value = if (reasons.isEmpty()) "특별한 여행 신호 없음" else "특별한 여행 신호: " + reasons.joinToString(" · ")
        tripReasons.value = reasons
        val far = reasons.any { it.startsWith("집에서") }
        if (!far && !(policy.home == null && policy.isSpecial(reasons))) return
        askingTrip.value = true
        flowStatus.value = "오늘 여행을 기록할지 물어보는 중…"
        prompts.play("v_ask_trip")
    }

    private fun answerTrip(yes: Boolean) {
        policy.todayAnswer = yes
        askingTrip.value = false
        if (yes) {
            policy.startTrip(odometerKm)
            routes.keepToday()          // 집에서부터 모아 둔 오늘 길도 같이 남긴다
        } else {
            routes.dropToday()
            policy.declined = true      // 집에 돌아올 때까지 다시 안 묻기
            val here = hereNow()
            if (here != null) policy.addSkipPlace(here.latitude, here.longitude)   // 이곳은 다음에도 안 묻기
        }
        dayRoutes.value = routes.load()
        refreshPolicyText()
        if (yes) prompts.play("v_trip_on")
        if (yes && scenarioStage.value == 0) requestTripQr()   // 메뉴바 사진 QR
        flowStatus.value = if (yes) "좋아요. 집에 돌아올 때까지 정차했다 출발할 때마다 물어볼게요" else "알겠어요. 집에 돌아올 때까지 묻지 않을게요"
        // 누구와 함께인지 고르기 (예시 여행은 자체 순서를 따른다)
        if (yes && scenarioStage.value == 0) openRiderPicker(note = "집에서부터 온 길도 함께 남겼어요. 누구와 함께인지 골라 주세요")
    }

    /** "QR 만들기" 버튼: 차곡차곡 서버에 오늘 여행을 만들고, 받은 주소를 QR로 그린다. */
    private fun requestTripQr() {
        tripUploadBusy.value = true
        tripUploadStatus.value = "여행 링크를 만드는 중…"
        tripLink.value = null
        tripQrBitmap.value = null
        val title = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA).format(java.util.Date())
        TripUpload.newTrip(this, title) { link, error ->
            tripUploadBusy.value = false
            if (link != null) {
                tripLink.value = link
                tripQrBitmap.value = try {
                    qrBitmap(link.uploadUrl)
                } catch (e: Exception) {
                    tripUploadStatus.value = "QR을 그리지 못했어요: ${e.message}"
                    null
                }
                if (tripQrBitmap.value != null) tripUploadStatus.value = "QR을 동승자 폰으로 찍게 해 주세요"
            } else {
                tripUploadStatus.value = error ?: "알 수 없는 오류예요"
            }
        }
    }

    private fun setHomeHere() {
        val loc = hereNow() ?: run { answerText.value = "아직 위치를 모르겠어요"; return }
        policy.setHome(loc.latitude, loc.longitude)
        refreshPolicyText()
    }

    /** 여행 기록 중이면 지금까지 온 길과 쉬었다 간 곳을 다시 읽는다 (지도에 구간마다 색을 바꿔 그림) */
    private fun refreshLive() {
        anyTripOn.value = policy.tripActive
        liveTripOn.value = policy.tripActive
        liveTrip.value = if (!liveTripOn.value) null
            else LiveTrip(
                livePoints.toList().ifEmpty { routes.pointsSince(policy.tripStartedAt) },
                policy.tripStops().map { it.lat to it.lng },
                // 달리는 중이면 지금 구간만 점선, 쉬는 곳에 서면 전부 실선.
                // 구간 중간에 잠깐 섰을 때는 아직 가는 길이므로 점선을 유지한다
                driving = lastGear == VehicleGear.GEAR_DRIVE || (scenarioStage.value in 2..8 && !legArrived)
            )
    }

    override fun onResume() {
        super.onResume()
        // 운전 중엔 앱이 안 보이다가, 쉬는 곳에서 앱을 열면 그때까지 온 길을 바로 보여준다
        if (::policy.isInitialized && policy.tripActive) { refreshLive(); if (liveTripOn.value) mapFitKey.value++ }
    }

    private fun refreshPolicyText() {
        refreshLive()
        tripMode.value = policy.tripModeToday
        homeState.value = policy.home
        recordingToday.value = policy.shouldRecord()
        if (policy.shouldRecord()) routes.keepToday()
        val today = when {
            policy.tripActive -> "여행 중 (" + java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(policy.tripStartedAt)) + "부터) → 집에 오면 마감"
            policy.declined -> "이번엔 여행 아님 → 집에 돌아오면 다시 물어봐요"
            policy.tripModeToday -> "오늘: 여행 모드 켜짐 → 정차할 때마다 기록"
            else -> "여행 중 아님 → 네비 목적지가 여행 같거나, 집에서 30km 넘게 와서 처음 P일 때 물어봐요"
        }
        val h = policy.home
        answerText.value = today + when {
            h == null -> " · 집 위치 미설정"
            TripPolicy.FIXED_HOME != null -> " · 집: 서울역 (고정)"
            else -> String.format(java.util.Locale.KOREA, " · 집 %.4f, %.4f", h.first, h.second)
        }
    }

    /** 좌석 센서 값과 출발 전에 고른 탑승자 수 중 큰 쪽 (센서가 뒷좌석 아이를 못 세거나, 에뮬레이터라 0일 때 대비) */
    private fun mergedPassengers(seat: Int?): Int? {
        val picked = ridersStore.todayRiders?.size ?: 0
        return when {
            seat != null -> maxOf(seat, picked)
            picked > 0 -> picked
            else -> null
        }
    }

    /** 좌석 착석 센서로 탑승 인원을 센다 (지원 안 하면 null) */
    private fun countPassengers() {
        val v = vehicle ?: return
        val seats = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
        var answered = 0
        var occupied = 0
        var failed = 0
        seats.forEach { sv ->
            safe {
                v.getSeat().getCurrentSeatOccupancy(SeatArea(sv), { on ->
                    runOnUiThread {
                        answered++; if (on == true) occupied++
                        if (answered + failed == seats.size) passengers = mergedPassengers(if (answered > 0) occupied else null)
                    }
                }, { _ ->
                    runOnUiThread {
                        failed++
                        if (answered + failed == seats.size) passengers = mergedPassengers(if (answered > 0) occupied else null)
                    }
                })
            }
        }
    }

    /**
     * 질문(TTS) → 삐 → 듣기(Pleos STT) → 알아들은 글자를 이 자리에 저장 → "잘 남겨둘게요"
     * 앱이 마이크로 직접 녹음하지 않는다 (주최 측 안내: 음성 기능은 Pleos 문서 방식으로만).
     */
    private fun startMemoryFlow() {
        if (flowRunning) return
        flowRunning = true
        focus.pause()                        // 묻고 듣는 동안 음악을 잠깐 멈춘다
        val spot = parkedAt ?: hereNow()?.let { it.latitude to it.longitude }   // 저장 위치 = P를 넣었던 자리
        val q = pickQuestion()
        recQuestion.value = q.text
        recHeard.value = ""
        flowStatus.value = "차가 질문하는 중… (${q.key}) ${q.text}"
        recPhase.value = 1
        // 시연: 이번 여행 N번째 대답 → DemoSpeech.ANSWERS[N-1] + demo_rec_N (준비한 것보다 많으면 마지막 것)
        val n = memories.value.count { it.id >= tripSince() }
        val k = n.coerceAtMost(DemoSpeech.ANSWERS.size - 1)
        say(q.text, if (prompts.has(q.voice)) q.voice else "v_ask_after_stop") {
            prompts.play("v_beep") {
                flowStatus.value = "듣고 있어요… 말이 끝나면 글자로 남겨요"
                recPhase.value = 2
                listenFamily(DemoSpeech.ANSWERS[k], "demo_rec_${k + 1}") { heard ->
                    if (heard.isBlank()) {
                        // 조용히 버리지 않고 알려 준다. 이 자리엔 기록이 없으니 다음 정차 때 다시 묻는다
                        flowStatus.value = "잘 못 알아들었어요. 다음에 다시 여쭤볼게요"
                        recPhase.value = 4
                        val done = { flowRunning = false; recPhase.value = 0; focus.release() }
                        if (VoiceRules.CLOSING_PROMPTS && prompts.has("v_not_heard")) prompts.play("v_not_heard") { done() }
                        else window.decorView.postDelayed({ done() }, 2_500L)   // 소리 없이 카드만 잠깐
                    } else {
                        val id = System.currentTimeMillis()
                        memories.value = store.add(Memory(id, "", isRainy, 0, spot?.first, spot?.second, q.key, heard))
                        recHeard.value = heard
                        flowStatus.value = "저장했어요: \"$heard\""
                        recPhase.value = 3
                        say("좋아요. 이 장소에 잘 남겨둘게요.", "v_saved") {
                            flowRunning = false; recPhase.value = 0; focus.release(); cue("💾 저장 끝 — 이제 가속")
                        }
                    }
                }
            }
        }
    }

    private data class Question(val key: String, val voice: String, val text: String)

    /**
     * 상황에 맞는 질문 고르기 (위에서부터 우선, 이번 여행에서 이미 한 질문은 건너뜀)
     * 다시 온 곳 > 이번 여행 첫 정차 > 비 > 오래 운전 > 밤 > 여럿이 탐 > 제일 좋았던 것 > 다음에 또 오면 > 기본
     * 위 질문을 다 했으면 기본 질문("방금 들른 곳, 어땠어요?")은 몇 번이고 다시 한다.
     */
    private fun pickQuestion(): Question {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val since = tripSince()
        val thisTrip = memories.value.filter { it.id >= since }
        val asked = thisTrip.mapNotNull { it.question }.toSet()
        val spot = parkedAt ?: hereNow()?.let { it.latitude to it.longitude }
        val revisit = spot != null && memories.value.any { m ->
            m.lat != null && m.lng != null && m.id < since &&       // 예전 여행에서 남긴 곳
                LocationTracker.distanceM(spot.first, spot.second, m.lat, m.lng) <= VoiceRules.REVISIT_QUESTION_M
        }
        val basic = Question("basic", "v_ask_after_stop", "방금 들른 곳, 누구랑 뭐 했는지 한마디 남겨 주세요")
        if (!VoiceRules.SITUATION_QUESTIONS) {
            // 짧은 순서 (시연 대본: 천안휴게소 → 성심당 → 충주호). 다 하고 나면 기본 질문만
            val qRevisit = Question("revisit", "v_q_revisit", "예전에도 여기 왔었죠. 그때랑 오늘, 뭐가 달라졌어요?")
            val qFamily = Question("family", "v_q_family", "다 같이 탔네요. 뒷자리에서도 한마디씩 남겨 주세요")
            val qBest = Question("best", "v_q_best", "여기서 제일 좋았던 거, 하나만 말해 주세요")
            // 시연: 대답(DemoSpeech.ANSWERS)이 정차 순서로 정해지므로 질문도 순서로 고정한다.
            // 거리·인원 조건에 기대면 차를 세운 자리가 조금만 달라도 질문이 바뀌어 대답과 어긋난다.
            if (VoiceRules.DEMO_RECORDINGS) return listOf(qRevisit, qFamily, qBest).getOrNull(thisTrip.size) ?: basic
            val short = listOfNotNull(
                qRevisit.takeIf { revisit },
                qFamily.takeIf { (passengers ?: 0) >= 3 },
                qBest
            )
            return short.firstOrNull { it.key !in asked } ?: basic
        }
        val candidates = listOfNotNull(
            Question("revisit", "v_q_revisit", "예전에도 여기 왔었죠. 그때랑 오늘, 뭐가 달라졌어요?").takeIf { revisit },
            Question("first", "v_q_first", "오늘 여행의 첫 쉼표예요. 여기 오는 길에 무슨 얘기 했어요?").takeIf { thisTrip.isEmpty() },
            Question("rain", "v_q_rain", "비 오는 날 여행이네요. 지금 기분을 한마디로 남겨 주세요").takeIf { isRainy },
            Question("long_drive", "v_q_long_drive", "한참 달려왔어요. 운전한 사람에게 한마디 해 주세요").takeIf { lastDriveMs >= longDriveMs },
            Question("night", "v_q_night", "벌써 밤이에요. 오늘 하루 중 제일 좋았던 순간은요?").takeIf { hour >= 19 || hour < 5 },
            Question("family", "v_q_family", "다 같이 탔네요. 뒷자리에서도 한마디씩 남겨 주세요").takeIf { (passengers ?: 0) >= 3 },
            Question("best", "v_q_best", "여기서 제일 좋았던 거, 하나만 말해 주세요"),
            Question("next", "v_q_next", "다음에 여기 또 오면, 뭐 하고 싶어요?")
        )
        return candidates.firstOrNull { it.key !in asked } ?: basic
    }

    private fun startVehicle() {
        val v = try {
            Vehicle(applicationContext).also { it.initialize() }
        } catch (e: Exception) {
            sdkStatus.value = "Vehicle SDK 연결 실패: ${e.message}"
            return
        }
        vehicle = v
        sdkStatus.value = "Vehicle SDK 연결됨 · 값이 바뀌면 자동으로 갱신돼요"

        // 처음 값 한 번 읽기
        safe { v.getGear().getCurrentGearSelectionState({ g -> gearListener.onSelected(g) }, { e -> gearListener.onFailed(e) }) }
        safe { v.getOdometer().getCurrentDistanceDriven({ d -> odometerListener.onDistanceDrivenUpdated(d) }, { e -> odometerListener.onFailed(e) }) }
        safe { v.getOdometer().getTripCurrentDrivingTime({ t -> tripTimeListener.onDrivingTimeUpdated(t) }, { e -> tripTimeListener.onFailed(e) }) }
        safe { v.getWiper().getCurrentWiperState(frontWindshield, { w -> wiperListener.onWiperStateUpdated(w) }, { e -> wiperListener.onFailed(e) }) }

        // 이후 변경은 리스너로 받기
        safe { v.getGear().registerGearSelection(gearListener) }
        safe { v.getOdometer().registerDistanceDriven(odometerListener) }
        safe { v.getOdometer().registerTripCurrentDrivingTime(tripTimeListener) }
        safe { v.getWiper().registerWiperState(frontWindshield, wiperListener) }
    }

    override fun onPause() {
        if (::routes.isInitialized) routes.flush()
        super.onPause()
    }

    override fun onDestroy() {
        if (::pleos.isInitialized) pleos.release()
        if (::prompts.isInitialized) prompts.stop()
        if (::focus.isInitialized) focus.release()
        tracker?.stop()
        navi?.let { n -> try { n.removeListener(naviListener); n.release() } catch (_: Exception) {} }
        vehicle?.let { v ->
            safe { v.getGear().unregisterGearSelection(gearListener) }
            safe { v.getOdometer().unregisterDistanceDriven(odometerListener) }
            safe { v.getOdometer().unregisterTripCurrentDrivingTime(tripTimeListener) }
            safe { v.getWiper().unregisterWiperState(frontWindshield, wiperListener) }
            safe { v.release() }
        }
        super.onDestroy()
    }

    private fun ui(block: () -> Unit) = runOnUiThread {
        block()
        updatedAt.value = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { sdkStatus.value = "호출 오류: ${e.message}" }
    }

    private fun gearLabel(g: VehicleGear?): String = when (g) {
        null -> "알 수 없음"
        VehicleGear.GEAR_PARK -> "P (주차)"
        VehicleGear.GEAR_REVERSE -> "R (후진)"
        VehicleGear.GEAR_NEUTRAL -> "N (중립)"
        VehicleGear.GEAR_DRIVE -> "D (주행)"
        else -> g.name
    }

    private fun kmLabel(km: Float?) = if (km == null) "알 수 없음" else String.format(java.util.Locale.KOREA, "%,.1f km", km)

    private fun timeLabel(sec: Int?) = if (sec == null) "알 수 없음" else "${sec / 60}분 ${sec % 60}초"

    private fun wiperLabel(w: WiperState?) = when (w) {
        null -> "알 수 없음"
        WiperState.ON -> "작동 중 (비 오는 중일 수 있음)"
        WiperState.OFF -> "꺼짐"
        else -> w.name
    }
}

/** 테스트 화면 구역 제목 (위에 줄 하나) */
@Composable
private fun DebugSection(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(180.dp), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
