package com.example.rampdutytimetracker
 
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
 
data class Stamp(
    val name: String,
    val time: String? = null
)
 
data class SavedFlight(
    val flightNo: String,
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
        "On Block",
        "Chocks On",
        "Step Connected",
        "GPU Connected",
        "A/C Connected",
        "Door Open",
        "BY First Bag",
        "BY Last Bag",
        "BT First Bag",
        "BT Last Bag"
    )
 
    val departureNames = listOf(
        "Task Started",
        "LIR/NOTOC Received",
        "Cargo Received",
        "First Bag Received",
        "D-15 Baggage Received",
        "Last Bag Received",
        "Last Bag Loaded",
        "Hold Closed",
        "Cabin Door Closed",
        "GPU Removed",
        "A/C Removed",
        "Step Removed",
        "Off Block"
    )
 
    val turnaroundNames = listOf(
        "On Block",
        "Chocks On",
        "Step Connected",
        "GPU Connected",
        "A/C Connected",
        "Hold Open",
        "BY First Bag",
        "BY Last Bag",
        "BT First Bag",
        "BT Last Bag",
        "LIR/NOTOC Received",
        "Cargo Received",
        "First Bag Received",
        "D-10 Baggage Received",
        "Last Bag Received",
        "Last Bag Loaded",
        "Hold Closed",
        "Cabin Door Closed",
        "GPU Removed",
        "A/C Removed",
        "Step Removed",
        "Off Block"
    )
 
    val allKnownNames = (
        arrivalNames +
        departureNames +
        turnaroundNames +
        listOf(
            // Old-version names retained so existing saved flights remain readable.
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
    var selectedTaskType by remember { mutableStateOf("") }
 
    var flightNo by remember { mutableStateOf("") }
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
 
    fun namesForTask(taskType: String): List<String> {
        return when (taskType.uppercase()) {
            "ARRIVAL" -> arrivalNames
            "DEPARTURE" -> departureNames
            else -> turnaroundNames
        }
    }
 
    fun resetForm() {
        flightNo = ""
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
        return SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())
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
                val second =
                    if (!existing.isNullOrBlank() && existing.split(":").size == 3) {
                        existing.split(":")[2].toIntOrNull() ?: 0
                    } else {
                        0
                    }
 
                val newTime = String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hour,
                    minute,
                    second
                )
 
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
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val start = fmt.parse(first)!!.time
            var end = fmt.parse(last)!!.time
 
            if (end < start) {
                end += 24 * 60 * 60 * 1000
            }
 
            (end - start) / 1000
        } catch (_: Exception) {
            null
        }
    }
 
    fun formatDuration(seconds: Long?): String {
        if (seconds == null) return "—"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "$minutes min $remainingSeconds sec"
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
 
            allKnownNames.forEach { name ->
                if (!timingsMap.containsKey(name)) {
                    timingsMap[name] = ""
                }
            }
 
            val countKeys = countsObject.keys()
            while (countKeys.hasNext()) {
                val key = countKeys.next()
                countsMap[key] = countsObject.optString(key, "")
            }
 
            var taskType = item.optString("taskType", "")
            if (taskType.isBlank()) {
                taskType = "TURNAROUND"
            }
 
            list.add(
                SavedFlight(
                    flightNo = item.optString("flightNo"),
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
        reg: String,
        aircraftType: String,
        standNumber: String,
        dateText: String,
        notesText: String,
        stampList: List<Stamp>,
        counts: Map<String, String>
    ) {
        obj.put("flightNo", flightNumber)
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
            reg = registration,
            aircraftType = aircraft,
            standNumber = stand,
            dateText = dateText,
            notesText = notes,
            stampList = stamps,
            counts = bagCounts
        )
 
        array.put(obj)
 
        prefs.edit()
            .putString("flights", array.toString())
            .apply()
 
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
            reg = registration,
            aircraftType = aircraft,
            standNumber = stand,
            dateText = original.date,
            notesText = notes,
            stampList = stamps,
            counts = bagCounts
        )
 
        array.put(original.storageIndex, updated)
 
        prefs.edit()
            .putString("flights", array.toString())
            .apply()
 
        Toast.makeText(
            context,
            "Flight Updated Successfully",
            Toast.LENGTH_SHORT
        ).show()
 
        selectedFlight = SavedFlight(
            flightNo = flightNo,
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
 
            prefs.edit()
                .putString("flights", array.toString())
                .apply()
 
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
            appendLine("Ramp Duty Time Tracker")
            appendLine()
            appendLine("Task: ${flight.taskType}")
            appendLine("Flight: ${flight.flightNo}")
            appendLine("Registration: ${flight.registration}")
            appendLine("Aircraft: ${flight.aircraft}")
            appendLine("Stand: ${flight.stand}")
            appendLine("Date: ${flight.date}")
            appendLine()
            appendLine("Recorded Timings")
 
            visibleNames.forEach { name ->
                val time = flight.timings[name]
                    ?.takeIf { it.isNotBlank() }
                    ?: "—"
                val count = flight.bagCounts[name]
                    ?.takeIf { it.isNotBlank() }
                if (count != null) {
                    appendLine("$name: $time • Count: $count")
                } else {
                    appendLine("$name: $time")
                }
            }
 
            if (flight.taskType == "ARRIVAL" || flight.taskType == "TURNAROUND") {
                appendLine()
                appendLine(
                    "Local (BY) delivery: ${
                        formatDuration(secondsBetween(byFirst, byLast))
                    }"
                )
                appendLine(
                    "Transfer (BT) delivery: ${
                        formatDuration(secondsBetween(btFirst, btLast))
                    }"
                )
            }
 
            if (flight.taskType == "TURNAROUND") {
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
 
    fun prepareEdit(flight: SavedFlight) {
        selectedFlight = flight
        selectedTaskType = flight.taskType
        flightNo = flight.flightNo
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
                    time = flight.timings[name]
                        ?.takeIf { it.isNotBlank() }
                )
            }
        )
 
        page = AppPage.EDIT_SAVED_FLIGHT
    }
 
    fun checkMissingAndSave(isUpdate: Boolean) {
        val missing = stamps
            .filter { it.time.isNullOrBlank() }
            .map { it.name }
 
        if (missing.isEmpty()) {
            if (isUpdate) updateSavedFlight() else saveNewFlight()
        } else {
            missingTimings = missing
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
 
    BackHandler(
        enabled = page != AppPage.HOME
    ) {
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
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = page in listOf(
                            AppPage.HOME,
                            AppPage.FLIGHT_SETUP,
                            AppPage.ACTIVE_FLIGHT
                        ),
                        onClick = {
                            goHome()
                        },
                        icon = {
                            Text("⌂")
                        },
                        label = {
                            Text("Home")
                        }
                    )
 
                    NavigationBarItem(
                        selected = page in listOf(
                            AppPage.HISTORY,
                            AppPage.HISTORY_DETAILS,
                            AppPage.EDIT_SAVED_FLIGHT
                        ),
                        onClick = {
                            goHistory()
                        },
                        icon = {
                            Text("≡")
                        },
                        label = {
                            Text("History")
                        }
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
                                .padding(horizontal = 20.dp),
                            contentPadding = PaddingValues(
                                top = 28.dp,
                                bottom = 28.dp
                            ),
                            verticalArrangement =
                                Arrangement.spacedBy(16.dp)
                        ) {
 
                            item {
                                Text(
                                    "Ramp Duty",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .headlineLarge,
                                    fontWeight = FontWeight.Bold
                                )
 
                                Text(
                                    "Time Tracker",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )
 
                                Spacer(
                                    Modifier.height(6.dp)
                                )
 
                                Text(
                                    "Select your operation",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyLarge
                                )
 
                                Spacer(
                                    Modifier.height(12.dp)
                                )
                            }
 
                            item {
                                OperationCard(
                                    title = "ARRIVAL",
                                    subtitle = "Arrival handling timings",
                                    symbol = "✈",
                                    onClick = {
                                        startNewTask("ARRIVAL")
                                    }
                                )
                            }
 
                            item {
                                OperationCard(
                                    title = "DEPARTURE",
                                    subtitle = "Departure handling timings",
                                    symbol = "↗",
                                    onClick = {
                                        startNewTask("DEPARTURE")
                                    }
                                )
                            }
 
                            item {
                                OperationCard(
                                    title = "TURNAROUND",
                                    subtitle = "Complete arrival + departure",
                                    symbol = "↻",
                                    onClick = {
                                        startNewTask("TURNAROUND")
                                    }
                                )
                            }
                        }
                    }
 
                    AppPage.FLIGHT_SETUP -> {
 
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(selectedTaskType)
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                goHome()
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
                                    .padding(horizontal = 18.dp),
                                contentPadding = PaddingValues(
                                    top = 12.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement =
                                    Arrangement.spacedBy(12.dp)
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        label = {
                                            Text(
                                                "Aircraft Registration"
                                            )
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Stand")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                item {
                                    Button(
                                        onClick = {
                                            initializeStampsForTask(
                                                selectedTaskType
                                            )
                                            page =
                                                AppPage.ACTIVE_FLIGHT
                                        },
                                        enabled =
                                            flightNo.isNotBlank() &&
                                            registration.isNotBlank() &&
                                            aircraft.isNotBlank() &&
                                            stand.isNotBlank(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                    ) {
                                        Text("START FLIGHT")
                                    }
                                }
                            }
                        }
                    }
 
                    AppPage.ACTIVE_FLIGHT -> {
 
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Column {
                                            Text(
                                                selectedTaskType
                                            )
                                            Text(
                                                "$flightNo • Stand $stand",
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelMedium
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                page =
                                                    AppPage.FLIGHT_SETUP
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
                                verticalArrangement =
                                    Arrangement.spacedBy(9.dp)
                            ) {
 
                                items(
                                    stamps,
                                    key = { it.name }
                                ) { stamp ->
 
                                    val index =
                                        stamps.indexOfFirst {
                                            it.name == stamp.name
                                        }
 
                                    TimingCard(
                                        stamp = stamp,
                                        bagCount = bagCounts[stamp.name] ?: "",
                                        onBagCountChange = { value ->
                                            if (value.all { it.isDigit() }) {
                                                bagCounts[stamp.name] = value
                                            }
                                        },
                                        onRecord = {
                                            record(index)
                                        },
                                        onEdit = {
                                            editStamp(index)
                                        },
                                        onReset = {
                                            resetStamp(index)
                                        }
                                    )
                                }
 
                                item {
 
                                    val timingMap =
                                        stamps.associate {
                                            it.name to (it.time ?: "")
                                        }
 
                                    val onBlock =
                                        getTime(
                                            timingMap,
                                            "On Block"
                                        )
 
                                    val offBlock =
                                        getTime(
                                            timingMap,
                                            "Off Block"
                                        )
 
                                    val byFirst =
                                        getTime(
                                            timingMap,
                                            "BY First Bag"
                                        )
 
                                    val byLast =
                                        getTime(
                                            timingMap,
                                            "BY Last Bag"
                                        )
 
                                    val btFirst =
                                        getTime(
                                            timingMap,
                                            "BT First Bag"
                                        )
 
                                    val btLast =
                                        getTime(
                                            timingMap,
                                            "BT Last Bag"
                                        )
 
                                    SummaryCard(
                                        taskType = selectedTaskType,
                                        turnaround =
                                            formatDuration(
                                                secondsBetween(
                                                    onBlock,
                                                    offBlock
                                                )
                                            ),
                                        localDelivery =
                                            formatDuration(
                                                secondsBetween(
                                                    byFirst,
                                                    byLast
                                                )
                                            ),
                                        transferDelivery =
                                            formatDuration(
                                                secondsBetween(
                                                    btFirst,
                                                    btLast
                                                )
                                            )
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = {
                                            notes = it
                                        },
                                        modifier =
                                            Modifier
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
                                            checkMissingAndSave(
                                                isUpdate = false
                                            )
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                    ) {
                                        Text("SAVE FLIGHT")
                                    }
                                }
                            }
                        }
                    }
 
                    AppPage.HISTORY -> {
 
                        val history = remember(
                            historyRefreshKey
                        ) {
                            loadHistory()
                        }
 
                        val filteredHistory =
                            if (historySearch.isBlank()) {
                                history
                            } else {
                                val query =
                                    historySearch
                                        .trim()
                                        .lowercase()
 
                                history.filter { flight ->
                                    flight.flightNo
                                        .lowercase()
                                        .contains(query) ||
                                    flight.registration
                                        .lowercase()
                                        .contains(query) ||
                                    flight.aircraft
                                        .lowercase()
                                        .contains(query) ||
                                    flight.stand
                                        .lowercase()
                                        .contains(query) ||
                                    flight.date
                                        .lowercase()
                                        .contains(query) ||
                                    flight.taskType
                                        .lowercase()
                                        .contains(query)
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
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            "Search flight, reg, date..."
                                        )
                                    },
                                    singleLine = true
                                )
 
                                Spacer(
                                    Modifier.height(12.dp)
                                )
 
                                if (history.isEmpty()) {
 
                                    Text(
                                        "No saved flights yet."
                                    )
 
                                } else if (
                                    filteredHistory.isEmpty()
                                ) {
 
                                    Text(
                                        "No matching flights found."
                                    )
 
                                } else {
 
                                    LazyColumn(
                                        modifier =
                                            Modifier.fillMaxSize(),
                                        contentPadding =
                                            PaddingValues(
                                                bottom = 20.dp
                                            ),
                                        verticalArrangement =
                                            Arrangement.spacedBy(
                                                10.dp
                                            )
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
                                                    selectedFlight =
                                                        flight
                                                    page =
                                                        AppPage
                                                            .HISTORY_DETAILS
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
 
                            val visibleNames =
                                namesForTask(
                                    flight.taskType
                                )
 
                            val onBlock =
                                getTime(
                                    flight.timings,
                                    "On Block"
                                )
 
                            val offBlock =
                                getTime(
                                    flight.timings,
                                    "Off Block"
                                )
 
                            val byFirst =
                                getTime(
                                    flight.timings,
                                    "BY First Bag",
                                    "BY First Baggage"
                                )
 
                            val byLast =
                                getTime(
                                    flight.timings,
                                    "BY Last Bag",
                                    "BY Last Baggage"
                                )
 
                            val btFirst =
                                getTime(
                                    flight.timings,
                                    "BT First Bag",
                                    "BT First Baggage"
                                )
 
                            val btLast =
                                getTime(
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
                                                    page =
                                                        AppPage.HISTORY
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowBack,
                                                    contentDescription =
                                                        "Back"
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
                                        .padding(
                                            horizontal = 14.dp
                                        ),
                                    contentPadding =
                                        PaddingValues(
                                            top = 8.dp,
                                            bottom = 24.dp
                                        ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            9.dp
                                        )
                                ) {
 
                                    item {
                                        FlightInfoCard(flight)
                                    }
 
                                    item {
                                        Text(
                                            "Recorded Timings",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleLarge,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }
 
                                    items(visibleNames) { name ->
                                        SavedTimingRow(
                                            name = name,
                                            time =
                                                flight.timings[name]
                                                    ?.takeIf {
                                                        it.isNotBlank()
                                                    }
                                                    ?: "—",
                                            bagCount =
                                                flight.bagCounts[name]
                                                    ?.takeIf {
                                                        it.isNotBlank()
                                                    }
                                        )
                                    }
 
                                    item {
                                        SummaryCard(
                                            taskType =
                                                flight.taskType,
                                            turnaround =
                                                formatDuration(
                                                    secondsBetween(
                                                        onBlock,
                                                        offBlock
                                                    )
                                                ),
                                            localDelivery =
                                                formatDuration(
                                                    secondsBetween(
                                                        byFirst,
                                                        byLast
                                                    )
                                                ),
                                            transferDelivery =
                                                formatDuration(
                                                    secondsBetween(
                                                        btFirst,
                                                        btLast
                                                    )
                                                )
                                        )
                                    }
 
                                    item {
                                        Card(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier =
                                                    Modifier.padding(
                                                        16.dp
                                                    )
                                            ) {
                                                Text(
                                                    "Notes",
                                                    fontWeight =
                                                        FontWeight.Bold
                                                )
                                                Spacer(
                                                    Modifier.height(
                                                        6.dp
                                                    )
                                                )
                                                Text(
                                                    flight.notes
                                                        .takeIf {
                                                            it.isNotBlank()
                                                        }
                                                        ?: "No notes"
                                                )
                                            }
                                        }
                                    }
 
                                    item {
                                        Button(
                                            onClick = {
                                                prepareEdit(
                                                    flight
                                                )
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth()
                                        ) {
                                            Text("EDIT FLIGHT")
                                        }
 
                                        Spacer(
                                            Modifier.height(8.dp)
                                        )
 
                                        OutlinedButton(
                                            onClick = {
                                                shareFlightReport(
                                                    flight
                                                )
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "SHARE FLIGHT REPORT"
                                            )
                                        }
 
                                        OutlinedButton(
                                            onClick = {
                                                copyFlightReport(
                                                    flight
                                                )
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "COPY FLIGHT DETAILS"
                                            )
                                        }
 
                                        OutlinedButton(
                                            onClick = {
                                                flightToDelete =
                                                    flight
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth()
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
                                                page =
                                                    AppPage.HISTORY_DETAILS
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
                                contentPadding =
                                    PaddingValues(
                                        top = 8.dp,
                                        bottom = 24.dp
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp)
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        label = {
                                            Text(
                                                "Aircraft Registration"
                                            )
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
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
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        label = {
                                            Text("Stand")
                                        },
                                        singleLine = true
                                    )
                                }
 
                                items(
                                    stamps,
                                    key = { it.name }
                                ) { stamp ->
 
                                    val index =
                                        stamps.indexOfFirst {
                                            it.name == stamp.name
                                        }
 
                                    TimingCard(
                                        stamp = stamp,
                                        bagCount = bagCounts[stamp.name] ?: "",
                                        onBagCountChange = { value ->
                                            if (value.all { it.isDigit() }) {
                                                bagCounts[stamp.name] = value
                                            }
                                        },
                                        onRecord = {
                                            record(index)
                                        },
                                        onEdit = {
                                            editStamp(index)
                                        },
                                        onReset = {
                                            resetStamp(index)
                                        }
                                    )
                                }
 
                                item {
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = {
                                            notes = it
                                        },
                                        modifier =
                                            Modifier
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
                                            checkMissingAndSave(
                                                isUpdate = true
                                            )
                                        },
                                        enabled =
                                            flightNo.isNotBlank() &&
                                            registration.isNotBlank() &&
                                            aircraft.isNotBlank() &&
                                            stand.isNotBlank(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
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
fun OperationCard(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
            ) {
                Box(
                    modifier =
                        Modifier.size(62.dp),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        symbol,
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
 
            Spacer(
                Modifier.width(18.dp)
            )
 
            Column {
                Text(
                    title,
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight = FontWeight.Bold
                )
 
                Spacer(
                    Modifier.height(4.dp)
                )
 
                Text(
                    subtitle,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }
        }
    }
}
 
@Composable
fun TaskBadge(taskType: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            MaterialTheme
                .colorScheme
                .primaryContainer
    ) {
        Text(
            taskType,
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
            fontWeight = FontWeight.Bold
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    stamp.name,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
 
                if (!stamp.time.isNullOrBlank()) {
                    Text(
                        "✓ ${stamp.time}",
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
 
            if (
                stamp.name == "D-15 Baggage Received" ||
                stamp.name == "D-10 Baggage Received"
            ) {
                Spacer(
                    Modifier.height(10.dp)
                )
 
                OutlinedTextField(
                    value = bagCount,
                    onValueChange = onBagCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Bag Count")
                    },
                    singleLine = true
                )
            }
 
            Spacer(
                Modifier.height(10.dp)
            )
 
            if (stamp.time.isNullOrBlank()) {
                Button(
                    onClick = onRecord,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text("RECORD TIME")
                }
            } else {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("EDIT")
                    }
 
                    OutlinedButton(
                        onClick = onReset,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("RESET")
                    }
                }
            }
        }
    }
}
 
@Composable
fun SummaryCard(
    taskType: String,
    turnaround: String,
    localDelivery: String,
    transferDelivery: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Automatic Timings",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight = FontWeight.Bold
            )
 
            Spacer(
                Modifier.height(8.dp)
            )
 
            if (taskType == "TURNAROUND") {
                Text("Turnaround: $turnaround")
            }
 
            if (
                taskType == "ARRIVAL" ||
                taskType == "TURNAROUND"
            ) {
                Text(
                    "Local (BY) delivery: $localDelivery"
                )
                Text(
                    "Transfer (BT) delivery: $transferDelivery"
                )
            }
 
            if (taskType == "DEPARTURE") {
                Text(
                    "Departure timings are recorded above."
                )
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
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    flight.flightNo,
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight = FontWeight.Bold
                )
 
                TaskBadge(flight.taskType)
            }
 
            Spacer(
                Modifier.height(8.dp)
            )
 
            Text(
                "${flight.registration} • ${flight.aircraft}"
            )
            Text(
                "Stand ${flight.stand} • ${flight.date}"
            )
 
            Spacer(
                Modifier.height(8.dp)
            )
 
            Text(
                "Tap to view full details",
                color =
                    MaterialTheme
                        .colorScheme
                        .primary
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
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    flight.flightNo,
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                    fontWeight = FontWeight.Bold
                )
 
                TaskBadge(flight.taskType)
            }
 
            Spacer(
                Modifier.height(8.dp)
            )
 
            Text(
                "Registration: ${flight.registration}"
            )
            Text(
                "Aircraft: ${flight.aircraft}"
            )
            Text(
                "Stand: ${flight.stand}"
            )
            Text(
                "Date: ${flight.date}"
            )
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
            horizontalArrangement =
                Arrangement.SpaceBetween
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
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    fontWeight = FontWeight.Bold
                )
                if (!bagCount.isNullOrBlank()) {
                    Text(
                        "Count: $bagCount",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }
            }
        }
    }
}
