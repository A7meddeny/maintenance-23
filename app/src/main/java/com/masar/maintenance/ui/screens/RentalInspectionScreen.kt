package com.masar.maintenance.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.masar.maintenance.data.*
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.theme.*
import com.masar.maintenance.ui.tr
import com.masar.maintenance.ui.I18n
import kotlinx.coroutines.launch

private val RENTAL_ITEMS = listOf(
    "استمارة السيارة" to "Registration Book", "المساحات" to "Wipers", "المراية الداخلية" to "Rear-View Mirror",
    "المراية الخارجية" to "Side Mirror", "الاستبني + العدة" to "Spare Tire & Tools", "رقم اللوحات" to "Number Plates",
    "اقفال الأبواب" to "Door Locks", "النور" to "Head Light", "الاشارات" to "Indicators", "بنزين" to "Gas",
    "جنط" to "Hub", "كفرات" to "Tires", "كفر أمامي يمين" to "Right Front Tire", "كفر خلفي يمين" to "Right Back Tire",
    "كفر أمامي يسار" to "Left Front Tire", "كفر خلفي يسار" to "Left Back Tire", "مساند الرأس" to "Head Set",
    "نظافة داخلية" to "Clean Interior", "نظافة خارجية" to "Clean Exterior", "طفايات السجائر" to "Clean Ashtrays",
    "ولاعات السجائر" to "Cigarettes Lighter", "الراديو" to "Radio", "الأحزمة" to "Seat Belts",
    "المكيف" to "Air Conditioning", "الطاسات" to "Hub Caps", "زيت الماكينة" to "Engine Oil",
    "فرامل" to "Brakes", "عداد السرعة" to "Speedometer", "الفراش الداخلي" to "Interior Furniture",
)

private data class Side(val key: String, val ar: String, val en: String, val drawable: String, val photos: Int)
private val SIDES = listOf(
    Side("front", "الأمام", "Front", "body_front", 5),
    Side("right", "اليمين", "Right", "body_right", 10),
    Side("left", "اليسار", "Left", "body_left", 10),
    Side("top", "الأعلى", "Top", "body_top", 5),
    Side("back", "الخلف", "Back", "body_back", 5),
)

private fun drawableId(ctx: android.content.Context, name: String): Int =
    ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

