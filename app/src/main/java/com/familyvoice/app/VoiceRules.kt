package com.familyvoice.app

/**
 * 묻기·남기기·들려주기 규칙. 숫자는 전부 여기서만 바꾼다.
 * DEMO = true 이면 시연용 짧은 시간, false 이면 실제 서비스 시간.
 */
object VoiceRules {
    const val DEMO = true

    /** 이만큼 서 있다가 출발해야 질문한다 (시연 5초 · 실제 10분) */
    val MIN_STOP_MS: Long = if (DEMO) 5_000L else 10 * 60_000L

    /** "한참 달려왔어요" 질문을 할 운전 시간 (시연 2분 · 실제 1시간 30분) */
    val LONG_DRIVE_MS: Long = if (DEMO) 2 * 60_000L else 90 * 60_000L

    /** 집 이 거리 안에서 출발할 때는 묻지 않는다 */
    const val NO_ASK_HOME_M = 300f

    /** 이번 여행에서 이미 대답을 남긴 곳 이 거리 안에서는 다시 묻지 않는다 */
    const val NO_ASK_SAME_TRIP_M = 300f

    /**
     * 예전 목소리를 남긴 곳 이 거리 안에 들어오면 그때 목소리를 들려준다 (시연 1km · 실제 300m).
     * 시연은 고속도로를 빨리 달리니까, 휴게소에 닿기 조금 전에 미리 울려야 "들렀다 갈까?" 할 틈이 생긴다.
     */
    val REPLAY_RADIUS_M: Float = if (DEMO) 1000f else 300f

    /**
     * 정차한 곳이 예전 목소리를 남긴 곳에서 이 거리 안이면 "예전에도 여기 왔었죠" 질문을 한다.
     * 휴게소는 넓고, 예시 기록은 실제 길 위로 옮겨 그려지면서 수백 m 움직이기도 해서 1km 로 둔다.
     */
    const val REVISIT_QUESTION_M = 1000f

    /** 같은 여행에서 남긴 목소리는 그곳에서 이만큼 멀어졌다가 돌아올 때만 들려준다 */
    const val SAME_TRIP_LEAVE_M = 1000f

    /**
     * 주행 중 네비에 "지금 어디예요?" 하고 물어보는 간격.
     * 앱이 뒤에 있으면 안드로이드가 GPS를 몇 분에 한 번으로 줄여서, 지나가며 듣는 목소리를 놓친다.
     * 네비(차 서비스)는 뒤에 있어도 답해 주므로 여행 중에는 이쪽으로도 위치를 받는다.
     *
     * 1초인 이유: 시연 스크립트(drive_trip.sh)는 300m를 0.15초에 달린다(약 2km/s).
     * 3초마다 물어보면 한 번에 6km씩 건너뛰어서 휴게소 1km 구간을 통째로 지나쳐 버린다.
     * (스크립트가 예전에 멈춘 곳 1.5km 안에서는 0.25km/s로 늦춰 주긴 한다)
     * 실제 차 GPS도 보통 1초 간격이라 이 정도가 맞다.
     */
    const val NAVI_POLL_MS = 1_000L

    /**
     * 소리 키우기 (밀리벨: 100 = +1dB, 350 ≈ 1.5배).
     * 안내 음성은 원래 그대로였던 걸 1.5배로, 녹음 목소리는 원래 +8dB 에서 1.5배 더.
     * 소리가 깨지면 낮추고, 더 작으면 올리기.
     */
    const val PROMPT_GAIN_MB = 350
    const val VOICE_GAIN_MB = 1150

    /**
     * 이미 큰 소리로 들어온 목소리(시연용 파일 등)는 조금만 키운다.
     * 에뮬레이터 마이크 녹음은 아주 작게 들어와서 많이 키워야 하지만,
     * 만들어 둔 음성 파일은 이미 꽉 차 있어서 그대로 키우면 소리가 깨진다.
     */
    const val LOUD_VOICE_GAIN_MB = 300

    /* ───── 켜고 끄는 스위치 (11/7 단순화: 시연·취지에 맞게 핵심만 남김) ───── */

    /**
     * 시연용 대체 (11/11~ STT·TTS 방식). true 면 Pleos 음성 SDK 대신 DemoSpeech 예시를 쓴다:
     *  - 듣기(STT): res/raw/demo_rec_N (가족이 대답하는 소리)을 틀고, DemoSpeech.ANSWERS[N-1] 을 알아들은 글자로 저장
     *  - 읽어 주기(TTS): clip 이 있으면 그 소리(예: v_tts_last_year), 없으면 Pleos TTS → 안 되면 글자만
     * 에뮬레이터엔 Pleos 음성 엔진이 없어서(ON_DEVICE_ENGINE_ERROR) 영상·발표용으로 켜 둔다.
     * false 면 Pleos STT·TTS 만 쓴다 (실차·엔진이 열린 환경). 앱은 마이크로 직접 녹음하지 않는다.
     */
    const val DEMO_RECORDINGS = true

