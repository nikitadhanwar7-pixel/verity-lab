package com.veritylab.app

import android.os.Build
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF0B0E13)
private val Panel = Color(0xFF151B25)
private val Blue = Color(0xFFB9D3FF)
private val Muted = Color(0xFF9BA8BC)

private fun restoreRecords(raw: String?): List<TestRecord> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            val attemptsJson = item.optJSONArray("attempts") ?: return@mapNotNull null
            val attempts = (0 until attemptsJson.length()).map { j ->
                val a = attemptsJson.getJSONObject(j)
                val outcome = runCatching { Outcome.valueOf(a.optString("outcome")) }.getOrDefault(Outcome.INCONCLUSIVE)
                Attempt(outcome, a.optJSONObject("evidence") ?: JSONObject(), a.optString("note"), a.optLong("at"))
            }
            if (attempts.isEmpty()) null else TestRecord(item.optString("code"), attempts, item.optInt("selected_attempt", 1).minus(1).coerceIn(attempts.indices), item.optString("summary"))
        }
    } catch (_: Exception) { emptyList() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 14, 19)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 19)
        setContent { VerityApp() }
    }

    @Composable private fun VerityApp() {
        val prefs = remember { getSharedPreferences("verity-checkpoint", MODE_PRIVATE) }
        val records = remember { mutableStateListOf<TestRecord>().apply { addAll(restoreRecords(prefs.getString("evidence_records", null))) } }
        val deviceLabel = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }
        var serial by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Local test session. No report is issued by this build.") }
        var touchMode by remember { mutableStateOf(false) }
        var touchFirst by remember { mutableStateOf<Attempt?>(null) }
        val points = remember { mutableStateListOf<Offset>() }
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            val attempt = if (bitmap != null) Attempt(Outcome.OBSERVED, JSONObject().put("captured_width", bitmap.width).put("captured_height", bitmap.height), "A camera app returned a preview image. This does not test every camera, focus accuracy or image quality.") else Attempt(Outcome.NOT_TESTED, JSONObject(), "No image was returned; cancellation or camera access may be responsible.")
            addWithRetestPolicy(records, "camera-preview", attempt, statusSetter = { status = it }, prefs = prefs)
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) status = "Permission granted. Run the relevant test again." else status = "Permission denied. The test remains unavailable; this is not a device defect."
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Ink, surface = Panel)) {
            Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                if (touchMode) {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TOUCH RESPONSE", style = MaterialTheme.typography.headlineSmall, color = Blue)
                        Text("Trace the entire panel, edges and corners. Dots show where Android delivered touch events.", color = Muted)
                        Box(Modifier.fillMaxWidth().weight(1f).border(1.dp, Muted, RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).pointerInput(Unit) {
                            detectDragGestures(onDragStart = { points.add(Offset(it.x / size.width.coerceAtLeast(1) * 1000f, it.y / size.height.coerceAtLeast(1) * 1000f)) }) { change, _ -> points.add(Offset(change.position.x / size.width.coerceAtLeast(1) * 1000f, change.position.y / size.height.coerceAtLeast(1) * 1000f)); change.consume() }
                        }.pointerInput(Unit) { detectTapGestures { points.add(Offset(it.x / size.width.coerceAtLeast(1) * 1000f, it.y / size.height.coerceAtLeast(1) * 1000f)) } }) {
                            Canvas(Modifier.fillMaxSize()) {
                                for (i in 1..4) {
                                    drawLine(Color.DarkGray, Offset(size.width * i / 5f, 0f), Offset(size.width * i / 5f, size.height), 1f)
                                    drawLine(Color.DarkGray, Offset(0f, size.height * i / 5f), Offset(size.width, size.height * i / 5f), 1f)
                                }
                                points.forEach { drawCircle(Blue, radius = 5f, center = Offset(it.x / 1000f * size.width, it.y / 1000f * size.height)) }
                            }
                        }
                        Text("Recorded touch samples: ${points.size}", color = Color.White)
                        Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { points.clear() }) { Text("Clear") }
                            Button(onClick = {
                                val cells = points.map { p -> "${(p.x / 100).toInt().coerceIn(0, 9)}:${(p.y / 100).toInt().coerceIn(0, 9)}" }.distinct()
                                val a = Attempt(if (cells.size >= 20) Outcome.OBSERVED else Outcome.ATTENTION,
                                    JSONObject().put("touch_sample_count", points.size).put("unique_approx_cells", cells.size).put("grid_resolution", "10x10 approximate"),
                                    "This records touch events, not every physical digitizer point. The coverage threshold is experimental and must be validated on real devices; repeat and inspect any dead zones.")
                                if (touchFirst == null && a.outcome == Outcome.ATTENTION) {
                                    touchFirst = a
                                    points.clear()
                                    status = "The first touch attempt showed limited coverage. Repeat the full grid test now; both attempts will be retained."
                                } else {
                                    val attempts = if (touchFirst != null) listOf(touchFirst!!, a) else listOf(a)
                                    val selected = if (attempts.size == 2 && attempts[1].evidence.optInt("unique_approx_cells") > attempts[0].evidence.optInt("unique_approx_cells")) 1 else 0
                                    val summary = if (attempts.size == 2) "Touch coverage was repeated. Both attempts are retained; the better coverage attempt is shown, but repeatable or inconsistent dead zones still require evaluation." else "The touch check recorded coverage evidence. This is not a calibrated digitizer test."
                                    records.removeAll { it.code == "touch-coverage" }
                                    records.add(0, TestRecord("touch-coverage", attempts, selected, summary))
                                    prefs.edit().putString("evidence_records", JSONArray().apply { records.forEach { put(it.json()) } }.toString()).apply()
                                    touchFirst = null
                                    touchMode = false
                                    status = if (attempts.size == 2) "Touch retest saved. Both attempts remain in the evidence record." else "Touch evidence saved locally; no server has verified or certified this result."
                                }
                            }) { Text("Save touch evidence") }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("VERITY", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("LAB  /  ANDROID DIAGNOSTICS", color = Blue, style = MaterialTheme.typography.labelSmall) }
                            Text("BUILD 0.1", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Know the phone.\nBefore you buy it.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text("Evidence-first testing. A test result is not a blanket guarantee of device health.", color = Muted)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(value = deviceLabel, onValueChange = { deviceLabel = it }, label = { Text("Phone model (as entered)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial number (not saved or uploaded in this build)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Text("Do not enter IMEI or serial until the privacy policy and secure backend are live.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { prefs.edit().putString("device_label", deviceLabel).apply(); status = "Model checkpoint saved locally. Serial is not persisted or uploaded by this build." }, modifier = Modifier.fillMaxWidth()) { Text("Save local checkpoint") }
                            }
                        }
                        Text("TEST MODULES", color = Blue, style = MaterialTheme.typography.titleMedium)
                        TestCard("Touch response", "Trace screen coverage; experimental threshold only") { touchMode = true; touchFirst = null; points.clear() }
                        TestCard("Camera capture", "Opens the Android camera app and checks whether a preview is returned") { cameraLauncher.launch(null) }
                        TestCard("Microphone permission", "Checks permission availability only; recording/loopback test is not implemented yet") { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        TestCard("Battery readings", "System-reported level and temperature; not true battery capacity") { addWithRetestPolicy(records, "battery-readings", DiagnosticEngine.battery(this@MainActivity), { status = it }, prefs) }
                        TestCard("Sensor inventory", "Lists sensors exposed by Android; not calibration") { addWithRetestPolicy(records, "sensor-inventory", DiagnosticEngine.sensors(this@MainActivity), { status = it }, prefs) }
                        TestCard("Vibration", "Requests a vibration pattern; user confirmation is still needed") { addWithRetestPolicy(records, "vibration", DiagnosticEngine.vibration(this@MainActivity), { status = it }, prefs) }
                        TestCard("Performance workload", "Runs a small local workload; not a universal hardware score") { addWithRetestPolicy(records, "performance", DiagnosticEngine.performance(), { status = it }, prefs) }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SESSION STATUS", color = Blue, style = MaterialTheme.typography.titleMedium)
                                Text(status, color = Color.White)
                                Text("Evidence captured: ${records.size} modules", color = Muted)
                                Button(onClick = {
                                    val out = JSONArray().apply { records.forEach { put(it.json()) } }
                                    val file = java.io.File(cacheDir, "verity-evidence.json")
                                    file.writeText(JSONObject().put("schema_version", 1).put("device_label", deviceLabel).put("created_at", System.currentTimeMillis()).put("records", out).toString(2))
                                    status = "Evidence export prepared at ${file.name}. It is not a certificate and has not been uploaded or signed."
                                }, modifier = Modifier.fillMaxWidth()) { Text("Prepare evidence export") }
                            }
                        }
                        records.forEach { r ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(r.code.replace('-', ' ').uppercase(), color = Blue)
                                    Text(r.summary, color = Color.White)
                                    Text("Attempts: ${r.attempts.size} · selected evidence attempt: ${r.selected + 1}", color = Muted)
                                    Text(r.chosen.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text(r.chosen.evidence.toString(), color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text("NOT A CERTIFICATE · No payment, backend, public verification or report signing is connected.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    @Composable private fun TestCard(title: String, detail: String, action: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = action) { Text("Run") }
            }
        }
    }

    private fun addWithRetestPolicy(records: MutableList<TestRecord>, code: String, first: Attempt, statusSetter: (String) -> Unit, prefs: android.content.SharedPreferences) {
        // For questionable outcomes, the app requests a second attempt. This first build can safely auto-repeat
        // deterministic local metrics; subjective checks require a second user confirmation in a later iteration.
        val shouldRetest = first.outcome == Outcome.ATTENTION || first.outcome == Outcome.INCONCLUSIVE
        val second = if (shouldRetest) when (code) {
            "performance" -> DiagnosticEngine.performance()
            "battery-readings" -> DiagnosticEngine.battery(this)
            "sensor-inventory" -> DiagnosticEngine.sensors(this)
            "vibration" -> DiagnosticEngine.vibration(this)
            else -> null
        } else null
        val attempts = if (second != null) listOf(first, second) else listOf(first)
        val selected = if (code == "performance" && attempts.size == 2) DiagnosticEngine.selectBestMetric(attempts[0], attempts[1], "duration_ms", preferHigher = false) else 0
        val summary = when {
            attempts.size == 2 -> "This result was repeated. Both attempts are retained; a single best score is not proof of device health."
            first.outcome == Outcome.OBSERVED -> "The check completed and recorded evidence."
            first.outcome == Outcome.ATTENTION -> "This check needs confirmation or further testing."
            first.outcome == Outcome.NOT_TESTED -> "This check could not collect enough evidence."
            else -> "The result is inconclusive and should not be treated as a defect or a clean bill of health."
        }
        val record = TestRecord(code, attempts, selected, summary)
        records.removeAll { it.code == code }
        records.add(0, record)
        val all = JSONArray().apply { records.forEach { put(it.json()) } }
        prefs.edit().putString("evidence_records", all.toString()).apply()
        statusSetter(if (shouldRetest && attempts.size == 1) "Questionable result recorded. Repeat the test manually; this test needs a human confirmation or targeted retest." else "Evidence saved locally. No server has verified or certified this result.")
    }
}
