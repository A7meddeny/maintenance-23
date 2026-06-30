package com.masar.maintenance.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.masar.maintenance.data.*
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.imageUrl
import com.masar.maintenance.ui.theme.*
import com.masar.maintenance.ui.tr
import kotlinx.coroutines.launch

@Composable
fun QuoteResponseScreen(t: MaintTask, onClose: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val items = t.items ?: emptyList()

    val prices = remember { mutableStateListOf<String>().apply { repeat(items.size) { add("") } } }
    var supplierType by remember { mutableStateOf("internal") } // internal | external
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    // مورد خارجي
    var exName by remember { mutableStateOf("") }
    var exBranch by remember { mutableStateOf("") }
    var exMaps by remember { mutableStateOf("") }
    var exPhone by remember { mutableStateOf("") }
    var saveSupplier by remember { mutableStateOf(false) }

    var note by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<UploadFile?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val alreadyResponded = t.quoteStatus == "responded"

    LaunchedEffect(Unit) {
        when (val r = Net.repo.taskSuppliers()) {
            is Outcome.Ok -> suppliers = r.data
            is Outcome.Err -> {}
        }
    }

    MasarScaffold(title = tr("عرض سعر", "Price quote"), onBack = onClose) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            err?.let {
                Surface(color = RedStatus.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = RedStatus, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            MasarCard {
                t.title?.let { Text(it, color = Txt, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(4.dp)) }
                t.carName?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(t.carPhoto, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column { Text(it, color = Txt, fontWeight = FontWeight.Bold); Text(t.plateFull ?: "", color = Muted, fontSize = 12.sp) }
                    }
                }
                t.createdByName?.let { Spacer(Modifier.height(6.dp)); Text(tr("الطلب من: ", "Requested by: ") + it, color = Muted, fontSize = 12.sp) }
                t.note?.takeIf { it.isNotBlank() }?.let { Text(tr("ملاحظة: ", "Note: ") + it, color = Muted, fontSize = 12.sp) }
            }

            if (alreadyResponded) {
                Spacer(Modifier.height(14.dp))
                Surface(color = Green.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(tr("تم تقديم عرضك لهذا الطلب — بانتظار قرار الإدارة.", "Your quote was submitted — awaiting admin decision."),
                        color = Green, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(28.dp))
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(tr("تسعير البنود", "Price the items"))
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { i, it ->
                Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!it.photo.isNullOrBlank()) {
                                RemoteImage(it.photo, modifier = Modifier.size(54.dp).clickable {
                                    imageUrl(it.photo)?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
                                })
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}. ${it.name}", color = Txt, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                it.suggested?.let { s -> Text(tr("سعر مقترح: ", "Suggested: ") + s, color = Muted, fontSize = 12.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        MasarField(prices[i], { prices[i] = it }, tr("سعرك لهذا البند", "Your price"), keyboard = KeyboardType.Number)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(tr("المورد", "Supplier"))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(tr("مورد مسجّل", "Registered"), supplierType == "internal") { supplierType = "internal" }
                ChoiceChip(tr("مورد خارجي", "External"), supplierType == "external") { supplierType = "external" }
            }
            Spacer(Modifier.height(10.dp))
            if (supplierType == "internal") {
                if (suppliers.isEmpty()) Text(tr("لا يوجد موردون مسجّلون — استخدم مورد خارجي.", "No registered suppliers — use external."), color = Muted, fontSize = 12.sp)
                else suppliers.forEach { s ->
                    val on = selectedSupplier?.id == s.id
                    Surface(
                        color = if (on) Red.copy(alpha = 0.14f) else Panel2, shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Red else Line),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedSupplier = s }
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (on) "◉" else "○", color = if (on) Red else Muted, fontSize = 16.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                val sub = listOfNotNull(s.branch, s.phone).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                MasarField(exName, { exName = it }, tr("اسم المورد/الشركة *", "Supplier/company name *"))
                Spacer(Modifier.height(8.dp))
                MasarField(exBranch, { exBranch = it }, tr("الفرع/الموقع", "Branch/location"))
                Spacer(Modifier.height(8.dp))
                MasarField(exMaps, { exMaps = it }, tr("رابط الموقع (قوقل ماب)", "Maps link"))
                Spacer(Modifier.height(8.dp))
                MasarField(exPhone, { exPhone = it }, tr("رقم الهاتف", "Phone"), keyboard = KeyboardType.Phone)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saveSupplier = !saveSupplier }) {
                    Text(if (saveSupplier) "☑" else "☐", color = if (saveSupplier) Green else Muted, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("حفظ هذا المورد للاستخدام مستقبلاً", "Save this supplier for future use"), color = Txt, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(tr("مرفق العرض (صورة/PDF)", "Quote attachment (image/PDF)"))
            Spacer(Modifier.height(8.dp))
            PhotoPickerField(tr("إرفاق عرض السعر", "Attach quote"), attachment, { attachment = it }, allowPdf = true)
            Spacer(Modifier.height(10.dp))
            MasarField(note, { note = it }, tr("ملاحظة (اختياري)", "Note (optional)"), singleLine = false)

            Spacer(Modifier.height(18.dp))
            PrimaryButton(tr("إرسال العرض", "Send quote"), loading = submitting) {
                err = null
                if (supplierType == "internal" && selectedSupplier == null) { err = tr("اختر المورد المسجّل", "Select a registered supplier"); return@PrimaryButton }
                if (supplierType == "external" && exName.isBlank()) { err = tr("اكتب اسم المورد الخارجي", "Enter external supplier name"); return@PrimaryButton }
                if (prices.all { it.isBlank() }) { err = tr("أدخل سعر بند واحد على الأقل", "Enter at least one price"); return@PrimaryButton }
                submitting = true
                scope.launch {
                    val q = JsonObject()
                    q.addProperty("supplier_type", supplierType)
                    if (supplierType == "internal") {
                        selectedSupplier?.let {
                            q.addProperty("supplier_id", it.id)
                            q.addProperty("supplier_name", it.name)
                            it.branch?.let { b -> q.addProperty("supplier_branch", b) }
                            it.mapsUrl?.let { m -> q.addProperty("supplier_maps", m) }
                            it.phone?.let { p -> q.addProperty("supplier_phone", p) }
                        }
                    } else {
                        q.addProperty("supplier_name", exName.trim())
                        q.addProperty("supplier_branch", exBranch.trim())
                        q.addProperty("supplier_maps", exMaps.trim())
                        q.addProperty("supplier_phone", exPhone.trim())
                        q.addProperty("save_supplier", saveSupplier)
                    }
                    q.addProperty("note", note.trim())
                    val arr = JsonArray()
                    prices.forEachIndexed { i, p ->
                        if (p.isNotBlank()) {
                            val o = JsonObject(); o.addProperty("item", i); o.addProperty("name", items.getOrNull(i)?.name ?: "")
                            o.addProperty("price", p.trim().toDoubleOrNull() ?: 0.0); arr.add(o)
                        }
                    }
                    q.add("prices", arr)
                    when (val r = Net.repo.taskSubmitQuote(t.id, q.toString(), attachment)) {
                        is Outcome.Ok -> { submitting = false; onDone() }
                        is Outcome.Err -> { submitting = false; err = r.message }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
