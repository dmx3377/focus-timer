package dmx.focus_timer.wearos.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
                val remainingSeconds =
                    ((targetEndTimeMillis - System.currentTimeMillis()) / 1000).toInt()

                if (remainingSeconds <= 0) {
                    _timeLeft.value = 0
                    _isRunning.value = false
                    break
                }

                if (_timeLeft.value != remainingSeconds) {
                    _timeLeft.value = remainingSeconds
                }
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

class WearOSActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold {
                    ScreenScaffold(
                        timeText = { } // Removed to prevent obstructing the timer
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            FocusTimerScreen()
                        }
                    }
                }
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

    LaunchedEffect(timeLeft, isRunning) {
        if (timeLeft == 0 && totalSeconds > 0 && !showFinishedDialog && !isRunning) {
            notifyUser(context)
            showFinishedDialog = true
        }
    }

    val activity = context as? ComponentActivity
    LaunchedEffect(activity) {
        val quickStart = activity?.intent?.getStringExtra("QUICK_START_MINUTES")?.toIntOrNull() ?: -1
        if (quickStart > 0) {
            viewModel.setDuration(quickStart * 60)
            if (!viewModel.isRunning.value) {
                viewModel.toggleTimer()
            }
            activity?.intent?.removeExtra("QUICK_START_MINUTES")
        }
    }

    // Static layout to avoid layout thrashing during animation
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Progress ring isolated
        TimerProgressRing(timeLeft = timeLeft, totalSeconds = totalSeconds)

        // Time Text
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        Text(
            text = "%02d:%02d".format(minutes, seconds),
            fontSize = 42.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center).offset(y = (-10).dp)
        )

        // Controls at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start"
                )
            }

            IconButton(
                onClick = { showSetupDialog = true },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Set Length",
                    modifier = Modifier.size(20.dp)
                )
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
        Dialog(
            onDismissRequest = { showFinishedDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Time's up!",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Session finished.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showFinishedDialog = false
                        viewModel.resetTimer()
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("OK", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun TimerProgressRing(timeLeft: Int, totalSeconds: Int) {
    val progress = remember(timeLeft, totalSeconds) {
        if (totalSeconds > 0) timeLeft.toFloat() / totalSeconds.toFloat() else 1f
    }

    val animatedProgressState = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress_animation"
    )

    CircularProgressIndicator(
        progress = { animatedProgressState.value },
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp), // Some padding so it's not flush with the very edge
        strokeWidth = 6.dp
    )
}

@Composable
fun TimerSetupDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf(10) }
    var customSeconds by remember { mutableStateOf(0) }

    val presets = remember { listOf(5, 10, 15, 25, 30, 45, 60) }
    val listState = rememberScalingLazyListState()

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (showCustom) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Custom Time",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Minutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { customMinutes++ }, modifier = Modifier.size(36.dp), colors = ButtonDefaults.filledTonalButtonColors()) { Text("+") }
                            Text(text = "%02d".format(customMinutes), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { if (customMinutes > 0) customMinutes-- }, modifier = Modifier.size(36.dp), colors = ButtonDefaults.filledTonalButtonColors()) { Text("-") }
                        }

                        Text(text = ":", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))

                        // Seconds
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { customSeconds = (customSeconds + 5) % 60 }, modifier = Modifier.size(36.dp), colors = ButtonDefaults.filledTonalButtonColors()) { Text("+") }
                            Text(text = "%02d".format(customSeconds), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { customSeconds = if (customSeconds - 5 < 0) 55 else customSeconds - 5 }, modifier = Modifier.size(36.dp), colors = ButtonDefaults.filledTonalButtonColors()) { Text("-") }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val totalSecs = (customMinutes * 60) + customSeconds
                            if (totalSecs > 0) onStart(totalSecs)
                         },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            } else {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        ListHeader {
                            Text("Select Duration")
                        }
                    }

                    item {
                        Button(
                            onClick = { showCustom = true },
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Custom...", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }

                    items(presets.size) { index ->
                        val mins = presets[index]
                        Button(
                            onClick = { onStart(mins * 60) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 4.dp)
                        ) {
                            Text("$mins Minutes", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission", "ObsoleteSdkInt")
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