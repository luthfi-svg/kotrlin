package com.kaskita.app

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------- Palette (dari gambar referensi) ----------
private val Bg = Color(0xFFFFFFFF)
private val Ink = Color(0xFF282754)
private val Purple = Color(0xFFD3C2FA)
private val Mint = Color(0xFFB4F0D6)
private val Coral = Color(0xFFFF8F84)
private val Blue = Color(0xFFBBD9FA)
private val Yellow = Color(0xFFFFEB99)
private val Peach = Color(0xFFFFD2BF)
private val Pink = Color(0xFFFFC2D6)
private val Cloud = Color(0xFFF6F4FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KasKitaApp() }
    }
}

data class Member(val name: String, val paid: Boolean = false)
data class Expense(val title: String, val amount: Long)

private fun parseNames(raw: String): List<String> =
    raw.split("\n", ",", ";")
        .map { it.trim().replace(Regex("^\\d+[.)]\\s*"), "") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

private fun moneyFmt(amount: Long): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount).replace(",00", "")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KasKitaApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("kaskita", 0) }
    var members by remember { mutableStateOf(loadMembers(prefs)) }
    var expenses by remember { mutableStateOf(loadExpenses(prefs)) }
    var monthlyFee by remember { mutableLongStateOf(prefs.getLong("fee", 10000L)) }
    var addMemberDialog by remember { mutableStateOf(false) }
    var addExpenseDialog by remember { mutableStateOf(false) }
    var namesInput by remember { mutableStateOf("") }
    var expenseName by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var feeInput by remember { mutableStateOf(monthlyFee.toString()) }

    val paidCount = members.count { it.paid }
    val paidTotal = paidCount * monthlyFee
    val expenseTotal = expenses.sumOf { it.amount }

    fun saveMembers() {
        prefs.edit().putString("members", JSONArray().apply {
            members.forEach { put(JSONObject().put("name", it.name).put("paid", it.paid)) }
        }.toString()).apply()
    }
    fun saveExpenses() {
        prefs.edit().putString("expenses", JSONArray().apply {
            expenses.forEach { put(JSONObject().put("title", it.title).put("amount", it.amount)) }
        }.toString()).apply()
    }
    fun toggleMember(i: Int) { members = members.toMutableList().also { it[i] = it[i].copy(paid = !it[i].paid) }; saveMembers() }
    fun deleteMember(i: Int) { members = members.toMutableList().also { it.removeAt(i) }; saveMembers() }
    fun deleteExpense(i: Int) { expenses = expenses.toMutableList().also { it.removeAt(i) }; saveExpenses() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { writePdf(it, members, expenses, monthlyFee) }
                Toast.makeText(ctx, "PDF berhasil disimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val pagerState = rememberPagerState { 3 }
    val bounceFling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
    )

    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Bg, surface = Bg)) {
        Scaffold(containerColor = Bg, bottomBar = {
            LiquidBottomBar(pagerState.currentPage, pagerState.currentPageOffsetFraction) { i ->
                scope.launch { pagerState.animateScrollToPage(i, animationSpec = spring(0.55f, Spring.StiffnessLow)) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("KasKita", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text("Kas kelas jadi lebih tertata", color = Ink.copy(alpha = .6f))
                    }
                    IconTile("🏫", Pink)
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    flingBehavior = bounceFling,
                    beyondViewportPageCount = 1
                ) { page ->
                    val off = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val pageMod = Modifier.fillMaxSize().graphicsLayer {
                        val s = 1f - 0.10f * off
                        scaleX = s * (1f - 0.04f * off)   // efek "squish" cair
                        scaleY = s
                        alpha = 1f - 0.45f * off
                    }
                    val pad = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 20.dp)
                    when (page) {
                        0 -> LazyColumn(pageMod, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard("Pemasukan", moneyFmt(paidTotal), Mint, "💰", Modifier.weight(1f))
                                    StatCard("Pengeluaran", moneyFmt(expenseTotal), Peach, "🧾", Modifier.weight(1f))
                                }
                            }
                            item { StatCard("Saldo kas", moneyFmt(paidTotal - expenseTotal), Purple, "🏦", Modifier.fillMaxWidth()) }
                            item {
                                ClayBox(Yellow) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconTile("📊", Color.White, 44.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Pembayaran bulan ini", color = Ink.copy(alpha = .7f))
                                            Text("$paidCount/${members.size} lunas", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                            item {
                                ClayBox(Blue) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Iuran per orang", color = Ink.copy(alpha = .7f))
                                        Text(moneyFmt(monthlyFee), fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 22.sp)
                                        ClayField(feeInput, { feeInput = it.filter(Char::isDigit) }, "Ubah nominal iuran (Rp)", Peach, number = true)
                                        ClayButton("Simpan iuran", Coral, Color.White, Modifier.fillMaxWidth()) {
                                            feeInput.toLongOrNull()?.let { monthlyFee = it; prefs.edit().putLong("fee", it).apply() }
                                        }
                                    }
                                }
                            }
                            item { ClayButton("⬇  Ekspor PDF Statistik", Mint, Ink, Modifier.fillMaxWidth()) { exportLauncher.launch("KasKita-Laporan.pdf") } }
                        }

                        1 -> LazyColumn(pageMod, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Status pembayaran", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 18.sp)
                                        Text("$paidCount dari ${members.size} siswa lunas", color = Ink.copy(alpha = .6f))
                                    }
                                    ClayButton("+ Tambah", Purple, Ink) { addMemberDialog = true }
                                }
                            }
                            if (members.isEmpty()) item { Text("Belum ada siswa. Tekan + Tambah, lalu tempel semua nama sekaligus.", color = Ink.copy(alpha = .6f)) }
                            itemsIndexed(members) { i, m -> MemberRow(m, { toggleMember(i) }, { deleteMember(i) }) }
                        }

                        else -> LazyColumn(pageMod, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Catatan pengeluaran", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                    ClayButton("+ Catat", Yellow, Ink) { addExpenseDialog = true }
                                }
                            }
                            item { StatCard("Total pengeluaran", moneyFmt(expenseTotal), Peach, "🧾", Modifier.fillMaxWidth()) }
                            itemsIndexed(expenses) { i, e ->
                                ClayBox(Cloud) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(e.title, fontWeight = FontWeight.Bold, color = Ink)
                                            Text(moneyFmt(e.amount), color = Ink.copy(alpha = .7f))
                                        }
                                        IconTile("✕", Coral, 36.dp, Modifier.clickable { deleteExpense(i) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addMemberDialog) {
        val parsed = parseNames(namesInput)
        val existing = members.map { it.name.lowercase() }.toSet()
        val fresh = parsed.filter { it.lowercase() !in existing }
        AlertDialog(
            onDismissRequest = { addMemberDialog = false },
            containerColor = Bg,
            shape = RoundedCornerShape(32.dp),
            title = { Text("Tambah siswa", fontWeight = FontWeight.ExtraBold, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tempel semua nama sekaligus. Pisahkan dengan baris baru, koma, atau titik koma.", color = Ink.copy(alpha = .65f))
                    ClayField(namesInput, { namesInput = it }, "Budi\nSiti\nAndi, Dewi", Purple, singleLine = false, minLines = 6)
                    Text("${fresh.size} nama akan ditambahkan", fontWeight = FontWeight.Bold, color = Ink)
                }
            },
            confirmButton = {
                ClayButton("Tambah", Coral, Color.White) {
                    if (fresh.isNotEmpty()) { members = members + fresh.map { Member(it) }; saveMembers() }
                    namesInput = ""; addMemberDialog = false
                }
            },
            dismissButton = { TextButton(onClick = { addMemberDialog = false }) { Text("Batal", color = Ink) } }
        )
    }

    if (addExpenseDialog) AlertDialog(
        onDismissRequest = { addExpenseDialog = false },
        containerColor = Bg,
        shape = RoundedCornerShape(32.dp),
        title = { Text("Tambah pengeluaran", fontWeight = FontWeight.ExtraBold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayField(expenseName, { expenseName = it }, "Keterangan", Peach)
                ClayField(expenseAmount, { expenseAmount = it.filter(Char::isDigit) }, "Nominal (Rp)", Peach, number = true)
            }
        },
        confirmButton = {
            ClayButton("Simpan", Coral, Color.White) {
                val amount = expenseAmount.toLongOrNull()
                if (expenseName.isNotBlank() && amount != null && amount > 0) {
                    expenses = expenses + Expense(expenseName.trim(), amount); saveExpenses()
                    expenseName = ""; expenseAmount = ""
                }
                addExpenseDialog = false
            }
        },
        dismissButton = { TextButton(onClick = { addExpenseDialog = false }) { Text("Batal", color = Ink) } }
    )
}

