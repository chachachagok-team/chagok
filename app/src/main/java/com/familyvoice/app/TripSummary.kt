package com.familyvoice.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/*
 * 여행 이야기 요약.
 * 원래는 그날 녹음 → STT(글자로) → LLM(요약) 인데, Pleos STT·LLM SDK 가 아직 "준비 중"이라
 * 지금은 시연용 예시 문장(assets/demo_summaries.json)을 쓴다.
 * SDK 가 열리면 TripSummarizer 를 하나 더 만들어 MainActivity 의 summarizer 만 바꿔 끼우면 된다.
 */

/** 화면 구석·여행 카드에 붙는 표기 */
const val DEMO_SUMMARY_LABEL = "시연용 예시 요약 · Pleos LLM SDK 연동 예정"

/** 요약 결과: 장소 id → 한 줄, 여행 전체 한 줄 */
data class TripSummaryResult(val perStop: Map<String, String>, val trip: String)

interface TripSummarizer {
    /** 목소리를 남긴 장소들(방문 순서) → 요약. 요약할 수 없으면 null */
    fun summarize(stops: List<Spot>): TripSummaryResult?
}

/** 시연용: 미리 써 둔 문장을 방문 순서대로 넣는다. 파일이 없으면 요약 화면 없이 넘어간다 */
class DemoSummarizer(private val context: Context) : TripSummarizer {
    override fun summarize(stops: List<Spot>): TripSummaryResult? {
        val o = try {
            JSONObject(context.assets.open("demo_summaries.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) { return null }
        val lines = o.optJSONArray("stops") ?: return null
        val per = stops.withIndex().mapNotNull { (i, s) ->
            lines.optString(i, "").takeIf { it.isNotBlank() }?.let { s.id to it }
        }.toMap()
        if (per.isEmpty()) return null
        return TripSummaryResult(per, o.optString("trip", ""))
    }
}

/** 요약 화면 상태. done = 정리가 끝난 장소 수 */
data class SummaryUi(val names: List<String>, val lines: List<String>, val done: Int) {
    val finished: Boolean get() = done >= names.size
}

/** 집 도착 때: "오늘 여행에서 나눈 이야기를 정리하고 있어요…" → 장소마다 체크되며 요약 한 줄 */
@Composable
fun SummaryOverlay(s: SummaryUi) = FvOverlay {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center)) {
            FvCard(700.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (s.finished) "다 정리했어요" else "오늘 여행에서 나눈 이야기를", fontFamily = Pen, fontSize = 30.sp, color = Fv.Blue)
                    Text(
                        if (s.finished) "여행 카드에 차곡차곡 넣어 둘게요" else "정리하고 있어요…",
                        fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Fv.Ink
                    )
                }
                s.names.forEachIndexed { i, name ->
                    val done = i < s.done
                    val now = i == s.done
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(32.dp).background(if (done) Fv.Blue else Fv.Chip, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (done) "✓" else "${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (done) Color.White else Fv.Sub)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (done || now) Fv.Ink else Fv.Sub)
                            if (done) Text(s.lines.getOrElse(i) { "" }, fontFamily = Pen, fontSize = 23.sp, color = Color(0xFF34404B))
                            else if (now) Text("그때 목소리를 듣고 있어요…", fontSize = 15.sp, color = Fv.Sub)
                        }
                    }
                }
            }
        }
        // 화면 오른쪽 아래 구석: 지금 요약은 미리 써 둔 예시라는 표시
        Text(
            DEMO_SUMMARY_LABEL, fontSize = 14.sp, color = Fv.Sub,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
                .background(Fv.Paper.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
