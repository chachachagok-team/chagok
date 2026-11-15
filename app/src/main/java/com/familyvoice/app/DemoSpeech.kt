package com.familyvoice.app

/**
 * 시연용 대체 (VoiceRules.DEMO_RECORDINGS = true 일 때).
 *
 * 에뮬레이터에는 Pleos 음성 엔진이 없고(ON_DEVICE_ENGINE_ERROR) 서버 STT·TTS 는 아직 Coming Soon 이라,
 * 영상·발표에서는 "Pleos STT 가 알아들은 글자"와 "Pleos TTS 가 읽어 준 소리"를 아래 예시로 대신한다.
 * 흐름(묻기 → 듣기 → 글자로 저장 → 그 장소에서 읽어 주기)은 실제와 똑같고, 엔진이 열리면 이 파일은 안 쓰인다.
 *
 * ⚠ demo_rec_1~3 (가족이 차 안에서 대답하는 소리) 의 실제 대사와 ANSWERS 글자가 같아야 한다.
 *    다르면: ANSWERS 를 소리에 맞추거나, 이 글자로 demo_rec_N 을 다시 만들기.
 * ⚠ 작년 이야기 문장(LAST_YEAR_SENTENCE)을 바꾸면 scripts/make_voices.sh 의 v_tts_last_year 도 같이 바꾸기.
 */
object DemoSpeech {

    /** 시연 여행에서 N번째로 대답한 말 (STT 결과 대신). res/raw/demo_rec_N 소리와 짝 */
    val ANSWERS = listOf(
        "작년엔 인형 한 번에 뽑았는데, 오늘은 세 번이나 했는데 안 나왔어! 그래도 호두과자는 작년이랑 똑같이 맛있어",   // 천안삼거리휴게소 출발 ("그때랑 뭐가 달라졌어요?")
        "줄이 진짜 길었는데, 빵이 따뜻해서 기다린 거 하나도 안 아까웠어! 할아버지가 다음엔 아침 일찍 사 오신대",       // 대전 성심당 출발 ("다 같이 탔네요…")
        "호수가 진짜 바다같이 넓었어! 유람선 지나가는 것도 봤는데, 다음에 오면 꼭 타고 싶어"                  // 충주호 출발 ("제일 좋았던 거…")
    )

    /** 작년 경주 여행 때 천안삼거리휴게소(e0)에서 남긴 말 */
    const val LAST_YEAR_TEXT = "호두과자 진짜 맛있다! 내년에 또 와서 사 먹자"

    /** 그 말을 차가 읽어 주는 문장 (= replaySentence 결과). 시연에서는 res/raw/v_tts_last_year 소리로 대신 */
    const val LAST_YEAR_SENTENCE = "작년 10월, 여기 오셨을 때 이런 이야기를 남기셨어요. $LAST_YEAR_TEXT"
    const val LAST_YEAR_CLIP = "v_tts_last_year"

    // ── 천안삼거리휴게소 3km 앞: 차가 먼저 "들렀다 갈까요?" (suggestStop) ──
    /** 차의 제안 (시연: Pleos LLM SDK 가 만들 문장을 예시로 대체). 소리는 res/raw/v_tts_suggest_cheonan */
    const val SUGGEST_LINE = "곧 천안삼거리휴게소예요. 작년에 여기서 호두과자가 진짜 맛있다고, 내년에 또 사 먹자고 하셨죠. 들렀다 갈까요?"
    const val SUGGEST_CLIP = "v_tts_suggest_cheonan"
    /** 가족 대답 (시연: Pleos STT 결과를 예시로 대체). ⚠ res/raw/demo_reaction_cheonan 소리와 같은 말이어야 한다 */
    const val STOP_YES = "응! 그때 그 호두과자 먹고 가자!"
    const val STOP_YES_CLIP = "demo_reaction_cheonan"
    /** "응" → 경유지 추가 뒤 차의 대답 */
    const val STOP_ADDED = "좋아요. 천안삼거리휴게소를 경유지로 넣었어요."
    const val STOP_ADDED_CLIP = "v_stop_added"
    /** "아니" 또는 못 알아들었을 때 */
    const val STOP_SKIP = "알겠어요. 그냥 지나갈게요."
    const val STOP_SKIP_CLIP = "v_stop_skip"

    /** 남기기 창에서 "말로 남기기" 를 눌렀을 때 (시연) */
    const val SPOT_TEXT = "여기 분위기 정말 좋다. 다음에 또 오자"
}