    /**
     * 앱을 켤 때마다 지난 테스트 기록을 싹 지우고 처음 상태로 시작할지.
     * 영상은 늘 같은 순서로(demo_rec_1 → 2 → 3) 나와야 해서 켜 둔다.
     * 지우는 것: 여행 중 상태 · 지나온 길 · 녹음한 목소리 · 테스트로 만든 여행 카드와 장소
     * 남기는 것: 예시 여행 8개(부산·대전…)와 작년 천안삼거리휴게소 목소리
     * ⚠ 켜져 있으면 여행 도중에 앱을 다시 켜도 지워진다. **실제 서비스로 돌릴 땐 false 로.**
     */
    /**
     * 시연 모드: 메인 화면 "여행 시작"을 누르면 기어로 움직이는 시연 시나리오로 들어간다
     * (집 → 천안삼거리휴게소 → 대전 성심당 → 충주호 → 집. 구간 시간은 TripScenario).
     * false 면 실제 위치를 보고 움직이는 원래 동작.
     */
    const val DEMO_SCENARIO = true

    /*
     * 주행 표시는 실제 차 속도를 따른다.
     * Vehicle SDK 에 "현재 속도" API 가 없어서, 주행거리계가 늘어난 양으로 계산한다
     * (accelerator 를 밟으면 주행거리가 늘어난다).
     */
    const val CRUISE_KMH = 100              // 최고 속도. 이때 경로가 제일 빨리 그려진다 (구간 시간의 기준)
    const val DRAW_FROM_KMH = 30            // 이 속도를 넘어야 경로가 그려지기 시작
    const val ARRIVE_SNAP_M = 1500f         // 목적지가 이 거리 안일 때 P 를 넣으면 바로 도착 (감속하다 멈춘 몇백 m 를 채운다)
    const val ACCEL_MS = 2_000L             // 0 → 최고 속도까지 걸리는 시간
    const val REAL_SPEED_HOLD_MS = 3_000L   // 이 시간 안에 받은 속도까지만 믿는다

    /**
     * 시연용: Pleos 시뮬레이터(PC 127.0.0.1:8088)에서 현재 속도를 읽어 쓴다.
     * 실제 차에는 그런 서버가 없다 → 읽히지 않으면 알아서 꺼지고, 촬영이 끝나면 false 로.
     */
    const val SIM_SPEED = true
    const val SIM_SPEED_PORT = 8088
    const val SIM_SPEED_POLL_MS = 200L
    const val SIM_SPEED_HOLD_MS = 2_000L

    /*
     * 지금은 앱이 속도를 만들어 쓴다.
     *   - Vehicle SDK 에 "현재 속도" API 가 없다 (Speed 로 찾히는 건 계기판 단위 상수,
     *     크루즈컨트롤 목표 속도, 에어컨 바람 세기뿐).
     *   - 에뮬레이터는 속도계만 움직이고 주행거리는 늘지 않아서, 거리로 속도를 구할 수도 없다.
     * 나중에 주행거리가 늘어나기 시작하면 그 값을 자동으로 쓴다 (speedNow 참고).
     */

    /** 메인 화면 아래에 상태 한 줄을 띄운다 (왜 안 물었는지 바로 보려고). 촬영 때는 false */
    const val SHOW_STATUS = false

    const val DEMO_FRESH_START = true

    /**
     * 상황별 질문 (비·밤·오래 운전·첫 정차·다음에 또 오면 등 전부).
     * false 면 짧은 순서만 (11/7 시연 대본에 맞춤, 이번 여행에서 한 질문은 건너뜀):
     *   예전 여행에서 왔던 곳 "그때랑 뭐가 달라졌어요?" → 3명 이상 "다 같이 탔네요" → "제일 좋았던 거" → 기본 질문
     */
    const val SITUATION_QUESTIONS = false

    /** 같은 여행에서 남긴 목소리도 1km 갔다가 돌아오면 다시 들려줄지. false 면 예전 여행 목소리만 */
    const val REPLAY_SAME_TRIP = false

    /** 끝맺음 멘트를 소리로 낼지 ("들렀다 갈까요?", "잘 안 들렸어요"). false 면 화면 글자만 */
    const val CLOSING_PROMPTS = false

    /**
     * 예전 여행에서 이야기를 남긴 곳에 다가가면, 그때 말을 짚으며 "들렀다 갈까요?" 하고 묻는다.
     * "응" → 네비(Pleos NaviHelper)에 경유지로 넣는다. false 면 예전처럼 그때 이야기만 읽어 준다
     */
    const val SUGGEST_STOP = true

    /**
     * 휴게소를 실제로 네비 경유지에 넣을지 (NaviHelper.addWaypoint).
     * false 면 말과 화면 표시는 그대로 하고 네비만 안 건드린다 —
     * 영상 촬영 중에 네비 화면이 앞으로 튀어나와 흐름이 끊기는 걸 막으려고.
     * ⚠ 끄고 찍은 영상에는 "경유지를 실제로 넣는 장면"이 없다.
     *   기능이 진짜 된다는 걸 보이려면 이걸 켜고 그 장면만 따로 한 컷 찍어 붙일 것.
     */
    const val ADD_WAYPOINT = false
}
