package com.familyvoice.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 함께 타는 사람 한 명 */
data class Rider(val id: String, val name: String, val emoji: String)

/** 아이콘을 그릴 때 쓰는 역할 (머리 모양·안경 등으로 구분한다) */
enum class RiderLook { DAD, MOM, SON, DAUGHTER, GRANDMA, GRANDPA, FRIEND, DOG }

fun lookOf(id: String) = when (id) {
    "me", "dad" -> RiderLook.DAD
    "mom" -> RiderLook.MOM
    "son" -> RiderLook.SON
    "daughter" -> RiderLook.DAUGHTER
    "grandma" -> RiderLook.GRANDMA
    "grandpa" -> RiderLook.GRANDPA
    "dog" -> RiderLook.DOG
    else -> RiderLook.FRIEND
}

/**
 * 오늘 누가 탔는지 기억해 둔다.
 * 좌석 센서(Vehicle SDK)는 몇 명인지까지만 알려주고 "누구"인지는 모르기 때문에,
 * 출발 전에 한 번 고르게 해서 나중에 "그날 네 명이 함께였어요" 같은 기록에 쓴다.
 */
class RiderStore(context: Context) {

    private val prefs = context.getSharedPreferences("riders", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())

    /** 기본 구성원. 나중에 사용자가 직접 추가·수정할 수 있게 바꾸면 된다. */
    // 이 앱을 쓰는 사람(차 주인)이 아빠라는 전제. 그래서 '나 (아빠)'가 맨 앞이고 배우자는 '엄마'로 둔다
    val roster = listOf(
        Rider("me", "나 (아빠)", "👨"),
        Rider("mom", "엄마", "👩"),
        Rider("son", "아들", "👦"),
        Rider("daughter", "딸", "👧"),
        Rider("grandma", "할머니", "👵"),
        Rider("grandpa", "할아버지", "👴"),
        Rider("friend", "친구", "🧑"),
        Rider("dog", "강아지", "🐶")
    )

    /** 오늘 고른 사람들. 아직 안 골랐으면 null */
    var todayRiders: Set<String>?
        get() = if (prefs.getString("date", null) == today()) prefs.getStringSet("ids", emptySet()) else null
        set(v) {
            if (v == null) prefs.edit().remove("date").apply()
            else prefs.edit().putString("date", today()).putStringSet("ids", v).apply()
        }

    /** 지난번에 함께 간 사람들 (다음 카드에서 미리 골라 둔다) */
    var lastRiders: Set<String>
        get() = prefs.getStringSet("last_ids", null)?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet("last_ids", v.toSet()).apply() }

    fun namesOf(ids: Set<String>): String =
        roster.filter { ids.contains(it.id) }.joinToString(", ") { it.name }

    fun clearToday() { prefs.edit().remove("date").remove("ids").apply() }
}

/* ───────────── 홈 화면과 같은 모양의 카드 (흐린 크림 배경 + 흰 종이 카드 + 파란 버튼) ───────────── */

/** 화면 전체를 덮는 흐린 배경 + 가운데 카드. 홈 화면과 같은 크기 기준(가로 1280)으로 맞춘다 */
@Composable
fun FvOverlay(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fit = Density(constraints.maxWidth / 1280f, 1f)
        CompositionLocalProvider(
            LocalDensity provides fit,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Sans)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xB8F3F0EA)).clickable(enabled = true, onClick = {}),   // 뒤 화면이 눌리지 않게
                contentAlignment = Alignment.Center
            ) { content() }
        }
    }
}

/** 흰 종이 카드 */
@Composable
fun FvCard(width: Dp, horizontal: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.width(width).shadow(28.dp, RoundedCornerShape(6.dp)).background(Fv.Paper, RoundedCornerShape(6.dp))
            .padding(horizontal = 44.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = horizontal,
        content = content
    )
}

