package com.masar.maintenance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.masar.maintenance.data.*
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.theme.*
import com.masar.maintenance.ui.imageUrl
import com.masar.maintenance.ui.tr
import com.masar.maintenance.ui.I18n

private data class RSide(val key: String, val ar: String, val en: String, val drawable: String)
private val R_SIDES = listOf(
    RSide("front", "الأمام", "Front", "body_front"),
    RSide("right", "اليمين", "Right", "body_right"),
    RSide("left", "اليسار", "Left", "body_left"),
    RSide("top", "الأعلى", "Top", "body_top"),
    RSide("back", "الخلف", "Back", "body_back"),
)

private fun rDrawable(ctx: android.content.Context, name: String): Int =
    ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

/** رحلة مراجعة التقرير السابق — قراءة فقط (مقارنة حالة السيارة عند التسليم). */
@Composable
fun InspectionReviewScreen(nav: NavController, rentalId: Int, kind: String) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rental by remember { mutableStateOf<Rental?>(null) }
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(rentalId, kind) {
        loading = true
        when (val r = Net.repo.rentalGet(rentalId)) {
            is Outcome.Ok -> { rental = r.data; error = null }
            is Outcome.Err -> error = r.message
        }
        loading = false
    }

    val total = 9 // 0=checklist, 1..5 sides, 6 tires, 7 interior, 8 odometer

    MasarScaffold(
        title = tr("مراجعة التقرير", "Review report") + "  (${step + 1}/$total)",
        onBack = { if (step > 0) step-- else nav.popBackStack() }
    ) { pad ->
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!) { }
            else -> {
                val ins = if (kind == "return") rental?.returnInsp else rental?.handover
                val data = ins?.data
                if (ins == null || data == null) {
                    EmptyBox(tr("لا يوجد تقرير سابق مفصّل", "No detailed previous report"), "∅")
                } else {
                    Column(Modifier.fillMaxSize().padding(pad)) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Panel2)) {
                            Box(Modifier.fillMaxWidth((step + 1) / total.toFloat()).fillMaxHeight().background(Yellow))
                        }
                        Surface(color = Yellow.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                tr("عرض فقط — حالة السيارة عند التسليم للعميل", "Read-only — car condition at handover"),
                                color = Yellow, fontSize = 12.sp, modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            when (step) {
                                0 -> ReviewChecklist(data.checklist ?: ins.checklist)
                                in 1..5 -> {
                                    val side = R_SIDES[step - 1]
                                    ReviewSide(ctx, side, data.body?.get(side.key), data.photos?.get(side.key))
                                }
                                6 -> ReviewPhotos(ctx, tr("الكفرات", "Tires"), data.tires?.flatten() ?: emptyList())
                                7 -> ReviewPhotos(ctx, tr("الصور الداخلية", "Interior"), data.photos?.get("interior") ?: emptyList())
                                8 -> ReviewOdometer(ctx, data.km ?: ins.km, data.photos?.get("odometer") ?: emptyList())
                            }
                            Spacer(Modifier.height(70.dp))
                        }
                        Surface(color = Ink2, shadowElevation = 8.dp) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (step > 0) GhostButton(tr("السابق", "Back"), onClick = { step-- }, modifier = Modifier.weight(1f))
                                if (step < total - 1) PrimaryButton(tr("التالي", "Next"), modifier = Modifier.weight(1f)) { step++ }
                                else PrimaryButton(tr("إنهاء المراجعة", "Finish"), modifier = Modifier.weight(1f)) { nav.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReviewChecklist(cl: Map<String, Boolean>?) {
    ReviewCard(tr("قائمة الفحص عند التسليم", "Checklist at handover")) {
        if (cl.isNullOrEmpty()) Text(tr("لا توجد بنود", "No items"), color = Muted, fontSize = 13.sp)
        else cl.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (v) "✓" else "✗", color = if (v) Green else RedStatus, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(k, color = Txt, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (v) tr("سليم", "OK") else tr("ملاحظة", "Note"), color = if (v) Green else RedStatus, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReviewSide(ctx: android.content.Context, side: RSide, points: List<BodyPoint>?, photos: List<String>?) {
    ReviewCard(tr("الهيكل — جهة ${side.ar}", "Body — ${side.en}")) {
        val imgId = rDrawable(ctx, side.drawable)
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 320.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(6.dp)) {
            if (imgId != 0) Image(painterResource(imgId), side.en, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.matchParentSize()) {
                points?.forEach { p ->
                    drawCircle(color = Color.Red, radius = 15f, center = Offset(p.x * size.width, p.y * size.height), style = Stroke(width = 5f))
                    drawCircle(color = Color.Red.copy(alpha = 0.18f), radius = 15f, center = Offset(p.x * size.width, p.y * size.height))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            (if ((points?.size ?: 0) > 0) tr("مواقع ملاحظات: ", "Marked spots: ") + "${points!!.size}" else tr("لا ملاحظات على هذه الجهة", "No marks on this side")),
            color = if ((points?.size ?: 0) > 0) RedStatus else Green, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(tr("صور هذه الجهة عند التسليم", "Photos at handover"), color = Muted, fontSize = 12.sp)
        PhotoStrip(ctx, photos ?: emptyList())
    }
}

@Composable
private fun ReviewPhotos(ctx: android.content.Context, title: String, photos: List<String>) {
    ReviewCard(title + tr(" عند التسليم", " at handover")) {
        if (photos.isEmpty()) Text(tr("لا صور", "No photos"), color = Muted, fontSize = 13.sp)
        else PhotoStrip(ctx, photos)
    }
}

@Composable
private fun ReviewOdometer(ctx: android.content.Context, km: String?, photos: List<String>) {
    ReviewCard(tr("العداد عند التسليم", "Odometer at handover")) {
        Text(tr("القراءة: ", "Reading: ") + (km?.takeIf { it.isNotBlank() } ?: "—") + " " + tr("كم", "km"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (photos.isEmpty()) Text(tr("لا صورة", "No photo"), color = Muted, fontSize = 13.sp)
        else PhotoStrip(ctx, photos)
    }
}

/** شريط صور — الضغط يفتح الصورة بالحجم الكامل للعرض أو التنزيل. */
@Composable
private fun PhotoStrip(ctx: android.content.Context, photos: List<String>) {
    Column {
        photos.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    RemoteImage(
                        p,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .clickable {
                                imageUrl(p)?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
                            },
                        contentScale = ContentScale.Crop
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (photos.isNotEmpty())
            Text(tr("اضغط أي صورة لفتحها أو حفظها", "Tap any photo to open or save"), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