// ---------- Bottom bar dengan indikator "liquid" ----------
@Composable
fun LiquidBottomBar(page: Int, fraction: Float, onSelect: (Int) -> Unit) {
    val items = listOf("🏠" to "Ringkasan", "💳" to "Pembayaran", "🧾" to "Pengeluaran")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).clay(Cloud, RoundedCornerShape(32.dp)).padding(6.dp)) {
        val tabW = maxWidth / 3
        val stretch = abs(fraction)
        // indikator melar saat digeser, lalu memantul kembali lewat spring pager
        Box(
            Modifier
                .offset { IntOffset(((page + fraction) * tabW.roundToPx() - tabW.roundToPx() * stretch * 0.3f).toInt(), 0) }
                .width(tabW + tabW * (stretch * 0.6f))
                .height(64.dp)
                .clay(Blue, RoundedCornerShape(24.dp), 6.dp)
        )
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, (icon, label) ->
                Column(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(24.dp)).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text(icon, fontSize = 20.sp)
                    Text(label, fontSize = 11.sp, color = Ink, fontWeight = if (page == i) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

// ---------- Komponen clay ----------
fun Color.lighten(f: Float) = lerp(this, Color.White, f)
fun Color.darken(f: Float) = lerp(this, Ink, f)

fun Modifier.clay(color: Color, shape: Shape = RoundedCornerShape(28.dp), elevation: Dp = 10.dp): Modifier =
    this.shadow(elevation, shape, ambientColor = color.darken(.4f), spotColor = color.darken(.4f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(color.lighten(.35f), color, color.darken(.08f))))
        .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(.95f), Color.White.copy(0f), Color.White.copy(.3f))), shape)