/** 큰 둥근 버튼. primary = 파란 버튼, 아니면 테두리 버튼 */
@Composable
fun FvButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier.height(68.dp)
            .then(
                if (primary) Modifier.background(if (enabled) Fv.Blue else Fv.BlueLine, shape)
                else Modifier.border(1.5.dp, Color(0xFFCFD6DD), shape)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 20.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            color = if (primary) Color.White else Fv.Ink
        )
    }
}

/**
 * 출발 전 "오늘 누구와 함께 가세요?" 카드.
 * 메인 화면 위에 덮어서 띄운다.
 */
@Composable
fun RiderPickerCard(
    riders: List<Rider>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    note: String = ""          // 예: "충주호에 가시네요. 여행이면 …"
) = FvOverlay {
    FvCard(760.dp) {
        if (note.isNotEmpty()) Text(note, fontFamily = Pen, fontSize = 28.sp, color = Fv.Blue)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("오늘 누구와 함께 가세요?", fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Fv.Ink)
            Text("골라 두면 오늘 남긴 목소리와 사진이 '누구와 함께였는지'까지 같이 기록돼요.", fontSize = 17.sp, color = Fv.Sub)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            riders.forEach { r ->
                val on = selected.contains(r.id)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(112.dp)
                        .background(if (on) Fv.BlueSoft else Fv.Chip, RoundedCornerShape(14.dp))
                        .border(if (on) 2.dp else 0.dp, if (on) Fv.Blue else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { onToggle(r.id) }
                        .padding(vertical = 14.dp)
                ) {
                    RiderAvatar(lookOf(r.id), on)
                    Text(
                        r.name, fontSize = 17.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) Fv.Blue else Fv.Ink
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FvButton(
                if (selected.isEmpty()) "한 명 이상 골라 주세요" else "${selected.size}명과 출발하기",
                Modifier.weight(1f), enabled = selected.isNotEmpty(), onClick = onStart
            )
            FvButton("나중에", primary = false, onClick = onSkip)
        }
    }
}

/**
 * 사진 모으기 QR 카드. 여행이 끝나 집에 도착했을 때 띄운다.
 * arrival 이 있으면 "잘 다녀오셨어요" 인사를 같이 보여 준다 (도착 카드와 한 장으로 합침).
 */
@Composable
fun TripQrCard(
    riderNames: String,
    qr: Bitmap?,
    status: String,
    onClose: () -> Unit,
    arrival: String? = null
) = FvOverlay {
    FvCard(640.dp, Alignment.CenterHorizontally) {
        if (arrival != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("잘 다녀오셨어요", fontFamily = Pen, fontSize = 30.sp, color = Fv.Blue)
                Text(arrival, fontSize = 17.sp, color = Fv.Sub, textAlign = TextAlign.Center)
            }
        }
        Text("오늘 사진은 여기에 모아요", fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Fv.Ink)
        if (riderNames.isNotEmpty()) Text("함께한 사람: $riderNames", fontSize = 17.sp, color = Fv.Sub)
        if (qr != null) {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = "사진 업로드 QR",
                modifier = Modifier.size(300.dp).shadow(6.dp, RoundedCornerShape(4.dp))
                    .background(Color.White, RoundedCornerShape(4.dp)).padding(10.dp)
            )
            Text("폰 카메라로 찍으면 사진 올리는 화면이 열려요", fontSize = 17.sp, color = Fv.Sub)
        } else {
            Text(status.ifEmpty { "QR을 만드는 중…" }, fontSize = 17.sp, color = Fv.Sub, textAlign = TextAlign.Center)
        }
        FvButton("닫기", Modifier.fillMaxWidth(), onClick = onClose)
    }
}

/* ───────────── 탑승자 아이콘 (이모지 대신 직접 그린다 — 기기마다 모양이 달라지지 않게) ───────────── */

/**
 * 동그란 바탕 위에 머리·어깨를 그리고, 역할마다 머리 모양이나 안경으로 구분한다.
 * 앱 색(파랑·모래색)만 써서 지도·카드와 같은 결을 유지한다.
 */
