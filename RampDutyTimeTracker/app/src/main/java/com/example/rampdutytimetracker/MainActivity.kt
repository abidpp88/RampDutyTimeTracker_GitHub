package com.example.rampdutytimetracker
 
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
 
data class Stamp(
    val name: String,
    val time: String? = null
)
 
data class TaskDisplayRow(
    val left: String,
    val right: String? = null,
    val countOnly: Boolean = false
)
 
data class SavedFlight(
    val flightNo: String,
    val departureFlightNo: String = "",
    val sta: String = "",
    val std: String = "",
    val registration: String,
    val aircraft: String,
    val stand: String,
    val date: String,
    val notes: String,
    val taskType: String,
    val timings: Map<String, String>,
    val bagCounts: Map<String, String>,
    val storageIndex: Int
)
 
private enum class AppPage {
    HOME,
    FLIGHT_SETUP,
    ACTIVE_FLIGHT,
    HISTORY,
    HISTORY_DETAILS,
    EDIT_SAVED_FLIGHT
}
 
private val Navy = Color(0xFF0B2A78)
private val RoyalBlue = Color(0xFF123FAF)
private val PaleBlue = Color(0xFFEAF2FF)
private val SoftBlue = Color(0xFFF6F9FF)
 
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RampDutyApp()
        }
    }
}
 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RampDutyApp() {
    val context = LocalContext.current
 
    val arrivalNames = listOf(
        "Task Start",
        "Task End",
        "On Block",
        "Chocks On",
        "Step Start",
        "GPU Start",
        "A/C Start",
        "Hold Open",
        "BY First Bag",
        "BY Last Bag",
        "BT First Bag",
        "BT Last Bag"
    )
 
    val departureNames = listOf(
        "Task Start",
        "Task End",
        "LIR/NOTOC Received",
        "Cargo Received",
        "First Bag Received",
        "D-15 Baggage Received",
        "Last Bag Received",
        "Last Bag Loaded",
        "Hold Closed",
        "Cabin Door Closed",
        "GPU End",
        "A/C End",
        "Off Block"
    )
 
    val turnaroundNames = listOf(
        "Task Start",
        "Task End",
        "On Block",
        "Chocks On",
        "Step Start",
        "Step End",
        "GPU Start",
        "GPU End",
        "A/C Start",
        "A/C End",
        "Hold Open",
        "BY First Bag",
        "BY Last Bag",
        "BT First Bag",
        "BT Last Bag",
        "LIR/NOTOC Received",
        "Cargo Received",
        "First Bag Received",
        "D-15 Baggage Received",
        "Last Bag Received",
        "Last Bag Loaded",
        "Hold Closed",
        "Cabin Door Closed",
        "Off Block"
    )
 
    val allKnownNames = (
        arrivalNames +
            departureNames +
            turnaroundNames +
            listOf(
                "Task Started",
                "GPU Disconnected",
                "A/C Disconnected",
                "Step Disconnected",
                "BY First Baggage",
                "BY Last Baggage",
                "BT First Baggage",
                "BT Last Baggage",
                "Last Baggage Received"
            )
        ).distinct()
 
    var page by remember { mutableStateOf(AppPage.HOME) }
    var showSplash by remember { mutableStateOf(true) }
    var selectedTaskType by remember { mutableStateOf("") }
 
    var flightNo by remember { mutableStateOf("") }
    var departureFlightNo by remember { mutableStateOf("") }
    var sta by remember { mutableStateOf("") }
    var std by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var aircraft by remember { mutableStateOf("") }
    var stand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
 
    val stamps = remember { mutableStateListOf<Stamp>() }
    val bagCounts = remember { mutableStateMapOf<String, String>() }
 
    var selectedFlight by remember { mutableStateOf<SavedFlight?>(null) }
    var historyRefreshKey by remember { mutableIntStateOf(0) }
    var historySearch by remember { mutableStateOf("") }
 
    var showMissingWarning by remember { mutableStateOf(false) }
    var missingTimings by remember { mutableStateOf<List<String>>(emptyList()) }
    var flightToDelete by remember { mutableStateOf<SavedFlight?>(null) }
 
    LaunchedEffect(showSplash) {
        if (showSplash) {
            delay(1800)
            showSplash = false
        }
    }
 
    if (showSplash) {
        RampSplashScreen()
        return
    }
 
    fun displayRowsForTask(taskType: String): List<TaskDisplayRow> {
        return when (taskType.uppercase()) {
            "ARRIVAL" -> listOf(
                TaskDisplayRow("Task Start", "Task End"),
                TaskDisplayRow("On Block"),
                TaskDisplayRow("Chocks On"),
                TaskDisplayRow("Step Start"),
                TaskDisplayRow("GPU Start", "A/C Start"),
                TaskDisplayRow("Hold Open"),
                TaskDisplayRow("BY First Bag", "BY Last Bag"),
                TaskDisplayRow("BT First Bag", "BT Last Bag")
            )
 
            "DEPARTURE" -> listOf(
                TaskDisplayRow("Task Start", "Task End"),
                TaskDisplayRow("LIR/NOTOC Received"),
                TaskDisplayRow("Cargo Received", "First Bag Received"),
                TaskDisplayRow("D-15 Baggage Received", countOnly = true),
                TaskDisplayRow("Last Bag Received", "Last Bag Loaded"),
                TaskDisplayRow("Hold Closed", "Cabin Door Closed"),
                TaskDisplayRow("GPU End", "A/C End"),
                TaskDisplayRow("Off Block")
            )
 
            else -> listOf(
                TaskDisplayRow("Task Start", "Task End"),
                TaskDisplayRow("On Block"),
                TaskDisplayRow("Chocks On"),
                TaskDisplayRow("Step Start", "Step End"),
                TaskDisplayRow("GPU Start", "GPU End"),
                TaskDisplayRow("A/C Start", "A/C End"),
                TaskDisplayRow("Hold Open"),
                TaskDisplayRow("BY First Bag", "BY Last Bag"),
                TaskDisplayRow("BT First Bag", "BT Last Bag"),
                TaskDisplayRow("LIR/NOTOC Received"),
                TaskDisplayRow("Cargo Received", "First Bag Received"),
                TaskDisplayRow("D-15 Baggage Received", countOnly = true),
                TaskDisplayRow("Last Bag Received", "Last Bag Loaded"),
                TaskDisplayRow("Hold Closed", "Cabin Door Closed"),
                TaskDisplayRow("Off Block")
            )
        }
    }
 
    fun namesForTask(taskType: String): List<String> {
        return when (taskType.uppercase()) {
            "ARRIVAL" -> arrivalNames
            "DEPARTURE" -> departureNames
            else -> turnaroundNames
        }
    }
 
    fun resetForm() {
        flightNo = ""
        departureFlightNo = ""
        sta = ""
        std = ""
        registration = ""
        aircraft = ""
        stand = ""
        notes = ""
        stamps.clear()
        bagCounts.clear()
    }
 
    fun startNewTask(taskType: String) {
        selectedTaskType = taskType
        selectedFlight = null
        resetForm()
        page = AppPage.FLIGHT_SETUP
    }
 
    fun initializeStampsForTask(taskType: String) {
        stamps.clear()
        stamps.addAll(namesForTask(taskType).map { Stamp(it) })
    }
 
    fun currentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
 
    fun displayTime(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        val parts = value.split(":")
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else value
    }
 
    fun record(index: Int) {
        if (stamps[index].time == null) {
            stamps[index] = stamps[index].copy(time = currentTime())
        }
    }
 
    fun resetStamp(index: Int) {
        stamps[index] = stamps[index].copy(time = null)
    }
 
    fun editStamp(index: Int) {
        val calendar = Calendar.getInstance()
        val existing = stamps[index].time
        if (!existing.isNullOrBlank()) {
            try {
                val parts = existing.split(":")
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
            } catch (_: Exception) {
            }
        }
 
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val newTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                stamps[index] = stamps[index].copy(time = newTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
 
    fun secondsBetween(first: String?, last: String?): Long? {
        if (first.isNullOrBlank() || last.isNullOrBlank()) return null
        return try {
            fun toMinutes(value: String): Long {
                val parts = value.split(":")
                parts[0].toLong() * 60L + parts[1].toLong()
            }
            val startMinutes = toMinutes(first)
            var endMinutes = toMinutes(last)
            if (endMinutes < startMinutes) endMinutes += 24L * 60L
            (endMinutes - startMinutes) * 60L
        } catch (_: Exception) {
            null
        }
    }
 
    fun formatDuration(seconds: Long?): String {
        if (seconds == null) return "—"
        val totalMinutes = seconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
    }
 
    fun getTime(map: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = map[key]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
 
    fun loadHistory(): List<SavedFlight> {
        val prefs = context.getSharedPreferences("ramp_history", 0)
        val data = prefs.getString("flights", "[]") ?: "[]"
        val array = JSONArray(data)
        val list = mutableListOf<SavedFlight>()
 
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val timingsObject = item.optJSONObject("timings") ?: JSONObject()
            val timingsMap = mutableMapOf<String, String>()
            val countsObject = item.optJSONObject("bagCounts") ?: JSONObject()
            val countsMap = mutableMapOf<String, String>()
 
            val keys = timingsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                timingsMap[key] = timingsObject.optString(key, "")
            }
 
            if (timingsMap["Task Start"].isNullOrBlank() && !timingsMap["Task Started"].isNullOrBlank()) {
                timingsMap["Task Start"] = timingsMap["Task Started"] ?: ""
            }
            allKnownNames.forEach { name ->
                if (!timingsMap.containsKey(name)) timingsMap[name] = ""
            }
 
            val countKeys = countsObject.keys()
            while (countKeys.hasNext()) {
                val key = countKeys.next()
                countsMap[key] = countsObject.optString(key, "")
            }
 
            var taskType = item.optString("taskType", "")
            if (taskType.isBlank()) taskType = "TURNAROUND"
 
            list.add(
                SavedFlight(
                    flightNo = item.optString("flightNo"),
                    departureFlightNo = item.optString("departureFlightNo", ""),
                    sta = item.optString("sta", ""),
                    std = item.optString("std", ""),
                    registration = item.optString("registration"),
                    aircraft = item.optString("aircraft"),
                    stand = item.optString("stand"),
                    date = item.optString("date"),
                    notes = item.optString("notes"),
                    taskType = taskType,
                    timings = timingsMap,
                    bagCounts = countsMap,
                    storageIndex = i
                )
            )
        }
 
        return list.reversed()
    }
 
    fun writeFlightToJson(
        obj: JSONObject,
        taskType: String,
        flightNumber: String,
        departureFlightNumber: String,
        staTime: String,
        stdTime: String,
        reg: String,
        aircraftType: String,
        standNumber: String,
        dateText: String,
        notesText: String,
        stampList: List<Stamp>,
        counts: Map<String, String>
    ) {
        obj.put("flightNo", flightNumber)
        obj.put("departureFlightNo", departureFlightNumber)
        obj.put("sta", staTime)
        obj.put("std", stdTime)
        obj.put("registration", reg)
        obj.put("aircraft", aircraftType)
        obj.put("stand", standNumber)
        obj.put("date", dateText)
        obj.put("notes", notesText)
        obj.put("taskType", taskType)
 
        val timings = JSONObject()
        stampList.forEach {
            timings.put(it.name, it.time ?: "")
        }
        obj.put("timings", timings)
 
        val countJson = JSONObject()
        counts.forEach { (name, count) ->
            countJson.put(name, count)
        }
        obj.put("bagCounts", countJson)
    }
 
    fun saveNewFlight() {
        val prefs = context.getSharedPreferences("ramp_history", 0)
        val oldData = prefs.getString("flights", "[]") ?: "[]"
        val array = JSONArray(oldData)
        val obj = JSONObject()
 
        val dateText = SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale.getDefault()
        ).format(Date())
 
        writeFlightToJson(
            obj = obj,
            taskType = selectedTaskType,
            flightNumber = flightNo,
            departureFlightNumber = departureFlightNo,
            staTime = sta,
            stdTime = std,
            reg = registration,
            aircraftType = aircraft,
            standNumber = stand,
            dateText = dateText,
            notesText = notes,
            stampList = stamps,
            counts = bagCounts
        )
 
        array.put(obj)
        prefs.edit().putString("flights", array.toString()).apply()
 
        Toast.makeText(
            context,
            "Flight Saved Successfully",
            Toast.LENGTH_SHORT
        ).show()
 
        resetForm()
        selectedTaskType = ""
        page = AppPage.HOME
        historyRefreshKey++
    }
 
    fun updateSavedFlight() {
        val original = selectedFlight ?: return
        val prefs = context.getSharedPreferences("ramp_history", 0)
        val oldData = prefs.getString("flights", "[]") ?: "[]"
        val array = JSONArray(oldData)
 
        if (original.storageIndex !in 0 until array.length()) {
            Toast.makeText(
                context,
                "Unable to update this flight",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
 
        val updated = JSONObject()
 
        writeFlightToJson(
            obj = updated,
            taskType = selectedTaskType,
            flightNumber = flightNo,
            departureFlightNumber = departureFlightNo,
            staTime = sta,
            stdTime = std,
            reg = registration,
            aircraftType = aircraft,
            standNumber = stand,
            dateText = original.date,
            notesText = notes,
            stampList = stamps,
            counts = bagCounts
        )
 
        array.put(original.storageIndex, updated)
        prefs.edit().putString("flights", array.toString()).apply()
 
        Toast.makeText(
            context,
            "Flight Updated Successfully",
            Toast.LENGTH_SHORT
        ).show()
 
        selectedFlight = SavedFlight(
            flightNo = flightNo,
            departureFlightNo = departureFlightNo,
            sta = sta,
            std = std,
            registration = registration,
            aircraft = aircraft,
            stand = stand,
            date = original.date,
            notes = notes,
            taskType = selectedTaskType,
            timings = stamps.associate { it.name to (it.time ?: "") },
            bagCounts = bagCounts.toMap(),
            storageIndex = original.storageIndex
        )
 
        historyRefreshKey++
        page = AppPage.HISTORY_DETAILS
    }
 
    fun deleteFlight(flight: SavedFlight) {
        val prefs = context.getSharedPreferences("ramp_history", 0)
        val data = prefs.getString("flights", "[]") ?: "[]"
        val array = JSONArray(data)
 
        if (flight.storageIndex in 0 until array.length()) {
            array.remove(flight.storageIndex)
            prefs.edit().putString("flights", array.toString()).apply()
 
            Toast.makeText(
                context,
                "Flight deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
 
        flightToDelete = null
        selectedFlight = null
        historyRefreshKey++
        page = AppPage.HISTORY
    }
 
    fun buildFlightReport(flight: SavedFlight): String {
        val visibleNames = namesForTask(flight.taskType)
 
        val chocksOn = getTime(flight.timings, "Chocks On")
        val onBlock = getTime(flight.timings, "On Block")
        val offBlock = getTime(flight.timings, "Off Block")
 
        val byFirst = getTime(
            flight.timings,
            "BY First Bag",
            "BY First Baggage"
        )
        val byLast = getTime(
            flight.timings,
            "BY Last Bag",
            "BY Last Baggage"
        )
        val btFirst = getTime(
            flight.timings,
            "BT First Bag",
            "BT First Baggage"
        )
        val btLast = getTime(
            flight.timings,
            "BT Last Bag",
            "BT Last Baggage"
        )
 
        return buildString {
            appendLine("Ramp Task Time Tracker")
            appendLine()
            appendLine("Task: ${flight.taskType}")
            if (flight.taskType == "TURNAROUND") {
                appendLine("Arrival Flight: ${flight.flightNo}")
                appendLine("STA: ${flight.sta.ifBlank { "—" }}")
                appendLine("Departure Flight: ${flight.departureFlightNo.ifBlank { "—" }}")
                appendLine("STD: ${flight.std.ifBlank { "—" }}")
            } else {
                appendLine("Flight: ${flight.flightNo}")
                if (flight.taskType == "ARRIVAL") appendLine("STA: ${flight.sta.ifBlank { "—" }}")
                if (flight.taskType == "DEPARTURE") appendLine("STD: ${flight.std.ifBlank { "—" }}")
            }
            appendLine("Registration: ${flight.registration}")
            appendLine("Aircraft: ${flight.aircraft}")
            appendLine("Stand: ${flight.stand}")
            appendLine("Date: ${flight.date}")
            appendLine()
            val reportTaskStart = getTime(flight.timings, "Task Start", "Task Started")
        val reportTaskEnd = getTime(flight.timings, "Task End")
        appendLine("Total Task Duration: ${formatDuration(secondsBetween(reportTaskStart, reportTaskEnd))}")
        appendLine()
        appendLine("Recorded Timings")
 
            visibleNames.forEach { name ->
                val time = displayTime(flight.timings[name])
                val count = flight.bagCounts[name]?.takeIf { it.isNotBlank() }
 
                if (count != null) {
                    appendLine("$name: $time • Count: $count")
                } else {
                    appendLine("$name: $time")
                }
            }
 
            if (flight.taskType == "ARRIVAL" || flight.taskType == "TURNAROUND") {
                appendLine()
                appendLine("Baggage Delivery Performance (from Chocks On)")
                appendLine(
                    "Local (BY) First Bag: ${
                        formatDuration(secondsBetween(chocksOn, byFirst))
                    }"
                )
                appendLine(
                    "Local (BY) Last Bag: ${
                        formatDuration(secondsBetween(chocksOn, byLast))
                    }"
                )
                appendLine(
                    "Transfer (BT) First Bag: ${
                        formatDuration(secondsBetween(chocksOn, btFirst))
                    }"
                )
                appendLine(
                    "Transfer (BT) Last Bag: ${
                        formatDuration(secondsBetween(chocksOn, btLast))
                    }"
                )
            }
 
            if (flight.taskType == "TURNAROUND") {
                appendLine()
                appendLine(
                    "Turnaround: ${
                        formatDuration(secondsBetween(onBlock, offBlock))
                    }"
                )
            }
 
            appendLine()
            appendLine(
                "Notes: ${
                    flight.notes.takeIf { it.isNotBlank() } ?: "No notes"
                }"
            )
        }
    }
 
    fun copyFlightReport(flight: SavedFlight) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
 
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Flight Report",
                buildFlightReport(flight)
            )
        )
 
        Toast.makeText(
            context,
            "Flight details copied",
            Toast.LENGTH_SHORT
        ).show()
    }
 
    fun shareFlightReport(flight: SavedFlight) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Flight Report - ${flight.flightNo}"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                buildFlightReport(flight)
            )
        }
 
        context.startActivity(
            Intent.createChooser(intent, "Share Flight Report")
        )
    }
 
    fun shareFlightAsPdf(flight: SavedFlight) {
        try {
            val pdf = PdfDocument()
 
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)
 
            val navy = AndroidColor.rgb(11, 42, 120)
            val blue = AndroidColor.rgb(18, 63, 175)
            val paleBlue = AndroidColor.rgb(234, 242, 255)
            val lightGrey = AndroidColor.rgb(245, 247, 250)
            val midGrey = AndroidColor.rgb(110, 118, 130)
            val dark = AndroidColor.rgb(25, 31, 43)
            val white = AndroidColor.WHITE
 
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
 
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(220, 230, 255)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
 
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
 
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = midGrey
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
 
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dark
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
 
            val valueBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
 
            val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = midGrey
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
 
            var pageNumber = 1
            var page = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            var canvas = page.canvas
            var y = 0f
 
            fun drawPageHeader() {
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 92f, Paint().apply {
                    color = navy
                })
 
                canvas.drawText(
                    "RAMP TASK TIME TRACKER",
                    margin,
                    38f,
                    titlePaint
                )
 
                canvas.drawText(
                    "${flight.taskType} • FLIGHT REPORT",
                    margin,
                    59f,
                    subtitlePaint
                )
 
                canvas.drawText(
                    "Flight ${flight.flightNo}",
                    pageWidth - margin - 105f,
                    38f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = white
                        textSize = 13f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                )
 
                canvas.drawText(
                    "Generated from saved ramp timings",
                    pageWidth - margin - 155f,
                    59f,
                    smallPaint.apply { color = AndroidColor.rgb(220, 230, 255) }
                )
 
                y = 108f
            }
 
            fun drawFooter() {
                val footerY = pageHeight - 24f
                canvas.drawLine(
                    margin,
                    footerY - 10f,
                    pageWidth - margin,
                    footerY - 10f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.rgb(220, 225, 233)
                        strokeWidth = 1f
                    }
                )
                canvas.drawText(
                    "Ramp Task Time Tracker",
                    margin,
                    footerY,
                    smallPaint.apply { color = midGrey }
                )
                canvas.drawText(
                    "Page $pageNumber",
                    pageWidth - margin - 38f,
                    footerY,
                    smallPaint
                )
            }
 
            fun newPageIfNeeded(requiredHeight: Float) {
                if (y + requiredHeight > pageHeight - 54f) {
                    drawFooter()
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(
                            pageWidth,
                            pageHeight,
                            pageNumber
                        ).create()
                    )
                    canvas = page.canvas
                    drawPageHeader()
                }
            }
 
            fun sectionTitle(title: String) {
                newPageIfNeeded(22f)
                canvas.drawRoundRect(
                    RectF(margin, y, pageWidth - margin, y + 21f),
                    8f,
                    8f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paleBlue }
                )
                canvas.drawText(title, margin + 12f, y + 15f, sectionPaint)
                y += 28f
            }
 
            fun infoRow(label1: String, value1: String, label2: String, value2: String) {
                newPageIfNeeded(42f)
                val half = contentWidth / 2f
 
                canvas.drawText(label1.uppercase(), margin + 10f, y + 11f, labelPaint)
                canvas.drawText(value1, margin + 10f, y + 25f, valueBoldPaint)
 
                canvas.drawText(label2.uppercase(), margin + half + 10f, y + 11f, labelPaint)
                canvas.drawText(value2, margin + half + 10f, y + 25f, valueBoldPaint)
 
                canvas.drawLine(
                    margin,
                    y + 33f,
                    pageWidth - margin,
                    y + 33f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.rgb(230, 233, 239)
                        strokeWidth = 1f
                    }
                )
 
                y += 38f
            }
 
            fun timingRow(name: String, time: String, count: String?) {
                newPageIfNeeded(22f)
 
                if (((y / 28f).toInt() % 2) == 0) {
                    canvas.drawRect(
                        margin,
                        y - 4f,
                        pageWidth - margin,
                        y + 16f,
                        Paint().apply { color = lightGrey }
                    )
                }
 
                canvas.drawText(name, margin + 10f, y + 10f, valuePaint)
 
                val display = if (count.isNullOrBlank()) {
                    time
                } else {
                    "$time   •   Count: $count"
                }
 
                canvas.drawText(
                    display,
                    pageWidth - margin - 150f,
                    y + 10f,
                    valueBoldPaint
                )
 
                y += 20f
            }
 
            fun performanceRow(label: String, value: String) {
                newPageIfNeeded(25f)
                canvas.drawRoundRect(
                    RectF(margin, y, pageWidth - margin, y + 21f),
                    7f,
                    7f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.rgb(248, 250, 255)
                    }
                )
                canvas.drawText(label, margin + 10f, y + 14f, valuePaint)
                canvas.drawText(
                    value,
                    pageWidth - margin - 115f,
                    y + 14f,
                    valueBoldPaint
                )
                y += 25f
            }
 
            drawPageHeader()
 
            sectionTitle("FLIGHT INFORMATION")
            if (flight.taskType == "TURNAROUND") {
                infoRow("Arrival Flight", flight.flightNo, "STA", flight.sta.ifBlank { "—" })
                infoRow("Departure Flight", flight.departureFlightNo.ifBlank { "—" }, "STD", flight.std.ifBlank { "—" })
            } else if (flight.taskType == "ARRIVAL") {
                infoRow("Flight", flight.flightNo, "STA", flight.sta.ifBlank { "—" })
            } else {
                infoRow("Flight", flight.flightNo, "STD", flight.std.ifBlank { "—" })
            }
            infoRow("Registration", flight.registration, "Aircraft", flight.aircraft)
            infoRow("Stand", flight.stand, "Date", flight.date)
            val taskStartPdf = getTime(flight.timings, "Task Start", "Task Started")
            val taskEndPdf = getTime(flight.timings, "Task End")
            infoRow("Task Start", displayTime(taskStartPdf), "Task End", displayTime(taskEndPdf))
            infoRow("Total Task Duration", formatDuration(secondsBetween(taskStartPdf, taskEndPdf)), "", "")
 
            sectionTitle("RECORDED TIMINGS")
            val visibleNames = namesForTask(flight.taskType)
 
            visibleNames.forEach { name ->
                val time = displayTime(flight.timings[name])
                val count = flight.bagCounts[name]?.takeIf { it.isNotBlank() }
                timingRow(name, time, count)
            }
 
            if (flight.taskType == "ARRIVAL" || flight.taskType == "TURNAROUND") {
                val chocksOn = getTime(flight.timings, "Chocks On")
                val byFirst = getTime(
                    flight.timings,
                    "BY First Bag",
                    "BY First Baggage"
                )
                val byLast = getTime(
                    flight.timings,
                    "BY Last Bag",
                    "BY Last Baggage"
                )
                val btFirst = getTime(
                    flight.timings,
                    "BT First Bag",
                    "BT First Baggage"
                )
                val btLast = getTime(
                    flight.timings,
                    "BT Last Bag",
                    "BT Last Baggage"
                )
 
                y += 4f
                sectionTitle("BAGGAGE DELIVERY PERFORMANCE • FROM CHOCKS ON")
 
                performanceRow(
                    "Local (BY) • First Bag",
                    formatDuration(secondsBetween(chocksOn, byFirst))
                )
                performanceRow(
                    "Local (BY) • Last Bag",
                    formatDuration(secondsBetween(chocksOn, byLast))
                )
                performanceRow(
                    "Transfer (BT) • First Bag",
                    formatDuration(secondsBetween(chocksOn, btFirst))
                )
                performanceRow(
                    "Transfer (BT) • Last Bag",
                    formatDuration(secondsBetween(chocksOn, btLast))
                )
            }
 
            if (flight.taskType == "TURNAROUND") {
                val onBlock = getTime(flight.timings, "On Block")
                val offBlock = getTime(flight.timings, "Off Block")
 
                y += 8f
                sectionTitle("TURNAROUND PERFORMANCE")
                performanceRow(
                    "On Block → Off Block",
                    formatDuration(secondsBetween(onBlock, offBlock))
                )
            }
 
            y += 4f
            sectionTitle("NOTES")
            newPageIfNeeded(58f)
 
            val noteText = flight.notes.takeIf { it.isNotBlank() } ?: "No notes"
            val noteWords = noteText.split(" ")
            val noteLines = mutableListOf<String>()
            var currentLine = ""
 
            noteWords.forEach { word ->
                val candidate = if (currentLine.isBlank()) word else "$currentLine $word"
                if (valuePaint.measureText(candidate) > contentWidth - 24f) {
                    if (currentLine.isNotBlank()) noteLines.add(currentLine)
                    currentLine = word
                } else {
                    currentLine = candidate
                }
            }
            if (currentLine.isNotBlank()) noteLines.add(currentLine)
 
            val noteBoxHeight = maxOf(46f, noteLines.size * 16f + 22f)
 
            canvas.drawRoundRect(
                RectF(margin, y, pageWidth - margin, y + noteBoxHeight),
                8f,
                8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = lightGrey
                }
            )
 
            noteLines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    margin + 12f,
                    y + 20f + (index * 16f),
                    valuePaint
                )
            }
 
            y += noteBoxHeight + 10f
 
            drawFooter()
            pdf.finishPage(page)
 
            val pdfDir = File(context.cacheDir, "shared_pdfs").apply {
                mkdirs()
            }
 
            val safeFlightNo = flight.flightNo
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
 
            val file = File(
                pdfDir,
                "Ramp_Task_${safeFlightNo}_${System.currentTimeMillis()}.pdf"
            )
 
            FileOutputStream(file).use { output ->
                pdf.writeTo(output)
            }
 
            pdf.close()
 
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
 
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Ramp Task Report - ${flight.flightNo}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
 
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share Flight Report as PDF"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to create PDF: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
 
    fun prepareEdit(flight: SavedFlight) {
        selectedFlight = flight
        selectedTaskType = flight.taskType
        flightNo = flight.flightNo
        departureFlightNo = flight.departureFlightNo
        sta = flight.sta
        std = flight.std
        registration = flight.registration
        aircraft = flight.aircraft
        stand = flight.stand
        notes = flight.notes
 
        stamps.clear()
        bagCounts.clear()
        bagCounts.putAll(flight.bagCounts)
 
        stamps.addAll(
            namesForTask(flight.taskType).map { name ->
                Stamp(
                    name = name,
                    time = flight.timings[name]?.takeIf { it.isNotBlank() }
                )
            }
        )
 
        page = AppPage.EDIT_SAVED_FLIGHT
    }
 
    fun checkMissingAndSave(isUpdate: Boolean) {
        val missing = stamps
            .filter { it.name != "D-15 Baggage Received" && it.time.isNullOrBlank() }
            .map { it.name }
 
        val d15CountMissing =
            namesForTask(selectedTaskType).contains("D-15 Baggage Received") &&
                bagCounts["D-15 Baggage Received"].isNullOrBlank()
 
        if (missing.isEmpty() && !d15CountMissing) {
            if (isUpdate) updateSavedFlight() else saveNewFlight()
        } else {
            missingTimings = if (d15CountMissing) {
                missing + "D-15 Baggage Count"
            } else {
                missing
            }
            showMissingWarning = true
        }
    }
 
    fun goHome() {
        selectedFlight = null
        selectedTaskType = ""
        resetForm()
        page = AppPage.HOME
    }
 
    fun goHistory() {
        selectedFlight = null
        historySearch = ""
        page = AppPage.HISTORY
    }
 
    BackHandler(enabled = page != AppPage.HOME) {
        when (page) {
            AppPage.HISTORY_DETAILS -> page = AppPage.HISTORY
            AppPage.EDIT_SAVED_FLIGHT -> page = AppPage.HISTORY_DETAILS
            AppPage.ACTIVE_FLIGHT -> page = AppPage.FLIGHT_SETUP
            AppPage.FLIGHT_SETUP -> goHome()
            AppPage.HISTORY -> goHome()
            AppPage.HOME -> Unit
        }
    }
 
    if (showMissingWarning) {
        AlertDialog(
            onDismissRequest = {
                showMissingWarning = false
            },
            title = {
                Text("Some timings are missing")
            },
            text = {
                Text(
                    "Missing: ${
                        missingTimings.joinToString(", ")
                    }\n\nSave anyway?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMissingWarning = false
                        if (page == AppPage.EDIT_SAVED_FLIGHT) {
                            updateSavedFlight()
                        } else {
                            saveNewFlight()
                        }
                    }
                ) {
                    Text("SAVE ANYWAY")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMissingWarning = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
 
    flightToDelete?.let { flight ->
        AlertDialog(
            onDismissRequest = {
                flightToDelete = null
            },
            title = {
                Text("Delete Flight?")
            },
            text = {
                Text(
                    "Delete ${flight.flightNo} from history? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteFlight(flight)
                    }
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        flightToDelete = null
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
 
    MaterialTheme {
        Scaffold(
            containerColor = Color.White,
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = page in listOf(
                            AppPage.HOME,
                            AppPage.FLIGHT_SETUP,
                            AppPage.ACTIVE_FLIGHT
                        ),
                        onClick = { goHome() },
                        icon = { Text("⌂") },
                        label = { Text("Home") }
                    )
 
                    NavigationBarItem(
                        selected = page in listOf(
                            AppPage.HISTORY,
                            AppPage.HISTORY_DETAILS,
                            AppPage.EDIT_SAVED_FLIGHT
                        ),
                        onClick = { goHistory() },
                        icon = { Text("◷") },
                        label = { Text("History") }
                    )
                }
            }
        ) { outerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outerPadding)
            ) {
                when (page) {
                    AppPage.HOME -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SoftBlue)
                                .padding(horizontal = 20.dp),
                            contentPadding = PaddingValues(
                                top = 28.dp,
                                bottom = 28.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(
                                    "Ramp Task",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
 
                                Text(
                                    "Time Tracker",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = RoyalBlue
                                )
 
                                Spacer(Modifier.height(6.dp))
 
                                Text(
                                    "Select your operation",
                                    style = MaterialTheme.typography.bodyLarge
                                )
 
                                Spacer(Modifier.height(12.dp))
                            }
 
                            item {
                                OperationCard(
                                    title = "ARRIVAL",
                                    subtitle = "Arrival handling timings",
                                    symbol = "↘ ✈",
                                    onClick = {
                                        startNewTask("ARRIVAL")
                                    }
                                )
                            }
 
                            item {
                                OperationCard(
                                    title = "DEPARTURE",
                                    subtitle = "Departure handling timings",
                                    symbol = "✈ ↗",
                                    onClick = {
                                        startNewTask("DEPARTURE")
                                    }
                                )
                            }
 
                            item {
                                OperationCard(
                                    title = "TURNAROUND",
                                    subtitle = "Complete arrival + departure",
                                    symbol = "✈ ↻",
                                    onClick = {
                                        startNewTask("TURNAROUND")
                                    }
                                )
                            }
                        }
                    }
 
                    AppPage.FLIGHT_SETUP -> {
                        FlightSetupScreen(
                            taskType = selectedTaskType,
                            flightNo = flightNo,
                            departureFlightNo = departureFlightNo,
                            sta = sta,
                            std = std,
                            registration = registration,
                            aircraft = aircraft,
                            stand = stand,
                            onFlightNoChange = { flightNo = it },
                            onDepartureFlightNoChange = { departureFlightNo = it },
                            onStaChange = { sta = it },
                            onStdChange = { std = it },
                            onRegistrationChange = { registration = it },
                            onAircraftChange = { aircraft = it },
                            onStandChange = { stand = it },
                            onBack = { goHome() },
                            onStart = {
                                initializeStampsForTask(selectedTaskType)
                                page = AppPage.ACTIVE_FLIGHT
                            }
                        )
                    }
 
                    AppPage.ACTIVE_FLIGHT -> {
                        Scaffold(
                            topBar = {
                                val activeTheme = when (selectedTaskType) {
                                    "ARRIVAL" -> Color(0xFF087A2F)
                                    "DEPARTURE" -> Color(0xFF4B1FA3)
                                    else -> Color(0xFFD31313)
                                }
 
                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = activeTheme,
                                        titleContentColor = Color.White,
                                        navigationIconContentColor = Color.White
                                    ),
                                    title = {
                                        Column {
                                            Text(selectedTaskType)
                                            Text(
                                                "$flightNo • Stand $stand",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                page = AppPage.FLIGHT_SETUP
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 14.dp),
                                contentPadding = PaddingValues(
                                    top = 10.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                items(
                                    displayRowsForTask(selectedTaskType),
                                    key = { "${it.left}-${it.right ?: ""}" }
                                ) { row ->
                                    val leftIndex = stamps.indexOfFirst { it.name == row.left }
                                    val rightIndex = row.right?.let { rightName ->
                                        stamps.indexOfFirst { it.name == rightName }
                                    } ?: -1
 
                                    TaskTimingRow(
                                        row = row,
                                        leftStamp = stamps.getOrNull(leftIndex),
                                        rightStamp = stamps.getOrNull(rightIndex),
                                        leftBagCount = bagCounts[row.left] ?: "",
                                        onLeftBagCountChange = { value ->
                                            if (value.all { it.isDigit() }) {
                                                bagCounts[row.left] = value
                                            }
                                        },
                                        onLeftRecord = {
                                            if (leftIndex >= 0) record(leftIndex)
                                        },
                                        onLeftEdit = {
                                            if (leftIndex >= 0) editStamp(leftIndex)
                                        },
                                        onLeftReset = {
                                            if (leftIndex >= 0) resetStamp(leftIndex)
                                        },
                                        onRightRecord = {
                                            if (rightIndex >= 0) record(rightIndex)
                                        },
                                        onRightEdit = {
                                            if (rightIndex >= 0) editStamp(rightIndex)
                                        },
                                        onRightReset = {
                                            if (rightIndex >= 0) resetStamp(rightIndex)
                                        },
                                        taskType = selectedTaskType
                                    )
                                }
 
                                item {
                                    val timingMap = stamps.associate {
                                        it.name to (it.time ?: "")
                                    }
 
                                    val taskStart = getTime(timingMap, "Task Start", "Task Started")
                                    val taskEnd = getTime(timingMap, "Task End")
                                    val chocksOn = getTime(timingMap, "Chocks On")
                                    val onBlock = getTime(timingMap, "On Block")
                                    val offBlock = getTime(timingMap, "Off Block")
                                    val byFirst = getTime(timingMap, "BY First Bag")
                                    val byLast = getTime(timingMap, "BY Last Bag")
                                    val btFirst = getTime(timingMap, "BT First Bag")
                                    val btLast = getTime(timingMap, "BT Last Bag")
 
                                    SummaryCard(
                                        taskType = selectedTaskType,
                                        taskDuration = formatDuration(secondsBetween(taskStart, taskEnd)),
                                        turnaround = formatDuration(
                                            secondsBetween(onBlock, offBlock)
                                        ),
                                        localFirst = formatDuration(
                                            secondsBetween(chocksOn, byFirst)
                                        ),
                                        localLast = formatDuration(
                                            secondsBetween(chocksOn, byLast)
                                        ),
                                        transferFirst = formatDuration(
                                            secondsBetween(chocksOn, btFirst)
                                        ),
                                        transferLast = formatDuration(
                                            secondsBetween(chocksOn, btLast)
                                        )
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = {
                                            notes = it
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        label = {
                                            Text("Notes")
                                        }
                                    )
                                }
 
                                item {
                                    Button(
                                        onClick = {
                                            checkMissingAndSave(isUpdate = false)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Navy
                                        )
                                    ) {
                                        Text("SAVE FLIGHT")
                                    }
                                }
                            }
                        }
                    }
 
                    AppPage.HISTORY -> {
                        val history = remember(historyRefreshKey) {
                            loadHistory()
                        }
 
                        val filteredHistory =
                            if (historySearch.isBlank()) {
                                history
                            } else {
                                val query = historySearch.trim().lowercase()
 
                                history.filter { flight ->
                                    flight.flightNo.lowercase().contains(query) ||
                                        flight.departureFlightNo.lowercase().contains(query) ||
                                        flight.registration.lowercase().contains(query) ||
                                        flight.aircraft.lowercase().contains(query) ||
                                        flight.stand.lowercase().contains(query) ||
                                        flight.date.lowercase().contains(query) ||
                                        flight.taskType.lowercase().contains(query)
                                }
                            }
 
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text("Flight History")
                                    }
                                )
                            }
                        ) { padding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 16.dp)
                            ) {
                                OutlinedTextField(
                                    value = historySearch,
                                    onValueChange = {
                                        historySearch = it
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text("Search flight, reg, date...")
                                    },
                                    singleLine = true
                                )
 
                                Spacer(Modifier.height(12.dp))
 
                                if (history.isEmpty()) {
                                    Text("No saved flights yet.")
                                } else if (filteredHistory.isEmpty()) {
                                    Text("No matching flights found.")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            bottom = 20.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(
                                            filteredHistory,
                                            key = {
                                                "${it.storageIndex}-${it.date}"
                                            }
                                        ) { flight ->
                                            HistoryFlightCard(
                                                flight = flight,
                                                onClick = {
                                                    selectedFlight = flight
                                                    page = AppPage.HISTORY_DETAILS
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
 
                    AppPage.HISTORY_DETAILS -> {
                        val flight = selectedFlight
 
                        if (flight == null) {
                            page = AppPage.HISTORY
                        } else {
                            val visibleNames = namesForTask(flight.taskType)
                            val taskStart = getTime(flight.timings, "Task Start", "Task Started")
                            val taskEnd = getTime(flight.timings, "Task End")
                            val chocksOn = getTime(flight.timings, "Chocks On")
                            val onBlock = getTime(flight.timings, "On Block")
                            val offBlock = getTime(flight.timings, "Off Block")
 
                            val byFirst = getTime(
                                flight.timings,
                                "BY First Bag",
                                "BY First Baggage"
                            )
                            val byLast = getTime(
                                flight.timings,
                                "BY Last Bag",
                                "BY Last Baggage"
                            )
                            val btFirst = getTime(
                                flight.timings,
                                "BT First Bag",
                                "BT First Baggage"
                            )
                            val btLast = getTime(
                                flight.timings,
                                "BT Last Bag",
                                "BT Last Baggage"
                            )
 
                            Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = {
                                            Text("Flight Details")
                                        },
                                        navigationIcon = {
                                            IconButton(
                                                onClick = {
                                                    page = AppPage.HISTORY
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowBack,
                                                    contentDescription = "Back"
                                                )
                                            }
                                        }
                                    )
                                }
                            ) { padding ->
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .padding(horizontal = 14.dp),
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = 24.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    item {
                                        FlightInfoCard(flight)
                                    }
 
                                    item {
                                        Text(
                                            "Recorded Timings",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
 
                                    items(visibleNames) { name ->
                                        SavedTimingRow(
                                            name = name,
                                            time = displayTime(flight.timings[name]),
                                            bagCount = flight.bagCounts[name]
                                                ?.takeIf { it.isNotBlank() }
                                        )
                                    }
 
                                    item {
                                        SummaryCard(
                                            taskType = flight.taskType,
                                            taskDuration = formatDuration(secondsBetween(taskStart, taskEnd)),
                                            turnaround = formatDuration(
                                                secondsBetween(onBlock, offBlock)
                                            ),
                                            localFirst = formatDuration(
                                                secondsBetween(chocksOn, byFirst)
                                            ),
                                            localLast = formatDuration(
                                                secondsBetween(chocksOn, byLast)
                                            ),
                                            transferFirst = formatDuration(
                                                secondsBetween(chocksOn, btFirst)
                                            ),
                                            transferLast = formatDuration(
                                                secondsBetween(chocksOn, btLast)
                                            )
                                        )
                                    }
 
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Text(
                                                    "Notes",
                                                    fontWeight = FontWeight.Bold
                                                )
 
                                                Spacer(Modifier.height(6.dp))
 
                                                Text(
                                                    flight.notes.takeIf {
                                                        it.isNotBlank()
                                                    } ?: "No notes"
                                                )
                                            }
                                        }
                                    }
 
                                    item {
                                        Button(
                                            onClick = {
                                                prepareEdit(flight)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Navy
                                            )
                                        ) {
                                            Text("EDIT FLIGHT")
                                        }
 
                                        Spacer(Modifier.height(8.dp))
 
                                        OutlinedButton(
                                            onClick = {
                                                shareFlightAsPdf(flight)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("SHARE AS PDF")
                                        }
 
                                        OutlinedButton(
                                            onClick = {
                                                shareFlightReport(flight)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("SHARE AS TEXT")
                                        }
 
                                        OutlinedButton(
                                            onClick = {
                                                copyFlightReport(flight)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("COPY FLIGHT DETAILS")
                                        }
 
                                        OutlinedButton(
                                            onClick = {
                                                flightToDelete = flight
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("DELETE FLIGHT")
                                        }
                                    }
                                }
                            }
                        }
                    }
 
                    AppPage.EDIT_SAVED_FLIGHT -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text("Edit Flight")
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                page = AppPage.HISTORY_DETAILS
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 14.dp),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    TaskBadge(selectedTaskType)
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = flightNo,
                                        onValueChange = {
                                            flightNo = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Flight Number")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = registration,
                                        onValueChange = {
                                            registration = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Aircraft Registration")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = aircraft,
                                        onValueChange = {
                                            aircraft = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Aircraft Type")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = stand,
                                        onValueChange = {
                                            stand = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Stand")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                items(
                                    displayRowsForTask(selectedTaskType),
                                    key = { "edit-${it.left}-${it.right ?: ""}" }
                                ) { row ->
                                    val leftIndex = stamps.indexOfFirst { it.name == row.left }
                                    val rightIndex = row.right?.let { rightName ->
                                        stamps.indexOfFirst { it.name == rightName }
                                    } ?: -1
 
                                    TaskTimingRow(
                                        row = row,
                                        leftStamp = stamps.getOrNull(leftIndex),
                                        rightStamp = stamps.getOrNull(rightIndex),
                                        leftBagCount = bagCounts[row.left] ?: "",
                                        onLeftBagCountChange = { value ->
                                            if (value.all { it.isDigit() }) {
                                                bagCounts[row.left] = value
                                            }
                                        },
                                        onLeftRecord = {
                                            if (leftIndex >= 0) record(leftIndex)
                                        },
                                        onLeftEdit = {
                                            if (leftIndex >= 0) editStamp(leftIndex)
                                        },
                                        onLeftReset = {
                                            if (leftIndex >= 0) resetStamp(leftIndex)
                                        },
                                        onRightRecord = {
                                            if (rightIndex >= 0) record(rightIndex)
                                        },
                                        onRightEdit = {
                                            if (rightIndex >= 0) editStamp(rightIndex)
                                        },
                                        onRightReset = {
                                            if (rightIndex >= 0) resetStamp(rightIndex)
                                        },
                                        taskType = selectedTaskType
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = {
                                            notes = it
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        label = {
                                            Text("Notes")
                                        }
                                    )
                                }
 
                                item {
                                    Button(
                                        onClick = {
                                            checkMissingAndSave(isUpdate = true)
                                        },
                                        enabled =
                                            flightNo.isNotBlank() &&
                                                registration.isNotBlank() &&
                                                aircraft.isNotBlank() &&
                                                stand.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Navy
                                        )
                                    ) {
                                        Text("UPDATE FLIGHT")
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
fun RampSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF061B3A),
                        Color(0xFF0B3A78),
                        Color(0xFF071426)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✈",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "RAMP TASK",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "TIME TRACKER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5DB7FF)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "SMART RAMP OPERATIONS",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(70.dp))
            Text(
                text = "Developed by",
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = "Abid Peediyakkal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "ACCURATE TRACKING • SMARTER OPERATIONS",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9ED5FF)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VERSION 1.0",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.60f)
            )
        }
    }
}
 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightSetupScreen(
    taskType: String,
    flightNo: String,
    departureFlightNo: String,
    sta: String,
    std: String,
    registration: String,
    aircraft: String,
    stand: String,
    onFlightNoChange: (String) -> Unit,
    onDepartureFlightNoChange: (String) -> Unit,
    onStaChange: (String) -> Unit,
    onStdChange: (String) -> Unit,
    onRegistrationChange: (String) -> Unit,
    onAircraftChange: (String) -> Unit,
    onStandChange: (String) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    val scheduleReady = when (taskType) {
        "ARRIVAL" -> sta.isNotBlank()
        "DEPARTURE" -> std.isNotBlank()
        else -> departureFlightNo.isNotBlank() && sta.isNotBlank() && std.isNotBlank()
    }
    val allFieldsReady =
        flightNo.isNotBlank() && scheduleReady &&
            registration.isNotBlank() && aircraft.isNotBlank() && stand.isNotBlank()
 
    val themeColor = when (taskType) {
        "ARRIVAL" -> Color(0xFF087A2F)
        "DEPARTURE" -> Color(0xFF4B1FA3)
        else -> Color(0xFFD31313)
    }
    val themeDark = when (taskType) {
        "ARRIVAL" -> Color(0xFF04551F)
        "DEPARTURE" -> Color(0xFF2C126B)
        else -> Color(0xFF9D0808)
    }
 
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                themeDark,
                                themeColor
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(
                        start = 8.dp,
                        end = 20.dp,
                        top = 10.dp,
                        bottom = 18.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
 
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            taskType,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
 
                        Text(
                            "Create New ${taskType.lowercase().replaceFirstChar { it.uppercase() }} Flight",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
 
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "✈",
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 22.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = SoftBlue
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = themeColor
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "✈",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                            }
                        }
 
                        Spacer(Modifier.width(16.dp))
 
                        Column {
                            Text(
                                "$taskType FLIGHT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = themeColor
                            )
 
                            Spacer(Modifier.height(3.dp))
 
                            Text(
                                "Record timing of ramp tasks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
 
            if (taskType == "TURNAROUND") {
                item {
                    FlightSetupFieldPair(
                        leftValue = flightNo, leftOnValueChange = onFlightNoChange,
                        leftTitle = "Arrival Flight", leftHint = "Flight no.", leftSymbol = "✈",
                        rightValue = sta, rightOnValueChange = onStaChange,
                        rightTitle = "STA", rightHint = "HH:mm", rightSymbol = "◷"
                    )
                }
                item {
                    FlightSetupFieldPair(
                        leftValue = departureFlightNo, leftOnValueChange = onDepartureFlightNoChange,
                        leftTitle = "Departure Flight", leftHint = "Flight no.", leftSymbol = "✈",
                        rightValue = std, rightOnValueChange = onStdChange,
                        rightTitle = "STD", rightHint = "HH:mm", rightSymbol = "◷"
                    )
                }
            } else {
                item {
                    FlightSetupFieldPair(
                        leftValue = flightNo, leftOnValueChange = onFlightNoChange,
                        leftTitle = "Flight Number", leftHint = "Flight no.", leftSymbol = "✈",
                        rightValue = if (taskType == "ARRIVAL") sta else std,
                        rightOnValueChange = if (taskType == "ARRIVAL") onStaChange else onStdChange,
                        rightTitle = if (taskType == "ARRIVAL") "STA" else "STD",
                        rightHint = "HH:mm", rightSymbol = "◷"
                    )
                }
            }
 
            item {
                FlightSetupFieldPair(
                    leftValue = registration, leftOnValueChange = onRegistrationChange,
                    leftTitle = "Aircraft Registration", leftHint = "Registration", leftSymbol = "▣",
                    rightValue = aircraft, rightOnValueChange = onAircraftChange,
                    rightTitle = "Aircraft Type", rightHint = "e.g. A350", rightSymbol = "✈"
                )
            }
 
            item {
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        FlightSetupField(
                            value = stand, onValueChange = onStandChange,
                            title = "Stand", hint = "Stand", symbol = "▤"
                        )
                    }
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Date", fontWeight = FontWeight.Bold, color = Navy)
                            Spacer(Modifier.height(12.dp))
                            Text(today, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
 
            item {
                Button(
                    onClick = onStart,
                    enabled = allFieldsReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColor,
                        disabledContainerColor = Color(0xFFD9DEE8)
                    )
                ) {
                    Text(
                        "▣   SAVE & START TASK",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
 
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PaleBlue
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ⓘ",
                            style = MaterialTheme.typography.titleLarge,
                            color = themeColor
                        )
 
                        Spacer(Modifier.width(12.dp))
 
                        Column {
                            Text(
                                "All fields are required to start tracking",
                                fontWeight = FontWeight.SemiBold,
                                color = themeColor
                            )
 
                            Text(
                                "Please enter complete flight details to continue",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}
 
@Composable
fun FlightSetupFieldPair(
    leftValue: String, leftOnValueChange: (String) -> Unit, leftTitle: String, leftHint: String, leftSymbol: String,
    rightValue: String, rightOnValueChange: (String) -> Unit, rightTitle: String, rightHint: String, rightSymbol: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.weight(1f)) {
            FlightSetupField(leftValue, leftOnValueChange, leftTitle, leftHint, leftSymbol)
        }
        Box(Modifier.weight(1f)) {
            FlightSetupField(rightValue, rightOnValueChange, rightTitle, rightHint, rightSymbol)
        }
    }
}
 
@Composable
fun FlightSetupField(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    hint: String,
    symbol: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(14.dp),
                color = PaleBlue
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = Navy,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
 
            Spacer(Modifier.width(12.dp))
 
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "$title *",
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
 
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(hint)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBlue,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}
 
@Composable
fun OperationCard(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit
) {
    val cardBrush = when (title) {
        "ARRIVAL" -> Brush.horizontalGradient(
            listOf(Color(0xFF04551F), Color(0xFF087A2F))
        )
 
        "DEPARTURE" -> Brush.horizontalGradient(
            listOf(Color(0xFF2C126B), Color(0xFF4B1FA3))
        )
 
        else -> Brush.horizontalGradient(
            listOf(Color(0xFF9D0808), Color(0xFFD31313))
        )
    }
 
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.20f)
            ) {
                Box(
                    modifier = Modifier.size(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        symbol,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
 
            Spacer(Modifier.width(18.dp))
 
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
 
                Spacer(Modifier.height(4.dp))
 
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}
 
@Composable
fun TaskBadge(taskType: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = PaleBlue
    ) {
        Text(
            taskType,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            fontWeight = FontWeight.Bold,
            color = Navy
        )
    }
}
 
@Composable
fun TaskTimingRow(
    row: TaskDisplayRow,
    leftStamp: Stamp?,
    rightStamp: Stamp?,
    leftBagCount: String,
    onLeftBagCountChange: (String) -> Unit,
    onLeftRecord: () -> Unit,
    onLeftEdit: () -> Unit,
    onLeftReset: () -> Unit,
    onRightRecord: () -> Unit,
    onRightEdit: () -> Unit,
    onRightReset: () -> Unit,
    taskType: String
) {
    val themeColor = when (taskType) {
        "ARRIVAL" -> Color(0xFF087A2F)
        "DEPARTURE" -> Color(0xFF4B1FA3)
        else -> Color(0xFFD31313)
    }
 
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (row.countOnly) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = row.left,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
 
                Spacer(Modifier.height(10.dp))
 
                OutlinedTextField(
                    value = leftBagCount,
                    onValueChange = onLeftBagCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bag Count (No Time Stamp)") },
                    singleLine = true
                )
            }
        } else if (row.right == null) {
            TimestampCell(
                label = row.left,
                stamp = leftStamp,
                onRecord = onLeftRecord,
                onEdit = onLeftEdit,
                onReset = onLeftReset,
                themeColor = themeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                TimestampCell(
                    label = row.left,
                    stamp = leftStamp,
                    onRecord = onLeftRecord,
                    onEdit = onLeftEdit,
                    onReset = onLeftReset,
                    themeColor = themeColor,
                    modifier = Modifier.weight(1f)
                )
 
                TimestampCell(
                    label = row.right,
                    stamp = rightStamp,
                    onRecord = onRightRecord,
                    onEdit = onRightEdit,
                    onReset = onRightReset,
                    themeColor = themeColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
 
@Composable
fun TimestampCell(
    label: String,
    stamp: Stamp?,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    themeColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = themeColor
        )
 
        Spacer(Modifier.height(8.dp))
 
        if (stamp?.time.isNullOrBlank()) {
            Button(
                onClick = onRecord,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColor
                ),
                contentPadding = PaddingValues(
                    horizontal = 8.dp,
                    vertical = 10.dp
                )
            ) {
                Text("RECORD")
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = themeColor.copy(alpha = 0.08f)
            ) {
                Text(
                    text = stamp?.time ?: "",
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 10.dp
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )
            }
 
            Spacer(Modifier.height(6.dp))
 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("EDIT", color = themeColor)
                }
 
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("RESET", color = themeColor)
                }
            }
        }
    }
}
 
@Composable
fun TimingCard(
    stamp: Stamp,
    bagCount: String,
    onBagCountChange: (String) -> Unit,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                stamp.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
 
            if (stamp.name == "D-15 Baggage Received") {
                Spacer(Modifier.height(10.dp))
 
                OutlinedTextField(
                    value = bagCount,
                    onValueChange = onBagCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bag Count (No Time Stamp)") },
                    singleLine = true
                )
            } else {
                Spacer(Modifier.height(10.dp))
 
                if (stamp.time.isNullOrBlank()) {
                    Button(
                        onClick = onRecord,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Navy
                        )
                    ) {
                        Text("RECORD TIME")
                    }
                } else {
                    Text(
                        text = "✓ ${stamp.time}",
                        color = RoyalBlue,
                        fontWeight = FontWeight.Bold
                    )
 
                    Spacer(Modifier.height(8.dp))
 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("EDIT")
                        }
 
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("RESET")
                        }
                    }
                }
            }
        }
    }
}
 
