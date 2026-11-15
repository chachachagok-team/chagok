package com.familyvoice.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 사진 업로드 링크 하나 (여행 하나당 하나씩 만들어짐). */
data class TripLink(val tripId: String, val uploadUrl: String, val expiresAt: Long)

/**
 * 차곡차곡 사진 업로드 서버(Google Apps Script)에 여행을 만들어 업로드 링크를 받아온다.
 *
 * - 주소(WEB_APP_URL)는 비밀이 아니라서 코드에 그대로 둔다. 재배포로 주소가 바뀌면 여기만 고치면 된다.
 * - 관리자 키는 비밀이라서 assets/appsscript_key.txt 에서 읽는다.
 *   이 파일은 .gitignore 에 있어서 깃허브에 올라가지 않는다. 각자 자기 PC에서 직접 채워 넣는다.
 *   (파일이 없거나 비어 있으면 빌드는 되고, 버튼을 눌렀을 때만 안내 메시지가 뜬다)
 *
 * 지금은 "QR 만들기" 버튼을 눌렀을 때만 호출한다 (수동). 여행 시작·도착 시점에
 * 자동으로 부르게 하는 건, 그 시점을 어떤 기준으로 잡을지 정한 뒤에 연결하면 된다.
 */
object TripUpload {

    /** 여행을 만들어 달라고 부르는 서버(Apps Script). 브라우저로 여는 주소가 아니라 앱이 호출만 한다. */
    private const val WEB_APP_URL =
        "https://script.google.com/macros/s/AKfycbwg29jyBF76JByc9qnclhoOLOiGGq3-dlis_6F1gDOfu5BJhfgPxW1NhWxQ1e0_ZAh8/exec"

    /**
     * 동승자가 QR을 찍으면 열리는 업로드 페이지 주소 (GitHub Pages).
     * 구글 밖에 두는 이유: script.google.com 페이지는 구글 계정이 여러 개 로그인된
     * 안드로이드폰에서 "현재 파일을 열 수 없습니다" 로 막히는 일이 있다.
     * 저장소를 만들고 Pages 를 켠 뒤, 그 주소를 여기에 넣으면 된다.
     * 예: "https://내계정.github.io/저장소이름/"
     * 비워 두면 예전처럼 Apps Script 페이지 주소를 쓴다(삼성폰에서 안 열릴 수 있음).
     */
    private const val UPLOAD_PAGE_URL = "https://icheonold1-alt.github.io/chagok-upload/"

    private const val KEY_ASSET = "appsscript_key.txt"

    /** assets 안의 관리자 키를 읽는다. 없거나 비어 있으면 빈 문자열. */
    private fun adminKey(context: Context): String = try {
        context.assets.open(KEY_ASSET).bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        ""
    }

    fun newTrip(context: Context, title: String, onResult: (TripLink?, String?) -> Unit) {
        val key = adminKey(context)
        if (key.isBlank()) {
            onResult(null, "관리자 키가 비어 있어요. app/src/main/assets/$KEY_ASSET 에 키를 넣어 주세요")
            return
        }
        Thread {
            try {
                val url = WEB_APP_URL + "?action=newTrip&adminKey=" + URLEncoder.encode(key, "UTF-8") +
                    "&title=" + URLEncoder.encode(title, "UTF-8")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                val json = JSONObject(body)
                if (json.optBoolean("ok", false)) {
                    val tripId = json.getString("tripId")
                    // 서버가 준 uploadUrl 은 쓰지 않는다.
                    // ScriptApp.getService().getUrl() 이 /dev 주소를 돌려줄 때가 있고,
                    // /dev 주소는 스크립트 편집 권한이 있는 계정만 열 수 있어서 동승자 폰에서 안 열린다.
                    // 우리가 방금 호출한 WEB_APP_URL 로 직접 조립하는 쪽이 확실하다.
                    val token = json.optString("token", "")
                    val base = if (UPLOAD_PAGE_URL.isNotBlank()) UPLOAD_PAGE_URL else WEB_APP_URL
                    val link = if (token.isNotBlank()) {
                        base + "?trip=" + URLEncoder.encode(tripId, "UTF-8") +
                            "&k=" + URLEncoder.encode(token, "UTF-8")
                    } else {
                        json.getString("uploadUrl")   // 예전 버전 서버와도 돌아가게
                    }
                    post(onResult, TripLink(
                        tripId = tripId,
                        uploadUrl = link,
                        expiresAt = json.optLong("expiresAt", 0L)
                    ), null)
                } else {
                    post(onResult, null, json.optString("error", "서버가 거절했어요 (코드 $code)"))
                }
            } catch (e: Exception) {
                post(onResult, null, "연결 실패: ${e.message}")
            }
        }.start()
    }

    private fun post(onResult: (TripLink?, String?) -> Unit, link: TripLink?, error: String?) {
        Handler(Looper.getMainLooper()).post { onResult(link, error) }
    }
}
