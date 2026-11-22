package com.kaskita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private val Bg = Color(0xFFFFF9F5)
private val Ink = Color(0xFF282754)
private val Purple = Color(0xFFD8C5FF)
private val Mint = Color(0xFFBDF5DE)
private val Coral = Color(0xFFFF9D91)
private val Blue = Color(0xFFBDE1FF)
private val Yellow = Color(0xFFFFE79D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { KasKitaApp() }
    }
}

data class Member(val name: String, val paid: Boolean = false)
data class Expense(val title: String, val amount: Long)


@Composable
fun KasKitaApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("kaskita", 0) }
    var members by remember { mutableStateOf(loadMembers(prefs)) }
    var expenses by remember { mutableStateOf(loadExpenses(prefs)) }
    var monthlyFee by remember { mutableLongStateOf(prefs.getLong("fee", 10000L)) }
    var tab by remember { mutableStateOf(0) }
    var addMemberDialog by remember { mutableStateOf(false) }
    var addExpenseDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var expenseName by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var feeInput by remember { mutableStateOf(monthlyFee.toString()) }
    val paidTotal = members.count { it.paid } * monthlyFee
    val expenseTotal = expenses.sumOf { it.amount }
    val money: (Long) -> String = { amount -> NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount).replace(",00", "") }

    fun saveMembers() { prefs.edit().putString("members", JSONArray().apply { members.forEach { put(JSONObject().put("name", it.name).put("paid", it.paid)) } }.toString()).apply() }
    fun saveExpenses() { prefs.edit().putString("expenses", JSONArray().apply { expenses.forEach { put(JSONObject().put("title", it.title).put("amount", it.amount)) } }.toString()).apply() }
    fun toggleMember(index: Int) { members = members.toMutableList().also { it[index] = it[index].copy(paid = !it[index].paid) }; saveMembers() }
    fun deleteMember(index: Int) { members = members.toMutableList().also { it.removeAt(index) }; saveMembers() }

    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Bg, surface = Bg)) {
        Scaffold(containerColor = Bg, bottomBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clay(Blue), horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomItem("Ringkasan", tab == 0) { tab = 0 }
                BottomItem("Pembayaran", tab == 1) { tab = 1 }
                BottomItem("Pengeluaran", tab == 2) { tab = 2 }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("KasKita", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Ink)
                        Text("Kas kelas jadi lebih tertata", color = Ink.copy(alpha = .65f))
                    }
                    Text("🏫", style = MaterialTheme.typography.headlineMedium)
                }
                when (tab) {
                    0 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Pemasukan", money(paidTotal), Mint, Modifier.weight(1f))
                            StatCard("Pengeluaran", money(expenseTotal), Coral.copy(alpha=.7f), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        StatCard("Saldo kas", money(paidTotal - expenseTotal), Purple, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Pembayaran bulan ini", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f)); Text("${members.count { it.paid }}/${members.size} lunas", color = Ink.copy(alpha=.65f)) }
                        Spacer(Modifier.height(8.dp))
                        ClayBox(Blue) { Column(Modifier.padding(14.dp)) {
                            Text("Iuran per orang", color = Ink.copy(alpha=.7f)); Text(money(monthlyFee), fontWeight = FontWeight.Bold, color = Ink)
                            OutlinedTextField(feeInput, { feeInput = it }, label = { Text("Ubah nominal iuran (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = { feeInput.toLongOrNull()?.let { monthlyFee = it; prefs.edit().putLong("fee",it).apply() } }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Simpan iuran") }
                        } }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Daftar siswa", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f)); Button(onClick = { addMemberDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)) { Text("+ Tambah") } }
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(members) { index, m -> MemberRow(m, onToggle = { toggleMember(index) }, onDelete = { deleteMember(index) }) }
                        }
                    }
                    1 -> {
                        Text("Status pembayaran", fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(members) { index, m -> MemberRow(m, onToggle = { toggleMember(index) }, onDelete = { deleteMember(index) }) } }
                        Button(onClick = { addMemberDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)) { Text("+ Tambah siswa") }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Catatan pengeluaran", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f)); Button(onClick = { addExpenseDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink)) { Text("+ Catat") } }
                        StatCard("Total pengeluaran", money(expenseTotal), Coral.copy(alpha=.7f), Modifier.fillMaxWidth())
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(expenses) { index, e ->
                            ClayBox(Color.White) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold, color = Ink); Text(money(e.amount), color = Ink.copy(alpha=.7f)) }; Text("✕", color = Coral, modifier = Modifier.clickable { expenses = expenses.toMutableList().also { it.removeAt(index) }; saveExpenses() }.padding(8.dp)) } }
                        } }
                    }
                }
            }
        }
    }
    if (addMemberDialog) AlertDialog(onDismissRequest = { addMemberDialog = false }, title = { Text("Tambah siswa") }, text = { OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Nama siswa") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (nameInput.isNotBlank()) { members = members + Member(nameInput.trim()); saveMembers(); nameInput = "" }; addMemberDialog = false }) { Text("Tambah") } }, dismissButton = { TextButton(onClick = { addMemberDialog = false }) { Text("Batal") } })
    if (addExpenseDialog) AlertDialog(onDismissRequest = { addExpenseDialog = false }, title = { Text("Tambah pengeluaran") }, text = { Column { OutlinedTextField(expenseName, { expenseName = it }, label = { Text("Keterangan") }, singleLine = true); OutlinedTextField(expenseAmount, { expenseAmount = it }, label = { Text("Nominal (Rp)") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { val amount = expenseAmount.toLongOrNull(); if (expenseName.isNotBlank() && amount != null && amount > 0) { expenses = expenses + Expense(expenseName.trim(), amount); saveExpenses(); expenseName = ""; expenseAmount = "" }; addExpenseDialog = false }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { addExpenseDialog = false }) { Text("Batal") } })
}


@Composable fun BottomItem(label: String, selected: Boolean, onClick: () -> Unit) { Text(label, modifier = Modifier.clickable { onClick() }.padding(10.dp), color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
@Composable fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) { ClayBox(color, modifier) { Column(Modifier.padding(16.dp)) { Text(label, color = Ink.copy(alpha=.7f)); Text(value, color = Ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge) } } }
@Composable fun MemberRow(member: Member, onToggle: () -> Unit, onDelete: () -> Unit) { ClayBox(if (member.paid) Mint else Color.White) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(member.name, fontWeight = FontWeight.Bold, color = Ink); Text(if (member.paid) "Sudah bayar" else "Belum bayar", color = Ink.copy(alpha=.65f)) }; Switch(checked = member.paid, onCheckedChange = { onToggle() }); Text("×", modifier = Modifier.clickable { onDelete() }.padding(start = 10.dp), color = Ink) } } }
@Composable fun ClayBox(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) { Box(modifier.clay(color), contentAlignment = Alignment.CenterStart) { content() } }
fun Modifier.clay(color: Color): Modifier { val shape = RoundedCornerShape(24.dp); return this.shadow(7.dp, shape).clip(shape).background(color).border(1.dp, Color.White.copy(alpha = .7f), shape).padding(1.dp) }

fun loadMembers(prefs: android.content.SharedPreferences): List<Member> { return try { val a = JSONArray(prefs.getString("members", "[]")); (0 until a.length()).map { val o=a.getJSONObject(it); Member(o.optString("name"), o.optBoolean("paid")) } } catch (_: Exception) { emptyList() } }
fun loadExpenses(prefs: android.content.SharedPreferences): List<Expense> { return try { val a=JSONArray(prefs.getString("expenses", "[]")); (0 until a.length()).map { val o=a.getJSONObject(it); Expense(o.optString("title"), o.optLong("amount")) } } catch (_: Exception) { emptyList() } }