@Composable
fun ClayBox(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clay(color), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
fun IconTile(icon: String, color: Color, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clay(color, RoundedCornerShape(size / 3), 6.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = (size.value * .42f).sp, color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, icon: String, modifier: Modifier = Modifier) {
    Box(modifier.clay(color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Ink.copy(alpha = .7f), fontSize = 13.sp)
                Text(value, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1)
            }
            IconTile(icon, Color.White, 40.dp)
        }
    }
}

@Composable
fun ClayButton(text: String, color: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(52.dp).clay(color, RoundedCornerShape(50), 8.dp).clickable { onClick() }.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
fun ClayField(value: String, onChange: (String) -> Unit, hint: String, color: Color, singleLine: Boolean = true, minLines: Int = 1, number: Boolean = false) {
    Box(Modifier.fillMaxWidth().clay(color, RoundedCornerShape(if (singleLine) 50 else 28), 6.dp)) {
        TextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = Ink.copy(alpha = .5f)) },
            singleLine = singleLine, minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ink
            )
        )
    }
}

@Composable
fun ClaySwitch(checked: Boolean, onChange: () -> Unit) {
    val x by animateDpAsState(if (checked) 26.dp else 0.dp, spring(0.45f, Spring.StiffnessMedium), label = "knob")
    Box(Modifier.width(58.dp).height(32.dp).clay(if (checked) Mint.darken(.08f) else Blue, CircleShape, 4.dp).clickable { onChange() }.padding(4.dp)) {
        Box(Modifier.offset(x = x).size(24.dp).clay(Color.White, CircleShape, 3.dp))
    }
}

@Composable
fun StatusChip(paid: Boolean) {
    val c = if (paid) Mint else Yellow
    Row(Modifier.clay(c, RoundedCornerShape(50), 4.dp).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (paid) Color(0xFF2EAD7A) else Color(0xFFF2A900)))
        Spacer(Modifier.width(6.dp))
        Text(if (paid) "Lunas" else "Belum bayar", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun MemberRow(member: Member, onToggle: () -> Unit, onDelete: () -> Unit) {
    ClayBox(Cloud) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clay(if (member.paid) Mint else Purple, CircleShape, 4.dp), contentAlignment = Alignment.Center) {
                Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Black, color = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.name, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                StatusChip(member.paid)
            }
            ClaySwitch(member.paid, onToggle)
            Spacer(Modifier.width(10.dp))
            IconTile("✕", Coral.copy(alpha = .8f), 32.dp, Modifier.clickable { onDelete() })
        }
    }
}

