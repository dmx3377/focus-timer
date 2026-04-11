package dmx.focus_timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                FocusTimerApp()
            }
        }
    }
}

@Composable
fun FocusTimerApp() {

    val totalTime = 25 * 60

    var timeLeft by rememberSaveable { mutableIntStateOf(totalTime) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var showFinishedDialog by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    fun startTimer() {
        if (isRunning) return
        isRunning = true

        timerJob?.cancel()

        timerJob = scope.launch {
            while (timeLeft > 0 && isRunning) {
                delay(1000)
                timeLeft--
            }

            isRunning = false

            if (timeLeft == 0) {
                showFinishedDialog = true
            }
        }
    }

    fun pauseTimer() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        timeLeft = totalTime
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60

    val progress = 1f - (timeLeft.toFloat() / totalTime.toFloat())

    Surface(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ⭕ Circular progress
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(140.dp),
                strokeWidth = 8.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ⏱ Timer text
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ▶ / ⏸ button
            Button(onClick = {
                if (isRunning) pauseTimer() else startTimer()
            }) {
                Text(if (isRunning) "Pause" else "Start")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔄 reset
            Button(onClick = { resetTimer() }) {
                Text("Reset")
            }
        }
    }

    // 🔔 Finished dialog
    if (showFinishedDialog) {
        AlertDialog(
            onDismissRequest = { showFinishedDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showFinishedDialog = false
                    resetTimer()
                }) {
                    Text("OK")
                }
            },
            title = { Text("Time's up!") },
            text = { Text("Your focus session has finished.") }
        )
    }
}