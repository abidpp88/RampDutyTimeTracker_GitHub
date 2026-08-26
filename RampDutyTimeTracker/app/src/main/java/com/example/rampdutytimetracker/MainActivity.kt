package com.example.rampdutytimetracker
 
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val timings: Map<String, String>
)
 
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
 
    var flightNo by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var aircraft by remember { mutableStateOf("") }
    var stand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
 
    var started by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedFlight by remember { mutableStateOf<SavedFlight?>(null) }
 
    val names = listOf(
        "On Block",
        "Chocks On",
        "GPU Connected",
        "GPU Disconnected",
        "A/C Connected",
        "A/C Disconnected",
        "Step Connected",
        "Step Disconnected",
        "BY First Baggage",
        "BY Last Baggage",
        "BT First Baggage",
        "BT Last Baggage",
        "Last Baggage Received",
        "Door Closed",
        "Off Block"
    )
 
    val stamps = remember {
        mutableStateListOf<Stamp>().apply {
            addAll(names.map { Stamp(it) })
        }
    }
 
    fun currentTime(): String {
        return SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())
    }
 
    fun record(index: Int) {
        if (stamps[index].time == null) {
            stamps[index] = stamps[index].copy(
                time = currentTime()
            )
        }
    }
 
    fun reset(index: Int) {
        stamps[index] = stamps[index].copy(time = null)
    }
 
    fun resetCurrentFlight() {
        flightNo = ""
        registration = ""
        aircraft = ""
        stand = ""
        notes = ""
        started = false
 
        stamps.indices.forEach { index ->
            stamps[index] = stamps[index].copy(time = null)
        }
    }
 
    fun edit(index: Int) {
 
        val calendar = Calendar.getInstance()
        val existing = stamps[index].time
 
        if (existing != null) {
            try {
                val parts = existing.split(":")
                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    parts[0].toInt()
                )
                calendar.set(
                    Calendar.MINUTE,
                    parts[1].toInt()
                )
            } catch (_: Exception) {
            }
        }
 
        TimePickerDialog(
            context,
            { _, hour, minute ->
 
                val second =
                    if (
                        existing != null &&
                        existing.split(":").size == 3
                    ) {
                        existing.split(":")[2].toInt()
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
 
                stamps[index] =
                    stamps[index].copy(time = newTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
 
    fun secondsBetween(
        first: String?,
        last: String?
    ): Long? {
        if (first.isNullOrBlank() || last.isNullOrBlank()) return null
 
        return try {
            val fmt = SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )
 
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
 
    fun saveFlight() {
 
        val prefs = context.getSharedPreferences(
            "ramp_history",
            0
        )
 
        val oldData = prefs.getString(
            "flights",
            "[]"
        ) ?: "[]"
 
        val array = JSONArray(oldData)
        val obj = JSONObject()
 
        obj.put("flightNo", flightNo)
        obj.put("registration", registration)
        obj.put("aircraft", aircraft)
        obj.put("stand", stand)
 
        obj.put(
            "date",
            SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                Locale.getDefault()
            ).format(Date())
        )
 
        obj.put("notes", notes)
 
        val timings = JSONObject()
 
        stamps.forEach {
            timings.put(
                it.name,
                it.time ?: ""
            )
        }
 
        obj.put("timings", timings)
        array.put(obj)
 
        prefs.edit()
            .putString(
                "flights",
                array.toString()
            )
            .apply()
 
        Toast.makeText(
            context,
            "Flight Saved Successfully",
            Toast.LENGTH_SHORT
        ).show()
 
        resetCurrentFlight()
        selectedFlight = null
        showHistory = false
    }
 
    fun loadHistory(): List<SavedFlight> {
 
        val prefs = context.getSharedPreferences(
            "ramp_history",
            0
        )
 
        val data = prefs.getString(
            "flights",
            "[]"
        ) ?: "[]"
 
        val array = JSONArray(data)
        val list = mutableListOf<SavedFlight>()
 
        for (i in 0 until array.length()) {
 
            val item = array.getJSONObject(i)
 
            val timingsObject =
                item.optJSONObject("timings") ?: JSONObject()
 
            val timingsMap = mutableMapOf<String, String>()
 
            names.forEach { name ->
                timingsMap[name] =
                    timingsObject.optString(name, "")
            }
 
            list.add(
                SavedFlight(
                    flightNo = item.optString("flightNo"),
                    registration = item.optString("registration"),
                    aircraft = item.optString("aircraft"),
                    stand = item.optString("stand"),
                    date = item.optString("date"),
                    notes = item.optString("notes"),
                    timings = timingsMap
                )
            )
        }
 
        return list.reversed()
    }
 
    BackHandler(
        enabled =
            selectedFlight != null ||
            showHistory ||
            started
    ) {
        when {
            selectedFlight != null -> {
                selectedFlight = null
            }
 
            showHistory -> {
                showHistory = false
            }
 
            started -> {
                started = false
            }
        }
    }
 
    MaterialTheme {
 
        when {
 
            selectedFlight != null -> {
 
                val flight = selectedFlight!!
 
                val onBlock =
                    flight.timings["On Block"]
 
                val offBlock =
                    flight.timings["Off Block"]
 
                val byFirst =
                    flight.timings["BY First Baggage"]
 
                val byLast =
                    flight.timings["BY Last Baggage"]
 
                val btFirst =
                    flight.timings["BT First Baggage"]
 
                val btLast =
                    flight.timings["BT Last Baggage"]
 
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Flight Details")
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        selectedFlight = null
                                    }
                                ) {
                                    Icon(
                                        imageVector =
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
 
                        item {
 
                            Card(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
 
                                Column(
                                    modifier =
                                        Modifier.padding(16.dp)
                                ) {
 
                                    Text(
                                        flight.flightNo,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleLarge
                                    )
 
                                    Spacer(
                                        Modifier.height(6.dp)
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
 
                        item {
                            Text(
                                "Recorded Timings",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge
                            )
                        }
 
                        itemsIndexed(names) {
                                _,
                                name ->
 
                            Card(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {
 
                                    Text(
                                        name,
                                        modifier =
                                            Modifier.weight(1f)
                                    )
 
                                    Text(
                                        flight.timings[name]
                                            ?.takeIf {
                                                it.isNotBlank()
                                            }
                                            ?: "—"
                                    )
                                }
                            }
                        }
 
                        item {
 
                            Spacer(
                                Modifier.height(4.dp)
                            )
 
                            Text(
                                "Automatic Timings",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge
                            )
 
                            Spacer(
                                Modifier.height(8.dp)
                            )
 
                            Text(
                                "Turnaround: ${
                                    formatDuration(
                                        secondsBetween(
                                            onBlock,
                                            offBlock
                                        )
                                    )
                                }"
                            )
 
                            Text(
                                "Local (BY) delivery: ${
                                    formatDuration(
                                        secondsBetween(
                                            byFirst,
                                            byLast
                                        )
                                    )
                                }"
                            )
 
                            Text(
                                "Transfer (BT) delivery: ${
                                    formatDuration(
                                        secondsBetween(
                                            btFirst,
                                            btLast
                                        )
                                    )
                                }"
                            )
 
                            Spacer(
                                Modifier.height(14.dp)
                            )
 
                            Text(
                                "Notes",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge
                            )
 
                            Spacer(
                                Modifier.height(6.dp)
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
            }
 
            showHistory -> {
 
                val history = loadHistory()
 
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Flight History")
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        showHistory = false
                                    }
                                ) {
                                    Icon(
                                        imageVector =
                                            Icons.Default.ArrowBack,
                                        contentDescription =
                                            "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
 
                    if (history.isEmpty()) {
 
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(16.dp)
                        ) {
                            Text(
                                "No saved flights yet."
                            )
                        }
 
                    } else {
 
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
 
                            itemsIndexed(history) {
                                    _,
                                    flight ->
 
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFlight =
                                                    flight
                                            }
                                ) {
 
                                    Column(
                                        modifier =
                                            Modifier.padding(16.dp)
                                    ) {
 
                                        Text(
                                            flight.flightNo,
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleLarge
                                        )
 
                                        Spacer(
                                            Modifier.height(4.dp)
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
 
                                        Spacer(
                                            Modifier.height(6.dp)
                                        )
 
                                        Text(
                                            "Tap to view full details"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
 
            else -> {
 
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Ramp Duty Time Tracker"
                                )
                            },
                            actions = {
 
                                TextButton(
                                    onClick = {
                                        showHistory = true
                                    }
                                ) {
                                    Text("HISTORY")
                                }
                            }
                        )
                    }
                ) { pad ->
 
                    LazyColumn(
                        modifier =
                            Modifier
                                .padding(pad)
                                .padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
 
                        item {
 
                            if (!started) {
 
                                OutlinedTextField(
                                    value = flightNo,
                                    onValueChange = {
                                        flightNo = it
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text("Flight Number")
                                    }
                                )
 
                                Spacer(
                                    Modifier.height(8.dp)
                                )
 
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
                                    }
                                )
 
                                Spacer(
                                    Modifier.height(8.dp)
                                )
 
                                OutlinedTextField(
                                    value = aircraft,
                                    onValueChange = {
                                        aircraft = it
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text("Aircraft Type")
                                    }
                                )
 
                                Spacer(
                                    Modifier.height(8.dp)
                                )
 
                                OutlinedTextField(
                                    value = stand,
                                    onValueChange = {
                                        stand = it
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    label = {
                                        Text("Stand")
                                    }
                                )
 
                                Spacer(
                                    Modifier.height(12.dp)
                                )
 
                                Button(
                                    onClick = {
                                        started = true
                                    },
                                    enabled =
                                        flightNo.isNotBlank() &&
                                        registration.isNotBlank() &&
                                        aircraft.isNotBlank() &&
                                        stand.isNotBlank(),
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text("START FLIGHT")
                                }
 
                            } else {
 
                                Text(
                                    "$flightNo • $registration • $aircraft • Stand $stand",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium
                                )
                            }
                        }
 
                        if (started) {
 
                            itemsIndexed(stamps) {
                                    index,
                                    stamp ->
 
                                Card(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
 
                                    Column(
                                        modifier =
                                            Modifier.padding(12.dp)
                                    ) {
 
                                        Text(
                                            if (
                                                stamp.time == null
                                            ) {
                                                stamp.name
                                            } else {
                                                "${stamp.name} • ${stamp.time}"
                                            },
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .titleMedium
                                        )
 
                                        Spacer(
                                            Modifier.height(8.dp)
                                        )
 
                                        if (
                                            stamp.time == null
                                        ) {
 
                                            Button(
                                                onClick = {
                                                    record(index)
                                                },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                            ) {
                                                Text(
                                                    "RECORD TIME"
                                                )
                                            }
 
                                        } else {
 
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth(),
                                                horizontalArrangement =
                                                    Arrangement
                                                        .spacedBy(
                                                            8.dp
                                                        )
                                            ) {
 
                                                OutlinedButton(
                                                    onClick = {
                                                        edit(index)
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                ) {
                                                    Text("EDIT")
                                                }
 
                                                OutlinedButton(
                                                    onClick = {
                                                        reset(index)
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                ) {
                                                    Text("RESET")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
 
                            item {
 
                                Spacer(
                                    Modifier.height(10.dp)
                                )
 
                                Text(
                                    "Automatic Timings",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge
                                )
 
                                Spacer(
                                    Modifier.height(8.dp)
                                )
 
                                val onBlock =
                                    stamps.first {
                                        it.name == "On Block"
                                    }.time
 
                                val offBlock =
                                    stamps.first {
                                        it.name == "Off Block"
                                    }.time
 
                                val byFirst =
                                    stamps.first {
                                        it.name ==
                                            "BY First Baggage"
                                    }.time
 
                                val byLast =
                                    stamps.first {
                                        it.name ==
                                            "BY Last Baggage"
                                    }.time
 
                                val btFirst =
                                    stamps.first {
                                        it.name ==
                                            "BT First Baggage"
                                    }.time
 
                                val btLast =
                                    stamps.first {
                                        it.name ==
                                            "BT Last Baggage"
                                    }.time
 
                                Text(
                                    "Turnaround: ${
                                        formatDuration(
                                            secondsBetween(
                                                onBlock,
                                                offBlock
                                            )
                                        )
                                    }"
                                )
 
                                Text(
                                    "Local (BY) delivery: ${
                                        formatDuration(
                                            secondsBetween(
                                                byFirst,
                                                byLast
                                            )
                                        )
                                    }"
                                )
 
                                Text(
                                    "Transfer (BT) delivery: ${
                                        formatDuration(
                                            secondsBetween(
                                                btFirst,
                                                btLast
                                            )
                                        )
                                    }"
                                )
 
                                Spacer(
                                    Modifier.height(12.dp)
                                )
 
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
 
                                Spacer(
                                    Modifier.height(12.dp)
                                )
 
                                Button(
                                    onClick = {
                                        saveFlight()
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text("SAVE FLIGHT")
                                }
 
                                Spacer(
                                    Modifier.height(8.dp)
                                )
 
                                OutlinedButton(
                                    onClick = {
                                        showHistory = true
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text("VIEW HISTORY")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