// ---------- Ekspor PDF ----------
fun writePdf(out: OutputStream, members: List<Member>, expenses: List<Expense>, fee: Long) {
    val W = 595; val H = 842; val M = 40f
    val doc = PdfDocument()
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
    var c = page.canvas
    var y = M

    fun paint(size: Float, bold: Boolean = false, col: String = "#282754", right: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; isFakeBoldText = bold; color = android.graphics.Color.parseColor(col)
        textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
    }
    fun fill(col: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor(col) }
    fun ensure(space: Float) {
        if (y + space > H - M) {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas; y = M
        }
    }

    val paidCount = members.count { it.paid }
    val income = paidCount * fee
    val spent = expenses.sumOf { it.amount }
    val arrears = (members.size - paidCount) * fee
    val pct = if (members.isEmpty()) 0 else paidCount * 100 / members.size

    c.drawText("Laporan KasKita", M, y + 24f, paint(26f, true)); y += 34f
    c.drawText("Dibuat ${SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date())}", M, y + 12f, paint(12f, col = "#777799")); y += 32f

    // kartu ringkasan
    val cards = listOf(
        Triple("Pemasukan", moneyFmt(income), "#B4F0D6"), Triple("Pengeluaran", moneyFmt(spent), "#FFD2BF"),
        Triple("Saldo kas", moneyFmt(income - spent), "#D3C2FA"), Triple("Tunggakan", moneyFmt(arrears), "#FFEB99")
    )
    val cw = (W - 2 * M - 12f) / 2
    cards.forEachIndexed { i, (l, v, col) ->
        val cx = M + (i % 2) * (cw + 12f); val cy = y + (i / 2) * 70f
        c.drawRoundRect(RectF(cx, cy, cx + cw, cy + 58f), 16f, 16f, fill(col))
        c.drawText(l, cx + 14f, cy + 21f, paint(11f, col = "#505078"))
        c.drawText(v, cx + 14f, cy + 44f, paint(17f, true))
    }
    y += 150f

    // bar progres lunas
    c.drawText("Kelunasan: $paidCount/${members.size} siswa ($pct%)  •  Iuran ${moneyFmt(fee)}/orang", M, y + 12f, paint(12f, true)); y += 22f
    c.drawRoundRect(RectF(M, y, W - M, y + 14f), 7f, 7f, fill("#EEE9FB"))
    if (pct > 0) c.drawRoundRect(RectF(M, y, M + (W - 2 * M) * pct / 100f, y + 14f), 7f, 7f, fill("#6FD3A5"))
    y += 38f

    // tabel siswa
    ensure(60f)
    c.drawText("Daftar Siswa", M, y + 16f, paint(16f, true)); y += 30f
    c.drawRoundRect(RectF(M, y, W - M, y + 24f), 8f, 8f, fill("#D3C2FA"))
    c.drawText("No", M + 10f, y + 16f, paint(11f, true)); c.drawText("Nama", M + 45f, y + 16f, paint(11f, true))
    c.drawText("Status", W - M - 10f, y + 16f, paint(11f, true, right = true)); y += 30f
    members.forEachIndexed { i, m ->
        ensure(24f)
        if (i % 2 == 0) c.drawRect(RectF(M, y - 4f, W - M, y + 20f), fill("#F6F4FB"))
        c.drawText("${i + 1}", M + 10f, y + 13f, paint(11f)); c.drawText(m.name, M + 45f, y + 13f, paint(11f))
        c.drawText(if (m.paid) "Lunas" else "Belum bayar", W - M - 10f, y + 13f, paint(11f, true, if (m.paid) "#1E9A68" else "#D18A00", true))
        y += 24f
    }
    if (members.isEmpty()) { c.drawText("Belum ada data siswa", M + 10f, y + 13f, paint(11f)); y += 24f }

    // tabel pengeluaran
    y += 16f; ensure(80f)
    c.drawText("Rincian Pengeluaran", M, y + 16f, paint(16f, true)); y += 30f
    c.drawRoundRect(RectF(M, y, W - M, y + 24f), 8f, 8f, fill("#FFD2BF"))
    c.drawText("Keterangan", M + 10f, y + 16f, paint(11f, true)); c.drawText("Nominal", W - M - 10f, y + 16f, paint(11f, true, right = true)); y += 30f
    expenses.forEachIndexed { i, e ->
        ensure(24f)
        if (i % 2 == 0) c.drawRect(RectF(M, y - 4f, W - M, y + 20f), fill("#F6F4FB"))
        c.drawText(e.title, M + 10f, y + 13f, paint(11f)); c.drawText(moneyFmt(e.amount), W - M - 10f, y + 13f, paint(11f, right = true))
        y += 24f
    }
    if (expenses.isEmpty()) { c.drawText("Belum ada pengeluaran", M + 10f, y + 13f, paint(11f)); y += 24f }
    ensure(30f)
    c.drawText("Total: ${moneyFmt(spent)}", W - M - 10f, y + 20f, paint(13f, true, right = true))

    doc.finishPage(page)
    doc.writeTo(out)
    doc.close()
}

// ---------- Penyimpanan ----------
fun loadMembers(prefs: android.content.SharedPreferences): List<Member> = try {
    val a = JSONArray(prefs.getString("members", "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); Member(o.optString("name"), o.optBoolean("paid")) }
} catch (_: Exception) { emptyList() }

fun loadExpenses(prefs: android.content.SharedPreferences): List<Expense> = try {
    val a = JSONArray(prefs.getString("expenses", "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); Expense(o.optString("title"), o.optLong("amount")) }
} catch (_: Exception) { emptyList() }