@Composable
fun RiderAvatar(look: RiderLook, selected: Boolean, size: Dp = 52.dp) {
    val ink = if (selected) Fv.Blue else Fv.Ink
    val bg = if (selected) Color.White else Fv.Paper
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = w / 2f
        // 바탕 원
        drawCircle(bg, radius = c, center = Offset(c, c))
        drawCircle(ink.copy(alpha = 0.18f), radius = c - w * 0.02f, center = Offset(c, c), style = Stroke(w * 0.035f))

        if (look == RiderLook.DOG) {
            val hc = Offset(c, c + w * 0.04f)
            drawCircle(ink, radius = w * 0.19f, center = hc)                       // 얼굴
            drawCircle(ink, radius = w * 0.085f, center = Offset(c - w * 0.20f, c - w * 0.12f))  // 귀
            drawCircle(ink, radius = w * 0.085f, center = Offset(c + w * 0.20f, c - w * 0.12f))
            drawCircle(bg, radius = w * 0.045f, center = Offset(c, c + w * 0.12f))  // 주둥이
            return@Canvas
        }

        val headR = if (look == RiderLook.SON || look == RiderLook.DAUGHTER) w * 0.155f else w * 0.17f
        val headY = c - w * 0.08f
        // 어깨
        val sw = if (look == RiderLook.SON || look == RiderLook.DAUGHTER) w * 0.40f else w * 0.46f
        drawArc(
            color = ink, startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(c - sw / 2f, c + w * 0.14f), size = Size(sw, sw * 0.9f)
        )
        drawCircle(ink, radius = headR, center = Offset(c, headY))

        when (look) {
            RiderLook.MOM, RiderLook.DAUGHTER -> {    // 긴 머리
                drawArc(
                    color = ink, startAngle = 160f, sweepAngle = 220f, useCenter = false,
                    topLeft = Offset(c - headR * 1.45f, headY - headR * 1.35f),
                    size = Size(headR * 2.9f, headR * 2.7f), style = Stroke(w * 0.075f)
                )
                if (look == RiderLook.DAUGHTER) {      // 양갈래
                    drawCircle(ink, radius = w * 0.045f, center = Offset(c - headR * 1.45f, headY + headR * 0.45f))
                    drawCircle(ink, radius = w * 0.045f, center = Offset(c + headR * 1.45f, headY + headR * 0.45f))
                }
            }
            RiderLook.GRANDMA -> {                     // 올림머리
                drawCircle(ink, radius = w * 0.07f, center = Offset(c, headY - headR * 1.15f))
            }
            RiderLook.GRANDPA -> {                     // 안경
                val ey = headY + headR * 0.1f
                drawCircle(color = bg, radius = headR * 0.42f, center = Offset(c - headR * 0.5f, ey), style = Stroke(w * 0.03f))
                drawCircle(color = bg, radius = headR * 0.42f, center = Offset(c + headR * 0.5f, ey), style = Stroke(w * 0.03f))
                drawLine(bg, Offset(c - headR * 0.1f, ey), Offset(c + headR * 0.1f, ey), strokeWidth = w * 0.03f)
            }
            RiderLook.DAD -> {                         // 짧은 머리
                drawArc(
                    color = ink, startAngle = 185f, sweepAngle = 170f, useCenter = false,
                    topLeft = Offset(c - headR * 1.2f, headY - headR * 1.25f),
                    size = Size(headR * 2.4f, headR * 2.2f), style = Stroke(w * 0.08f)
                )
            }
            RiderLook.SON -> {                         // 앞머리
                drawArc(
                    color = ink, startAngle = 195f, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(c - headR * 1.15f, headY - headR * 1.2f),
                    size = Size(headR * 2.3f, headR * 2.1f), style = Stroke(w * 0.085f)
                )
            }
            else -> {}
        }
    }
}
