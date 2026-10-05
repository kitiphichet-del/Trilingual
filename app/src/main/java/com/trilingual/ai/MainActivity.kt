package com.trilingual.ai

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trilingual.ai.data.Meeting
import com.trilingual.ai.data.Phrase
import com.trilingual.ai.service.SubtitleOverlayService
import com.trilingual.ai.speech.RecognitionService
import com.trilingual.ai.translation.DeviceTranslator
import com.trilingual.ai.translation.Notes
import com.trilingual.ai.translation.OnlineTextAi
import com.trilingual.ai.util.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val app: TriLingualApp get() = application as TriLingualApp
    private var pip by mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var pendingStart: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingStart?.invoke()
        pendingStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { }
        setContent {
            val colors = lightColorScheme(primary = Color(0xFF3159CB), secondary = Color(0xFF5367A1), background = Color(0xFFF8FAFE), surface = Color.White)
            MaterialTheme(colorScheme = colors) { MainScreen() }
        }
    }
    private fun start(mode: String, lang: String) {
        val go = {
            ContextCompat.startForegroundService(this, Intent(this, RecognitionService::class.java)
                .setAction(RecognitionService.ACTION_START)
                .putExtra(RecognitionService.EXTRA_MODE, mode)
                .putExtra(RecognitionService.EXTRA_LANGUAGE, lang))
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) go()
        else { pendingStart = go; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    private fun command(action: String, extra: String? = null, value: String? = null) {
        startService(Intent(this, RecognitionService::class.java).setAction(action).apply {
            if (extra != null && value != null) putExtra(extra, value)
        })
    }
    private fun speak(text: String, language: String) {
        tts?.language = Locale.forLanguageTag(Language.tag(language))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation-${System.currentTimeMillis()}")
    }
    private fun showPip() {
        if (Build.VERSION.SDK_INT >= 26) enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }
    private fun setOverlay(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        app.settings.overlayEnabled = enabled
        startService(Intent(this, SubtitleOverlayService::class.java).setAction(
            if (enabled) SubtitleOverlayService.ACTION_SHOW else SubtitleOverlayService.ACTION_HIDE
        ))
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip = isInPictureInPictureMode
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }

    @Composable private fun MainScreen() {
        val live by app.live.view.collectAsState()
        val meetings by app.store.meetings.collectAsState()
        val phrases by app.store.phrases.collectAsState()
        var page by rememberSaveable { mutableStateOf("translate") }
        var viewing by rememberSaveable { mutableLongStateOf(0L) }
        var selectedMode by rememberSaveable { mutableStateOf("conversation") }
        var selectedLanguage by rememberSaveable { mutableStateOf("th") }
        var uiError by rememberSaveable { mutableStateOf("") }
        var pendingDeletion by remember { mutableStateOf<Meeting?>(null) }
        var deletingId by remember { mutableLongStateOf(0L) }
        val current = meetings.firstOrNull { it.id == viewing }
        val last = phrases.lastOrNull()
        val scope = rememberCoroutineScope()
        val transcriptScroll = rememberLazyListState()
        LaunchedEffect(phrases.size, page) {
            if (page == "translate" && phrases.isNotEmpty()) transcriptScroll.animateScrollToItem(phrases.size - 1)
        }
        var exportBody by remember { mutableStateOf("") }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(exportBody.toByteArray(Charsets.UTF_8)) } }
                .onFailure { uiError = "ส่งออกไม่สำเร็จ: ${it.message}" }
        }
        LaunchedEffect(live.meetingId) {
            if (live.meetingId != 0L) { viewing = live.meetingId; app.store.selectMeeting(viewing) }
        }
        LaunchedEffect(viewing) { app.store.selectMeeting(viewing) }

        // A confirmation is required: deleting a saved session also removes all its phrases.
        pendingDeletion?.let { meeting ->
            AlertDialog(
                onDismissRequest = { if (deletingId == 0L) pendingDeletion = null },
                icon = { Icon(Icons.Default.DeleteOutline, null) },
                title = { Text("ลบบันทึกนี้หรือไม่?") },
                text = { Text("${meeting.title}\nข้อความต้นฉบับ คำแปล และสรุปทั้งหมดในรายการนี้จะถูกลบถาวรและไม่สามารถกู้คืนได้") },
                confirmButton = {
                    TextButton(
                        enabled = deletingId == 0L,
                        onClick = {
                            if (live.running && live.meetingId == meeting.id) {
                                uiError = "กรุณาหยุดบันทึกก่อนลบรายการที่กำลังใช้งาน"
                                pendingDeletion = null
                            } else {
                                deletingId = meeting.id
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) { app.store.deleteMeeting(meeting.id) }
                                    }
                                    if (result.getOrDefault(false)) {
                                        if (viewing == meeting.id) viewing = 0L
                                    } else {
                                        uiError = "ลบรายการไม่สำเร็จ: ${result.exceptionOrNull()?.message ?: "ไม่พบรายการ"}"
                                    }
                                    deletingId = 0L
                                    pendingDeletion = null
                                }
                            }
                        }
                    ) { Text(if (deletingId != 0L) "กำลังลบ…" else "ลบถาวร", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(enabled = deletingId == 0L, onClick = { pendingDeletion = null }) { Text("ยกเลิก") }
                }
            )
        }

        if (pip) {
            Box(Modifier.fillMaxSize().background(Color(0xFF14213D)).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(last?.thai?.ifBlank { last?.source.orEmpty() } ?: live.partial.ifBlank { "TriLingual AI · กำลังฟัง" }, color = Color.White, fontSize = 19.sp, lineHeight = 25.sp)
            }
            return
        }
        Scaffold(
            topBar = { Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column { Text("TriLingual AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("ไทย  ·  English  ·  简体中文", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.weight(1f))
                    Text("v0.1.2", color = Color.Gray, fontSize = 12.sp)
                }
            } },
            bottomBar = { NavigationBar {
                listOf(Triple("translate", Icons.Default.Mic, "แปลสด"), Triple("history", Icons.Default.History, "บันทึก"), Triple("presentation", Icons.Default.Slideshow, "นำเสนอ"), Triple("settings", Icons.Default.Settings, "ตั้งค่า")).forEach { (route, icon, title) ->
                    NavigationBarItem(selected = page == route, onClick = { page = route }, icon = { Icon(icon, title) }, label = { Text(title, fontSize = 11.sp) })
                }
            } }
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).background(MaterialTheme.colorScheme.background)) {
                if (uiError.isNotBlank()) {
                    Text(uiError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                    TextButton(onClick = { uiError = "" }) { Text("ปิดข้อความ") }
                }
                when (page) {
                    "translate" -> {
                        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedMode == "conversation", onClick = { selectedMode = "conversation" }, label = { Text("สนทนาสด") }, leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) })
                                FilterChip(selected = selectedMode == "meeting", onClick = { selectedMode = "meeting" }, label = { Text("การประชุม") }, leadingIcon = { Icon(Icons.Default.Groups, null) })
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("th", "en", "zh", "auto").forEach { code ->
                                    FilterChip(selected = (if (live.running) live.language else selectedLanguage) == code,
                                        onClick = { selectedLanguage = code; if (live.running) command(RecognitionService.ACTION_LANGUAGE, RecognitionService.EXTRA_LANGUAGE, code) },
                                        label = { Text(if (code == "zh") "中文" else if (code == "en") "EN" else if (code == "auto") "Auto*" else "ไทย", fontSize = 12.sp) })
                                }
                            }
                            val activeLanguage = if (live.running) live.language else selectedLanguage
                            if (activeLanguage == "auto") Text(
                                if (Build.VERSION.SDK_INT >= 34) "*Auto ใช้ความสามารถตรวจจับภาษาของ Android; หากจีนไม่ถูกจับให้เลือก 中文 โดยตรง"
                                else "*เครื่องรุ่นนี้ไม่มี Auto language detection แบบเต็ม สำหรับจีน → ไทย กรุณาเลือก 中文 ก่อนพูด",
                                style = MaterialTheme.typography.labelSmall, color = Color.Gray
                            )
                            if (activeLanguage == "zh") Text(
                                "จีน → ไทย: ใช้ภาษาจีนกลาง (简体中文) · ถ้าชุดเสียงจีนออฟไลน์ไม่พร้อม แอปจะลองสลับเป็นบริการระบบแบบ Hybrid",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary
                            )
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(live.status, color = if (live.running) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                    Spacer(Modifier.height(5.dp))
                                    LinearProgressIndicator(progress = { live.loudness }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!live.running) Button(onClick = { start(selectedMode, selectedLanguage) }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(5.dp)); Text("เริ่มแปลเสียง") }
                                        else {
                                            Button(onClick = { command(if (live.paused) RecognitionService.ACTION_RESUME else RecognitionService.ACTION_PAUSE) }) {
                                                Icon(if (live.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                                Text(if (live.paused) "ฟังต่อ" else "พัก")
                                            }
                                            OutlinedButton(onClick = { command(RecognitionService.ACTION_STOP) }) { Text("หยุด/บันทึก") }
                                        }
                                    }
                                    if (live.running) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("กำหนดผู้พูด (ด้วยตนเอง)", style = MaterialTheme.typography.labelSmall)
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            (1..4).forEach { n -> FilterChip(selected = live.speaker == n, onClick = {
                                                startService(Intent(this@MainActivity, RecognitionService::class.java).setAction(RecognitionService.ACTION_SPEAKER).putExtra(RecognitionService.EXTRA_SPEAKER, n))
                                            }, label = { Text("คน $n") }) }
                                        }
                                    }
                                    if (live.partial.isNotBlank()) Text("ฟังอยู่: ${live.partial}", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("ข้อความและคำแปล · ${phrases.size} ช่วงเสียง", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(Modifier.weight(1f), state = transcriptScroll, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                                if (phrases.isEmpty()) item { Text("แตะ ‘เริ่มแปลเสียง’ แล้วพูด ไทย อังกฤษ หรือจีน ข้อความสมบูรณ์แต่ละช่วงจะถูกบันทึกที่นี่", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                                items(phrases, key = { it.id }) { phrase -> PhraseCard(phrase, ::speak) }
                            }
                        }
                    }
                    "history" -> {
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Text("ประวัติการประชุมและบทสนทนา", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (meetings.isEmpty()) item { Text("ยังไม่มีบันทึก", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                                items(meetings, key = { it.id }) { meeting ->
                                    ElevatedCard(onClick = { viewing = meeting.id; page = "presentation" }) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (meeting.mode == "meeting") Icons.Default.Groups else Icons.Default.Chat, null)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) { Text(meeting.title, fontWeight = FontWeight.Medium); Text(if (meeting.summary.isBlank()) "แตะเพื่อดูรายละเอียด/สรุป" else "มีสรุปแล้ว", fontSize = 12.sp, color = Color.Gray) }
                                            IconButton(
                                                enabled = deletingId == 0L,
                                                onClick = {
                                                    if (live.running && live.meetingId == meeting.id) {
                                                        uiError = "กรุณาหยุดบันทึกก่อนลบรายการที่กำลังใช้งาน"
                                                    } else {
                                                        pendingDeletion = meeting
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "ลบ ${meeting.title}", tint = MaterialTheme.colorScheme.error)
                                            }
                                            Icon(Icons.Default.ChevronRight, null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "presentation" -> {
                        val keepAwake = LocalView.current
                        DisposableEffect(keepAwake) { keepAwake.keepScreenOn = true; onDispose { keepAwake.keepScreenOn = false } }
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Text(current?.title ?: "โหมดนำเสนอ", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102047)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                    Text("คำบรรยายสด · ผู้พูด ${last?.speaker ?: live.speaker}", color = Color(0xFFBCCAF3), fontSize = 13.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(last?.source ?: live.partial.ifBlank { "รอการพูด…" }, color = Color.White, fontSize = 23.sp)
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Gray)
                                    Text(last?.thai?.ifBlank { last?.source.orEmpty() } ?: "คำแปลภาษาไทย", color = Color(0xFFE3F1FF), fontSize = 23.sp)
                                    if (last?.chinese?.isNotBlank() == true) Text(last.chinese, color = Color(0xFFF4E4B7), fontSize = 19.sp)
                                    if (last?.english?.isNotBlank() == true) Text(last.english, color = Color.White, fontSize = 19.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { showPip() }) { Icon(Icons.Default.PictureInPictureAlt, null); Text("PiP") }
                                OutlinedButton(onClick = { setOverlay(!app.settings.overlayEnabled) }) { Text(if (app.settings.overlayEnabled) "ซ่อนคำบรรยายลอย" else "เปิดคำบรรยายลอย") }
                            }
                            if (current != null) {
                                Spacer(Modifier.height(6.dp))
                                Text("สรุปการประชุม", fontWeight = FontWeight.Bold)
                                Text(current.summary.ifBlank { "ยังไม่ได้สร้างสรุป" }, modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { app.store.setSummary(current.id, Notes.offline(phrases)) }) { Text("สรุปเบื้องต้น") }
                                    OutlinedButton(onClick = {
                                        if (!app.settings.onlineAi || app.settings.apiKey().isBlank()) { uiError = "เปิด AI ออนไลน์และบันทึก API key ในตั้งค่าก่อน" }
                                        else scope.launch {
                                            uiError = "กำลังสรุปด้วย AI ออนไลน์…"
                                            runCatching { OnlineTextAi(app.settings).summarize(phrases.joinToString("\n") { "ผู้พูด ${it.speaker}: ${it.source}" }) }
                                                .onSuccess { app.store.setSummary(current.id, it); uiError = "สรุปเสร็จแล้ว" }
                                                .onFailure { uiError = "สรุป AI ไม่สำเร็จ: ${it.message}" }
                                        }
                                    }) { Text("สรุป AI") }
                                }
                                OutlinedButton(onClick = {
                                    exportBody = Notes.export(current.title, phrases, current.summary)
                                    export.launch("TriLingual-${current.id}.md")
                                }) { Icon(Icons.Default.FileDownload, null); Text("ส่งออก Markdown") }
                            }
                        }
                    }
                    "settings" -> SettingsScreen(onOverlay = ::setOverlay, error = { uiError = it })
                }
            }
        }
    }

    @Composable private fun PhraseCard(phrase: Phrase, onSpeak: (String, String) -> Unit) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ผู้พูด ${phrase.speaker} · ${Language.label(phrase.sourceLanguage)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(phrase.source, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                listOf(Triple("🇹🇭", "th", phrase.thai), Triple("🇬🇧", "en", phrase.english), Triple("🇨🇳", "zh", phrase.chinese)).forEach { (flag, lang, text) ->
                    if (text.isNotBlank() && lang != phrase.sourceLanguage) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$flag  $text", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(onClick = { onSpeak(text, lang) }, modifier = Modifier.size(35.dp)) { Icon(Icons.Default.VolumeUp, contentDescription = "อ่านคำแปล $lang", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
                if (phrase.thai.isBlank() && phrase.english.isBlank() && phrase.chinese.isBlank()) Text("กำลังแปล…", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    @Composable private fun SettingsScreen(onOverlay: (Boolean) -> Unit, error: (String) -> Unit) {
        val prefs = app.settings
        var device by remember { mutableStateOf(prefs.onDevice) }
        var online by remember { mutableStateOf(prefs.onlineAi) }
        var endpoint by remember { mutableStateOf(prefs.onlineUrl) }
        var model by remember { mutableStateOf(prefs.onlineModel) }
        var key by remember { mutableStateOf("") }
        var glossary by remember { mutableStateOf(prefs.glossary) }
        var preparing by remember { mutableStateOf(false) }
        var prepareStatus by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val onDeviceAvailable = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("เสียงและความเป็นส่วนตัว", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ใช้รู้จำเสียงออฟไลน์ของระบบ", fontWeight = FontWeight.Medium)
                    Text(if (onDeviceAvailable) "พบระบบรู้จำเสียงในเครื่อง (ภาษาแต่ละภาษาต้องรองรับด้วย)" else "เครื่องนี้ไม่รองรับ Android on-device recognizer", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = device, enabled = onDeviceAvailable, onCheckedChange = { device = it; prefs.onDevice = it })
            }
            Text("โหมดระบบปกติอาจส่งเสียงให้ผู้ให้บริการรู้จำเสียงของโทรศัพท์; โหมดออฟไลน์จะไม่สลับไปออนไลน์โดยอัตโนมัติ", fontSize = 12.sp)
            OutlinedButton(enabled = !preparing, onClick = {
                preparing = true
                scope.launch {
                    val translator = DeviceTranslator()
                    try { translator.prepareAll { prepareStatus = it } }
                    catch (e: Exception) { prepareStatus = "ดาวน์โหลดไม่สำเร็จ: ${e.message}" }
                    finally { translator.close(); preparing = false }
                }
            }) { Text(if (preparing) "กำลังดาวน์โหลด…" else "ดาวน์โหลดโมเดลแปลภาษาออฟไลน์") }
            if (prepareStatus.isNotBlank()) Text(prepareStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text("AI ออนไลน์ (ข้อความเท่านั้น)", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("อนุญาตส่งบทสนทนาเป็นข้อความไปยัง API ที่กำหนด", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Switch(checked = online, onCheckedChange = { online = it; prefs.onlineAi = it })
            }
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("HTTPS Chat Completions endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key (เก็บเข้ารหัส)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick = {
                if (!endpoint.startsWith("https://", ignoreCase = true)) error("URL ต้องขึ้นต้นด้วย https://")
                else { prefs.onlineUrl = endpoint; prefs.onlineModel = model; if (key.isNotBlank()) { prefs.saveApiKey(key); key = "" }; error("บันทึกการตั้งค่าแล้ว") }
            }) { Text("บันทึกการตั้งค่าออนไลน์") }
            OutlinedButton(onClick = { prefs.saveApiKey(""); key = ""; error("ล้าง API key แล้ว") }) { Text("ลบ API key") }
            Text("คีย์จะไม่แนบมาในโปรเจ็กต์และไม่ส่งไปที่ GitHub การเรียก API อาจมีค่าบริการ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            HorizontalDivider()
            Text("คำศัพท์เฉพาะและบริบท", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = glossary, onValueChange = { glossary = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ชื่อบุคคล สถานที่ และคำศัพท์ (คั่นด้วย , หรือขึ้นบรรทัดใหม่)") }, minLines = 3)
            OutlinedButton(onClick = { prefs.glossary = glossary; error("บันทึกคำศัพท์แล้ว") }) { Text("บันทึกคำศัพท์") }
            HorizontalDivider()
            Text("คำบรรยายลอย", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onOverlay(!prefs.overlayEnabled) }) { Text(if (prefs.overlayEnabled) "ปิด Overlay" else "ขอสิทธิ์และเปิด Overlay") }
            Text("ต้องอนุญาต ‘แสดงทับแอปอื่น’ ในการตั้งค่า Android; PiP ใช้ได้จากหน้า ‘นำเสนอ’", fontSize = 12.sp, color = Color.Gray)
            Text("เวอร์ชัน 0.1.2 · ปรับปรุงจีน → ไทยและ Hybrid speech fallback", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