@Composable
fun RentalInspectionScreen(nav: NavController, rentalId: Int, kind: String) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val isReturn = kind == "return"

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rental by remember { mutableStateOf<Rental?>(null) }

    val checked = remember { mutableStateMapOf<String, Boolean>() }
    val sideCircles = remember { SIDES.associate { it.key to mutableStateListOf<Offset>() } }
    val sidePhotos = remember { SIDES.associate { it.key to mutableStateListOf<UploadFile>() } }
    val tirePhotos = remember { List(4) { mutableStateListOf<UploadFile>() } }
    val interiorPhotos = remember { mutableStateListOf<UploadFile>() }
    var odometerPhoto by remember { mutableStateOf<UploadFile?>(null) }
    var km by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var hasIssues by remember { mutableStateOf(false) }

    var sigFollowBmp by remember { mutableStateOf<Bitmap?>(null) }
    var sigRenterBmp by remember { mutableStateOf<Bitmap?>(null) }

    var step by remember { mutableStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var submitErr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rentalId) {
        loading = true
        when (val r = Net.repo.rentalGet(rentalId)) {
            is Outcome.Ok -> { rental = r.data; error = null }
            is Outcome.Err -> error = r.message
        }
        loading = false
    }

    val total = 10

    MasarScaffold(
        title = (if (isReturn) tr("تقرير إرجاع", "Return report") else tr("تقرير تسليم", "Handover report")) + "  (${step + 1}/$total)",
        onBack = { if (step > 0) step-- else nav.popBackStack() }
    ) { pad ->
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!) { }
            rental == null -> EmptyBox(tr("العقد غير موجود", "Rental not found"), "∅")
            else -> {
                val r = rental!!
                Column(Modifier.fillMaxSize().padding(pad)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Panel2)) {
                        Box(Modifier.fillMaxWidth((step + 1) / total.toFloat()).fillMaxHeight().background(Red))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        submitErr?.let {
                            Surface(color = RedStatus.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(it, color = RedStatus, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (step == 0) {
                            MasarCard {
                                Text("${r.carName} — ${r.plateFull ?: ""}", color = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(tr("المستأجر: ", "Renter: ") + r.renterName + (r.renterPhone?.let { " · $it" } ?: ""), color = Muted, fontSize = 12.sp)
                            }
                            if (isReturn && r.handover != null) {
                                Spacer(Modifier.height(12.dp))
                                PreviousReportCard(r.handover!!, rentalId, ctx, nav)
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        when (step) {
                            0 -> ChecklistStep(checked, ctx)
                            in 1..5 -> {
                                val side = SIDES[step - 1]
                                BodySideStep(side, sideCircles[side.key]!!, sidePhotos[side.key]!!, ctx)
                            }
                            6 -> TiresStep(tirePhotos, ctx)
                            7 -> InteriorStep(interiorPhotos, ctx)
                            8 -> OdometerStep(km, { km = it }, odometerPhoto, { odometerPhoto = it }, ctx)
                            9 -> SignaturesStep(
                                { sigFollowBmp = it }, { sigRenterBmp = it },
                                note, { note = it }, isReturn, hasIssues, { hasIssues = it }
                            )
                        }
                        Spacer(Modifier.height(80.dp))
                    }

                    Surface(color = Ink2, shadowElevation = 8.dp) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (step > 0) GhostButton(tr("السابق", "Back"), onClick = { step-- }, modifier = Modifier.weight(1f))
                            if (step < total - 1) {
                                PrimaryButton(tr("التالي", "Next"), modifier = Modifier.weight(1f)) { step++ }
                            } else {
                                PrimaryButton(
                                    if (isReturn) tr("اعتماد وإقفال", "Approve & close") else tr("اعتماد التقرير", "Approve report"),
                                    loading = submitting, modifier = Modifier.weight(1f)
                                ) {
                                    submitErr = null; submitting = true
                                    scope.launch {
                                        submitInspection(
                                            ctx, rentalId, kind, isReturn, checked, sideCircles, sidePhotos,
                                            tirePhotos, interiorPhotos, odometerPhoto, km, note, hasIssues,
                                            sigFollowBmp, sigRenterBmp,
                                            onDone = { submitting = false; nav.popBackStack() },
                                            onErr = { submitting = false; submitErr = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistStep(checked: MutableMap<String, Boolean>, ctx: android.content.Context) {
    SectionCard(tr("قائمة الفحص — اضغط على البنود السليمة", "Checklist — tap items that are OK")) {
        // تحديد الكل ثم إلغاء غير السليم (لتسريع العمل)
        val allOn = RENTAL_ITEMS.all { checked[it.first] == true }
        Surface(
            color = if (allOn) Green.copy(alpha = 0.20f) else Blue.copy(alpha = 0.12f), shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable {
                val target = !allOn; RENTAL_ITEMS.forEach { checked[it.first] = target }
            }
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (allOn) "☑" else "▣", color = if (allOn) Green else Blue, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Text(if (allOn) tr("إلغاء تحديد الكل", "Clear all") else tr("تحديد كل البنود (ثم ألغِ غير السليم)", "Select all (then clear faulty)"),
                    color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        RENTAL_ITEMS.forEachIndexed { i, pair ->
            val ar = pair.first; val en = pair.second
            val on = checked[ar] == true
            val icId = drawableId(ctx, "ic_chk_${i + 1}")
            // خلفية بيضاء لوضوح الأيقونات (الحمراء على داكن غير واضحة)
            Surface(
                color = if (on) Green.copy(alpha = 0.22f) else Color.White,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Green else Line),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { checked[ar] = !on }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icId != 0) Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                        Image(painterResource(icId), null, modifier = Modifier.size(48.dp).padding(2.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(if (I18n.isAr) ar else en, color = Color(0xFF1A1A1A), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (on) "☑" else "☐", color = if (on) Green else Color(0xFF999999), fontSize = 24.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("المحدد: ", "Selected: ") + "${checked.count { it.value }}/${RENTAL_ITEMS.size}", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun BodySideStep(side: Side, circles: MutableList<Offset>, photos: MutableList<UploadFile>, ctx: android.content.Context) {
    SectionCard(tr("جهة ${side.ar}", "${side.en} side") + " — " + tr("حدّد مواقع الأضرار", "Mark damage spots")) {
        val imgId = drawableId(ctx, side.drawable)
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(6.dp)) {
            if (imgId != 0) Image(painterResource(imgId), side.en, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.matchParentSize().pointerInput(side.key) {
                detectTapGestures { o -> circles.add(Offset(o.x / size.width, o.y / size.height)) }
            }) {
                circles.forEach { p ->
                    drawCircle(color = Red, radius = 16f, center = Offset(p.x * size.width, p.y * size.height), style = Stroke(width = 6f))
                    drawCircle(color = Red.copy(alpha = 0.18f), radius = 16f, center = Offset(p.x * size.width, p.y * size.height))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("المواقع: ", "Spots: ") + "${circles.size}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (circles.isNotEmpty()) GhostButton(tr("تراجع", "Undo"), onClick = { circles.removeAt(circles.size - 1) })
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("صور هذه الجهة (المطلوب ${side.photos})", "Photos for this side (need ${side.photos})"), color = Muted, fontSize = 13.sp)
        MultiPhoto(photos, side.photos)
    }
}

@Composable
private fun TiresStep(tirePhotos: List<MutableList<UploadFile>>, ctx: android.content.Context) {
    SectionCard(tr("الكفرات — صوّر الإطارات الأربعة", "Tires — photograph the four tires")) {
        GuideImage(ctx, "guide_tire", tr("نموذج تصوير الإطار", "Tire photo example"))
        val labels = listOf(
            tr("أمامي يمين", "Front right"), tr("أمامي يسار", "Front left"),
            tr("خلفي يمين", "Rear right"), tr("خلفي يسار", "Rear left")
        )
        labels.forEachIndexed { i, lbl ->
            Spacer(Modifier.height(12.dp))
            Text("${i + 1}. $lbl " + tr("(1-3 صور)", "(1-3 photos)"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            MultiPhoto(tirePhotos[i], 3)
        }
    }
}

@Composable
private fun InteriorStep(photos: MutableList<UploadFile>, ctx: android.content.Context) {
    SectionCard(tr("الصور الداخلية", "Interior photos")) {
        GuideImage(ctx, "guide_interior", tr("نموذج التصوير الداخلي", "Interior photo example"))
        Spacer(Modifier.height(10.dp))
        Text(tr("حتى 20 صورة للداخلية", "Up to 20 interior photos"), color = Muted, fontSize = 13.sp)
        MultiPhoto(photos, 20)
    }
}

@Composable
private fun OdometerStep(km: String, onKm: (String) -> Unit, photo: UploadFile?, onPhoto: (UploadFile?) -> Unit, ctx: android.content.Context) {
    SectionCard(tr("العداد", "Odometer")) {
        GuideImage(ctx, "guide_odometer", tr("نموذج تصوير العداد", "Odometer photo example"))
        Spacer(Modifier.height(12.dp))
        MasarField(km, onKm, tr("قراءة العداد (كم)", "Odometer reading (km)"), keyboard = KeyboardType.Number)
        Spacer(Modifier.height(12.dp))
        PhotoPickerField(tr("صورة العداد", "Odometer photo"), photo, onPhoto)
    }
}

@Composable
private fun SignaturesStep(
    onFollowBmp: (Bitmap?) -> Unit, onRenterBmp: (Bitmap?) -> Unit,
    note: String, onNote: (String) -> Unit,
    isReturn: Boolean, hasIssues: Boolean, onHasIssues: (Boolean) -> Unit
) {
    SectionCard(tr("التواقيع والملاحظات", "Signatures & notes")) {
        MasarField(note, onNote, tr("ملاحظات (اختياري)", "Notes (optional)"), singleLine = false)
        if (isReturn) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = if (hasIssues) RedStatus.copy(alpha = 0.14f) else Panel2, shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth().clickable { onHasIssues(!hasIssues) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasIssues) "☑" else "☐", color = if (hasIssues) RedStatus else Muted, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("توجد مشاكل/أضرار عند الإرجاع", "There are problems/damages on return"), color = Txt, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(tr("توقيع موظف المتابعة", "Inspector signature"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SignaturePad(onFollowBmp)
        Spacer(Modifier.height(16.dp))
        Text(tr("توقيع المستأجر", "Renter signature"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SignaturePad(onRenterBmp)
    }
}

/** لوحة توقيع — الحالة محلية تماماً (لا تُعيد بناء الشاشة)، وتُصدّر صورة عند رفع الإصبع. */
@Composable
private fun SignaturePad(onBitmap: (Bitmap?) -> Unit) {
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var size by remember { mutableStateOf(IntSize(0, 0)) }
    Column {
        Box(
            Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .border(1.dp, Line, RoundedCornerShape(10.dp))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> strokes.add(mutableListOf(o)) },
                        onDrag = { change, _ ->
                            change.consume()
                            if (strokes.isNotEmpty()) strokes[strokes.size - 1].add(change.position)
                        },
                        onDragEnd = { onBitmap(strokesToBitmap(strokes, size)) }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                strokes.forEach { st ->
                    for (i in 1 until st.size) drawLine(Color.Black, st[i - 1], st[i], strokeWidth = 4f)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        GhostButton(tr("مسح التوقيع", "Clear signature"), onClick = { strokes.clear(); onBitmap(null) })
    }
}

@Composable
private fun GuideImage(ctx: android.content.Context, name: String, label: String) {
    val id = drawableId(ctx, name)
    if (id == 0) return
    Column {
        Surface(color = Blue.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("ℹ " + label, color = Blue, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Image(
            painterResource(id), label,
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(6.dp)
        )
    }
}

@Composable
private fun MultiPhoto(photos: MutableList<UploadFile>, max: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val multiPick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = if (max > 1) max else 2)
    ) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (u in uris) {
                        if (photos.size >= max) break
                        Uploads.prepare(ctx, u)?.let { photos.add(it) }
                    }
                }
                busy = false
            }
        }
    }
    if (photos.size < max) {
        Spacer(Modifier.height(8.dp))
        GhostButton(
            (if (busy) tr("...جارٍ الإضافة", "Adding...") else "🖼 " + tr("اختيار عدة صور من المعرض", "Pick multiple from gallery")) + " (${photos.size}/$max)",
            onClick = { if (!busy) multiPick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    photos.forEachIndexed { idx, uf ->
        Spacer(Modifier.height(8.dp))
        PhotoPickerField(tr("صورة ", "Photo ") + "${idx + 1}", uf, { nf -> if (nf == null) photos.removeAt(idx) else photos[idx] = nf })
    }
    if (photos.size < max) {
        Spacer(Modifier.height(8.dp))
        PhotoPickerField("＋ " + tr("أضف/التقط صورة", "Add/capture photo") + " (${photos.size}/$max)", null, { nf -> if (nf != null) photos.add(nf) })
    }
}

@Composable
private fun PreviousReportCard(prev: RentalInspection, rentalId: Int, ctx: android.content.Context, nav: NavController) {
    Surface(color = Panel2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(tr("التقرير السابق (التسليم) — كامل", "Previous report (handover) — full"), color = Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            prev.followName?.let { Text(tr("بواسطة: ", "By: ") + it + (prev.createdAt?.let { c -> " · " + c.take(16) } ?: ""), color = Muted, fontSize = 11.sp) }
            prev.km?.takeIf { it.isNotBlank() }?.let { Text(tr("الكيلومتر: ", "KM: ") + it, color = Txt, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            val okItems = prev.checklist?.filterValues { it }?.keys?.toList().orEmpty()
            Text(tr("بنود سليمة (", "OK items (") + "${okItems.size}/${RENTAL_ITEMS.size}): " + (if (okItems.isEmpty()) "—" else okItems.joinToString("، ")),
                color = Txt, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            prev.note?.takeIf { it.isNotBlank() }?.let { Text(tr("ملاحظة: ", "Note: ") + it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            if ((prev.photos?.size ?: 0) > 0) Text(tr("عدد الصور: ", "Photos: ") + "${prev.photos!!.size}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            // زر 1: مراجعة التقرير (رحلة قراءة فقط)
            PrimaryButton(tr("🔍 مراجعة التقرير السابق (رحلة كاملة)", "🔍 Review previous report (full journey)"), modifier = Modifier.fillMaxWidth()) {
                nav.navigate("inspectionReview/$rentalId?kind=handover")
            }
            Spacer(Modifier.height(8.dp))
            // زر 2: فتح/تنزيل PDF
            GhostButton(tr("⎙ فتح/تنزيل التقرير السابق PDF", "⎙ Open/download previous PDF"), onClick = {
                val url = Net.session.baseUrl + "rental_print.php?id=" + rentalId + "&kind=handover&t=" + (Net.session.token ?: "")
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun strokesToBitmap(strokes: List<List<Offset>>, size: IntSize): Bitmap? {
    if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    val p = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK; strokeWidth = 4f; style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true; strokeJoin = android.graphics.Paint.Join.ROUND; strokeCap = android.graphics.Paint.Cap.ROUND
    }
    strokes.forEach { st ->
        val path = android.graphics.Path()
        st.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        c.drawPath(path, p)
    }
    return bmp
}

private suspend fun submitInspection(
    ctx: android.content.Context, rentalId: Int, kind: String, isReturn: Boolean,
    checked: Map<String, Boolean>,
    sideCircles: Map<String, MutableList<Offset>>, sidePhotos: Map<String, MutableList<UploadFile>>,
    tirePhotos: List<MutableList<UploadFile>>, interiorPhotos: MutableList<UploadFile>,
    odometerPhoto: UploadFile?, km: String, note: String, hasIssues: Boolean,
    sigFollowBmp: Bitmap?, sigRenterBmp: Bitmap?,
    onDone: () -> Unit, onErr: (String) -> Unit
) {
    val cl = JsonObject()
    RENTAL_ITEMS.forEach { pair -> cl.addProperty(pair.first, checked[pair.first] == true) }

    val bodyArr = JsonArray()
    SIDES.forEach { s ->
        sideCircles[s.key]!!.forEach { p ->
            val o = JsonObject(); o.addProperty("view", s.key); o.addProperty("x", p.x); o.addProperty("y", p.y); bodyArr.add(o)
        }
    }

    val ordered = mutableListOf<UploadFile>()
    fun ref(uf: UploadFile): String { val name = "photo_${ordered.size}"; ordered.add(uf); return name }

    val data = JsonObject()
    data.add("checklist", cl)
    val bodyObj = JsonObject()
    SIDES.forEach { s ->
        val arr = JsonArray(); sideCircles[s.key]!!.forEach { p -> val o = JsonObject(); o.addProperty("x", p.x); o.addProperty("y", p.y); arr.add(o) }
        bodyObj.add(s.key, arr)
    }
    data.add("body", bodyObj)
    val photosObj = JsonObject()
    SIDES.forEach { s ->
        val arr = JsonArray(); sidePhotos[s.key]!!.forEach { uf -> arr.add(ref(uf)) }; photosObj.add(s.key, arr)
    }
    val intArr = JsonArray(); interiorPhotos.forEach { uf -> intArr.add(ref(uf)) }; photosObj.add("interior", intArr)
    val odoArr = JsonArray(); odometerPhoto?.let { odoArr.add(ref(it)) }; photosObj.add("odometer", odoArr)
    data.add("photos", photosObj)
    val tiresArr = JsonArray()
    tirePhotos.forEach { lst -> val a = JsonArray(); lst.forEach { uf -> a.add(ref(uf)) }; tiresArr.add(a) }
    data.add("tires", tiresArr)
    data.addProperty("km", km.trim())
    data.addProperty("note", note.trim())
    data.addProperty("has_issues", isReturn && hasIssues)

    val sigFollow = sigFollowBmp?.let { Uploads.fromBitmap(ctx, it, "sigf") }
    val sigRenter = sigRenterBmp?.let { Uploads.fromBitmap(ctx, it, "sigr") }

    when (val res = Net.repo.rentalSubmitInspectionV2(
        rentalId, kind, cl.toString(), bodyArr.toString(), data.toString(),
        km.trim(), note.trim(), isReturn && hasIssues, ordered, sigFollow, sigRenter
    )) {
        is Outcome.Ok -> onDone()
        is Outcome.Err -> onErr(res.message)
    }
}
