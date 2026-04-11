package dmx.focus_timer

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val RedHatMono = FontFamily(
    Font(R.font.redhatmono)
)

class TimerViewModel : ViewModel() {
    private val _totalSeconds = MutableStateFlow(25 * 60)
    val totalSeconds: StateFlow<Int> = _totalSeconds.asStateFlow()

    private val _timeLeft = MutableStateFlow(_totalSeconds.value)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var timerJob: Job? = null
    private var targetEndTimeMillis: Long = 0L

    fun setDuration(seconds: Int) {
        pauseTimer()
        _totalSeconds.value = seconds
        _timeLeft.value = seconds
    }

    fun toggleTimer() {
        if (_isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (_timeLeft.value <= 0) return

        _isRunning.value = true
        timerJob?.cancel()

        targetEndTimeMillis = System.currentTimeMillis() + (_timeLeft.value * 1000L)

        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                val currentTime = System.currentTimeMillis()
                val remainingSeconds = ((targetEndTimeMillis - currentTime) / 1000).toInt()

                if (remainingSeconds <= 0) {
                    _timeLeft.value = 0
                    _isRunning.value = false
                    break
                }

                _timeLeft.value = remainingSeconds
                delay(100L)
            }
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        _timeLeft.value = _totalSeconds.value
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                FocusTimerScreen()
            }
        }
    }
}

@Composable
fun FocusTimerScreen(viewModel: TimerViewModel = viewModel()) {
    val timeLeft by viewModel.timeLeft.collectAsState()
    val totalSeconds by viewModel.totalSeconds.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    var showFinishedDialog by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(timeLeft) {
        if (timeLeft == 0 && totalSeconds > 0 && !showFinishedDialog && !isRunning) {
            notifyUser(context)
            showFinishedDialog = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(250.dp)
            ) {
                val progress = if (totalSeconds > 0) timeLeft.toFloat() / totalSeconds.toFloat() else 1f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 100, easing = LinearEasing),
                    label = "progress_animation"
                )

                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                val minutes = timeLeft / 60
                val seconds = timeLeft % 60

                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    fontSize = 64.sp,
                    fontFamily = RedHatMono,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { viewModel.toggleTimer() }) {
                    Text(if (isRunning) "Pause" else "Start")
                }

                Button(onClick = { showSetupDialog = true }) {
                    Text("Set Length")
                }

                OutlinedButton(onClick = { viewModel.resetTimer() }) {
                    Text("Reset")
                }
            }
        }
    }

    if (showSetupDialog) {
        TimerSetupDialog(
            onDismiss = { showSetupDialog = false },
            onStart = { totalSecs ->
                viewModel.setDuration(totalSecs)
                showSetupDialog = false
            }
        )
    }

    if (showFinishedDialog) {
        AlertDialog(
            onDismissRequest = { showFinishedDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showFinishedDialog = false
                    viewModel.resetTimer()
                }) { Text("OK") }
            },
            title = { Text("Time's up!") },
            text = { Text("Your focus session has finished.") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSetupDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit
) {
    var customText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp, // Adds a subtle elevation shadow in M3
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Choose length:",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(10, 25, 30, 45)
                    presets.forEach { mins ->
                        Surface(
                            onClick = { onStart(mins * 60) },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "$mins min",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Custom length:",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("E.g: 10:00") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        val parsedSeconds = parseCustomTime(customText)
                        if (parsedSeconds != null && parsedSeconds > 0) {
                            onStart(parsedSeconds)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Set",
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun parseCustomTime(input: String): Int? {
    if (input.isBlank()) return null
    return try {
        if (input.contains(":")) {
            val parts = input.split(":")
            val m = parts[0].trim().toIntOrNull() ?: 0
            val s = parts[1].trim().toIntOrNull() ?: 0
            (m * 60) + s
        } else {
            (input.trim().toInt()) * 60
        }
    } catch (e: Exception) {
        null
    }
}

private fun notifyUser(context: Context) {
    MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)?.apply {
        start()
        setOnCompletionListener { release() }
    }

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}