@Composable
fun SummaryCard(
    taskType: String,
    taskDuration: String,
    turnaround: String,
    localFirst: String,
    localLast: String,
    transferFirst: String,
    transferLast: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SoftBlue
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Automatic Timings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
 
            Spacer(Modifier.height(8.dp))
 
            Text(
                "Total Task Duration: $taskDuration",
                fontWeight = FontWeight.Bold,
                color = RoyalBlue
            )
            Spacer(Modifier.height(6.dp))
 
            if (taskType == "TURNAROUND") {
                Text(
                    "Turnaround: $turnaround",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
            }
 
            if (
                taskType == "ARRIVAL" ||
                taskType == "TURNAROUND"
            ) {
                Text(
                    "Baggage Delivery • From Chocks On",
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue
                )
 
                Spacer(Modifier.height(5.dp))
 
                Text("Local (BY) First Bag: $localFirst")
                Text("Local (BY) Last Bag: $localLast")
                Text("Transfer (BT) First Bag: $transferFirst")
                Text("Transfer (BT) Last Bag: $transferLast")
            }
 
            if (taskType == "DEPARTURE") {
                Text("Departure timings are recorded above.")
            }
        }
    }
}
 
@Composable
fun HistoryFlightCard(
    flight: SavedFlight,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    flight.flightNo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
 
                TaskBadge(flight.taskType)
            }
 
            Spacer(Modifier.height(8.dp))
 
            Text("${flight.registration} • ${flight.aircraft}")
            Text("Stand ${flight.stand} • ${flight.date}")
 
            Spacer(Modifier.height(8.dp))
 
            Text(
                "Tap to view full details",
                color = RoyalBlue
            )
        }
    }
}
 
@Composable
fun FlightInfoCard(
    flight: SavedFlight
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    flight.flightNo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
 
                TaskBadge(flight.taskType)
            }
 
            Spacer(Modifier.height(8.dp))
 
            @Composable
            fun detailPair(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(leftLabel, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(leftValue.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rightLabel, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(rightValue.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
 
            if (flight.taskType == "TURNAROUND") {
                detailPair("Arrival Flight", flight.flightNo, "STA", flight.sta)
                detailPair("Departure Flight", flight.departureFlightNo, "STD", flight.std)
            } else if (flight.taskType == "ARRIVAL") {
                detailPair("Flight Number", flight.flightNo, "STA", flight.sta)
            } else {
                detailPair("Flight Number", flight.flightNo, "STD", flight.std)
            }
            detailPair("Aircraft Registration", flight.registration, "Aircraft Type", flight.aircraft)
            detailPair("Stand", flight.stand, "Date", flight.date)
        }
    }
}
 
@Composable
fun SavedTimingRow(
    name: String,
    time: String,
    bagCount: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
 
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    time,
                    color = RoyalBlue,
                    fontWeight = FontWeight.Bold
                )
 
                if (!bagCount.isNullOrBlank()) {
                    Text(
                        "Count: $bagCount",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
