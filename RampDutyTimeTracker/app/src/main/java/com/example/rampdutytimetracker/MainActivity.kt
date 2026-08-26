package com.example.rampdutytimetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Stamp(val name: String, val time: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RampDutyApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RampDutyApp() {
    var flightNo by remember { mutableStateOf("") }
    var aircraft by remember { mutableStateOf("") }
    var stand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }

    val names = listOf(
        "On Block", "Chocks On", "GPU Connected", "GPU Disconnected",
        "A/C Connected", "A/C Disconnected", "Step Connected", "Step Disconnected",
        "BY First Baggage", "BY Last Baggage", "BT First Baggage", "BT Last Baggage",
        "Door Closed", "Off Block"
    )
    val stamps = remember { mutableStateListOf(*names.map { Stamp(it) }.toTypedArray()) }

    fun record(index: Int) {
        if (stamps[index].time == null) {
            val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            stamps[index] = stamps[index].copy(time = t)
        }
    }

    fun minutesBetween(a: String?, b: String?): Long? {
        if (a == null || b == null) return null
        return try {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val x = fmt.parse(a)!!.time
            var y = fmt.parse(b)!!.time
            if (y < x) y += 24 * 60 * 60 * 1000L
            (y - x) / 60000L
        } catch (_: Exception) { null }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Ramp Duty Time Tracker") }) }
        ) { pad ->
            LazyColumn(
                modifier = Modifier.padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    if (!started) {
                        OutlinedTextField(flightNo, { flightNo = it }, Modifier.fillMaxWidth(), label = { Text("Flight Number") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(aircraft, { aircraft = it }, Modifier.fillMaxWidth(), label = { Text("Aircraft Type") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(stand, { stand = it }, Modifier.fillMaxWidth(), label = { Text("Stand") })
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { started = true },
                            enabled = flightNo.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("START FLIGHT") }
                    } else {
                        Text("$flightNo  •  $aircraft  •  Stand $stand", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (started) {
                    items(stamps.indices.toList()) { i ->
                        val s = stamps[i]
                        OutlinedButton(
                            onClick = { record(i) },
                            enabled = s.time == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (s.time == null) s.name else "${s.name}  •  ${s.time}")
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Automatic Timings", style = MaterialTheme.typography.titleLarge)

                        val onBlock = stamps.first { it.name == "On Block" }.time
                        val offBlock = stamps.first { it.name == "Off Block" }.time
                        val byFirst = stamps.first { it.name == "BY First Baggage" }.time
                        val byLast = stamps.first { it.name == "BY Last Baggage" }.time
                        val btFirst = stamps.first { it.name == "BT First Baggage" }.time
                        val btLast = stamps.first { it.name == "BT Last Baggage" }.time

                        Text("Turnaround: ${minutesBetween(onBlock, offBlock)?.let { "$it min" } ?: "—"}")
                        Text("Local (BY) delivery: ${minutesBetween(byFirst, byLast)?.let { "$it min" } ?: "—"}")
                        Text("Transfer (BT) delivery: ${minutesBetween(btFirst, btLast)?.let { "$it min" } ?: "—"}")

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            notes, { notes = it }, Modifier.fillMaxWidth().height(120.dp),
                            label = { Text("Notes") }
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { /* V1: data remains on screen; persistent history comes next */ },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SAVE FLIGHT") }
                    }
                }
            }
        }
    }
}
