package com.example.myapplication

import android.Manifest
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.CrimsonError
import com.example.myapplication.ui.theme.EmberOrange
import com.example.myapplication.ui.theme.MagicPurple
import com.example.myapplication.ui.theme.TuneGreen
import kotlin.math.abs
import kotlin.math.cos
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.GainProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import com.example.myapplication.ui.theme.CadenceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AppMode {
    HOME,
    TUNER,
    KEY_FINDER,
    METRONOME,
    SUGGESTER,
    FRETBOARD
}

class MainActivity : ComponentActivity() {

    private var dispatcher: AudioDispatcher? = null
    private var tunerNote by mutableStateOf("--")
    private var tunerCents by mutableStateOf(0f)
    private var isRecording by mutableStateOf(false)
    // Tuner stability: rolling window of last 4 MIDI note detections.
    // Display the note only when it's the majority in the window — tolerates
    // occasional missed/wrong frames without requiring consecutive matches.
    private val tunerNoteWindow = ArrayDeque<Int>(4)
    private var lastConfidentPitchMs = 0L
    // EMA smoothing for cents — reduces jitter on stable notes
    private var smoothedCents = 0f
    private var lastSmoothedMidiNote = -1

    // State for Key Finder
    private val detectedKeyNotes = mutableStateListOf<String>()
    private var foundKey by mutableStateOf("")
    private var lastKeyNoteDetectionTime by mutableStateOf(0L)

    // State for Metronome
    private var isMetronomeRunning by mutableStateOf(false)
    private var metronomeBpm by mutableStateOf(120)
    private var isBeat by mutableStateOf(false)
    private val metronomeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var metronomeJob: Job? = null
    private val tapTimestamps = mutableListOf<Long>()

    private var pendingRecordingMode: AppMode? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingRecordingMode?.let { startRecording(it) }
            }
            pendingRecordingMode = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CadenceTheme {
                MainScreen(
                    tunerNote = tunerNote,
                    tunerCents = tunerCents,
                    isRecording = isRecording,
                    detectedKeyNotes = detectedKeyNotes,
                    foundKey = foundKey,
                    isMetronomeRunning = isMetronomeRunning,
                    metronomeBpm = metronomeBpm,
                    isBeat = isBeat,
                    onStartRecording = ::startRecording,
                    onStopRecording = ::stopRecording,
                    onResetKeyFinder = ::resetKeyFinder,
                    onStartMetronome = ::startMetronome,
                    onStopMetronome = ::stopMetronome,
                    onBpmChange = { metronomeBpm = it },
                    onTapTempo = ::onTapTempo
                )
            }
        }
    }

    private fun startRecording(mode: AppMode) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecordingMode = mode
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isRecording = true
        // 8192-sample buffer at 22050 Hz = ~371 ms per window (~30 cycles of low E at 82 Hz).
        // Overlap of 6144 keeps the hop size at ~93 ms so the display stays responsive.
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 8192, 6144)
        val pitchDetectionHandler = PitchDetectionHandler { res, _ ->
            val pitchInHz = res.pitch
            val confidence = res.probability

            // Guitar range: low E2 (~82 Hz) to high e5 (~1175 Hz), with margin.
            // Low strings on an unplugged electric are quiet — 0.70 still rejects
            // broadband noise which scores far lower in the guitar frequency range.
            val isValidPitch = pitchInHz in 70f..1300f && confidence > 0.65f

            if (isValidPitch) {
                val a4 = 440.0
                val midiNote = (12 * (Math.log(pitchInHz.toDouble() / a4) / Math.log(2.0)) + 69).roundToInt()
                val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val detectedNote = noteNames[midiNote % 12]

                // Vote by note CLASS (0–11), not full MIDI note.
                // Low E fundamental (MIDI 40) and its harmonic (MIDI 52) are both
                // note class 4 (E), so they reinforce each other instead of splitting the vote.
                val noteClass = midiNote % 12
                if (tunerNoteWindow.size >= 4) tunerNoteWindow.removeFirst()
                tunerNoteWindow.addLast(noteClass)
                lastConfidentPitchMs = System.currentTimeMillis()

                val majority = tunerNoteWindow
                    .groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }
                val dominantNoteClass = majority?.key
                val dominantCount = majority?.value ?: 0

                if (dominantCount >= 2) {  // note must appear in at least 2 of the last 4 frames
                    val exactNoteFreq = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
                    val rawCents = (1200.0 * Math.log(pitchInHz.toDouble() / exactNoteFreq) / Math.log(2.0))
                        .toFloat().coerceIn(-50f, 50f)
                    // EMA: blend 50/50 when note is stable; snap immediately on note change
                    if (dominantNoteClass == lastSmoothedMidiNote) {
                        smoothedCents = smoothedCents * 0.5f + rawCents * 0.5f
                    } else {
                        smoothedCents = rawCents
                        lastSmoothedMidiNote = dominantNoteClass!!
                    }
                    runOnUiThread {
                        when (mode) {
                            AppMode.TUNER -> {
                                tunerNote = noteNames[((dominantNoteClass!! % 12) + 12) % 12]
                                tunerCents = smoothedCents
                            }
                            AppMode.KEY_FINDER -> {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastKeyNoteDetectionTime > 2000) {
                                    if (detectedNote.isNotEmpty() && !detectedKeyNotes.contains(detectedNote)) {
                                        detectedKeyNotes.add(detectedNote)
                                        lastKeyNoteDetectionTime = currentTime
                                    }
                                }
                                if (detectedKeyNotes.size >= 3) {
                                    findKeyFromNotes()
                                    stopRecording()
                                }
                            }
                            AppMode.METRONOME  -> {}
                            AppMode.SUGGESTER  -> {}
                            AppMode.FRETBOARD  -> {}
                            AppMode.HOME       -> {}
                        }
                    }
                }
            } else if (mode == AppMode.TUNER && System.currentTimeMillis() - lastConfidentPitchMs > 1500) {
                // No confident pitch for 1.5 s — clear the display (longer hold prevents flicker
                // between the sparse detections a quiet low string produces)
                runOnUiThread {
                    tunerNote = "--"
                    tunerCents = 0f
                }
            }
        }
        // Boost quiet signals (e.g. unplugged electric) before pitch analysis.
        // 4x gain makes low strings detectable without pushing loud signals into clipping.
        dispatcher?.addAudioProcessor(GainProcessor(8.0))
        // MPM (McLeod Pitch Method) is designed for musical instruments and outperforms
        // YIN on guitar, especially for low strings with rich harmonics at low amplitude.
        val pitchProcessor = PitchProcessor(PitchEstimationAlgorithm.MPM, 22050f, 8192, pitchDetectionHandler)
        dispatcher?.addAudioProcessor(pitchProcessor)
        dispatcher?.let { Thread(it, "Audio Dispatcher").start() }
    }

    private fun stopRecording() {
        isRecording = false
        tunerNote = "--"
        tunerCents = 0f
        tunerNoteWindow.clear()
        lastConfidentPitchMs = 0L
        smoothedCents = 0f
        lastSmoothedMidiNote = -1
        dispatcher?.stop()
    }

    private fun resetKeyFinder() {
        detectedKeyNotes.clear()
        foundKey = ""
        lastKeyNoteDetectionTime = 0L
        if (isRecording) stopRecording()
    }

    private fun findKeyFromNotes() {
        val possibleKeys = musicTheory.keys.filter {
            val scaleNotes = musicTheory[it]
            detectedKeyNotes.all { detectedNote -> scaleNotes?.contains(detectedNote) == true }
        }
        foundKey = if (possibleKeys.isNotEmpty()) possibleKeys.joinToString(", ") else "No matching key found"
    }

    private fun startMetronome() {
        isMetronomeRunning = true
        // Run on IO — audioTrack.write() is a blocking call
        metronomeJob = metronomeScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val clickSamples = generateClick(sampleRate)
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // Small buffer so write() blocks at the hardware rate, giving accurate timing
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            // Used to compensate if write() returns early (e.g. first beat with empty buffer)
            var nextBeatNs = System.nanoTime()

            try {
                while (isActive) {
                    val samplesPerBeat = (sampleRate * 60.0 / metronomeBpm).toInt()
                    val beatBuffer = ShortArray(samplesPerBeat)
                    val copyLen = minOf(clickSamples.size, samplesPerBeat)
                    clickSamples.copyInto(beatBuffer, 0, 0, copyLen)

                    isBeat = true
                    // Reset the visual flash independently — don't block the audio write
                    metronomeScope.launch { delay(80); isBeat = false }

                    // Blocks for ~one beat period while the hardware drains the buffer
                    audioTrack.write(beatBuffer, 0, beatBuffer.size)

                    // On the first beat the buffer starts empty, so write() returns slightly
                    // early. Use wall-clock time to pad any remaining time so every beat
                    // interval is uniform.
                    nextBeatNs += 60_000_000_000L / metronomeBpm
                    val driftNs = nextBeatNs - System.nanoTime()
                    if (driftNs > 1_000_000L) delay(driftNs / 1_000_000L)
                }
            } finally {
                audioTrack.stop()
                audioTrack.release()
                isBeat = false
            }
        }
    }

    private fun stopMetronome() {
        metronomeJob?.cancel()
        isMetronomeRunning = false
        isBeat = false
    }

    private fun onTapTempo() {
        val now = System.currentTimeMillis()
        // Reset tap sequence if more than 3 seconds since last tap
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 3000) {
            tapTimestamps.clear()
        }
        tapTimestamps.add(now)
        if (tapTimestamps.size >= 2) {
            val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
            val avgInterval = intervals.takeLast(8).average()
            val bpm = (60000.0 / avgInterval).roundToInt().coerceIn(40, 240)
            metronomeBpm = bpm
        }
    }

    private fun generateClick(sampleRate: Int): ShortArray {
        val numSamples = sampleRate * 30 / 1000  // 30ms click
        val buffer = ShortArray(numSamples)
        val freq = 1000.0
        for (i in 0 until numSamples) {
            val envelope = 1.0 - i.toDouble() / numSamples
            buffer[i] = (envelope * sin(2 * PI * freq * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    override fun onStop() {
        super.onStop()
        stopRecording()
        stopMetronome()
    }

    override fun onDestroy() {
        super.onDestroy()
        metronomeScope.cancel()
    }

    companion object {
        val musicTheory = mapOf(
            "C Major" to listOf("C", "D", "E", "F", "G", "A", "B"),
            "G Major" to listOf("G", "A", "B", "C", "D", "E", "F#"),
            "D Major" to listOf("D", "E", "F#", "G", "A", "B", "C#"),
            "A Major" to listOf("A", "B", "C#", "D", "E", "F#", "G#"),
            "E Major" to listOf("E", "F#", "G#", "A", "B", "C#", "D#"),
            "B Major" to listOf("B", "C#", "D#", "E", "F#", "G#", "A#"),
            "F# Major" to listOf("F#", "G#", "A#", "B", "C#", "D#", "E#"),
            "C# Major" to listOf("C#", "D#", "E#", "F#", "G#", "A#", "B#"),

            "F Major" to listOf("F", "G", "A", "Bb", "C", "D", "E"),
            "Bb Major" to listOf("Bb", "C", "D", "Eb", "F", "G", "A"),
            "Eb Major" to listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"),
            "Ab Major" to listOf("Ab", "Bb", "C", "Db", "Eb", "F", "G"),
            "Db Major" to listOf("Db", "Eb", "F", "Gb", "Ab", "Bb", "C"),
            "Gb Major" to listOf("Gb", "Ab", "Bb", "Cb", "Db", "Eb", "F"),

            "A Minor" to listOf("A", "B", "C", "D", "E", "F", "G"),
            "E Minor" to listOf("E", "F#", "G", "A", "B", "C", "D"),
            "B Minor" to listOf("B", "C#", "D", "E", "F#", "G", "A"),
            "F# Minor" to listOf("F#", "G#", "A", "B", "C#", "D", "E"),
            "C# Minor" to listOf("C#", "D#", "E", "F#", "G#", "A", "B"),
            "G# Minor" to listOf("G#", "A#", "B", "C#", "D#", "E", "F#"),
            "D# Minor" to listOf("D#", "E#", "F#", "G#", "A#", "B", "C#"),

            "D Minor" to listOf("D", "E", "F", "G", "A", "Bb", "C"),
            "G Minor" to listOf("G", "A", "Bb", "C", "D", "Eb", "F"),
            "C Minor" to listOf("C", "D", "Eb", "F", "G", "Ab", "Bb"),
            "F Minor" to listOf("F", "G", "Ab", "Bb", "C", "Db", "Eb"),
            "Bb Minor" to listOf("Bb", "C", "Db", "Eb", "F", "Gb", "Ab"),
            "Eb Minor" to listOf("Eb", "F", "Gb", "Ab", "Bb", "Cb", "Db"),
        )
    }
}

@Composable
fun MainScreen(
    tunerNote: String,
    tunerCents: Float,
    isRecording: Boolean,
    detectedKeyNotes: List<String>,
    foundKey: String,
    isMetronomeRunning: Boolean,
    metronomeBpm: Int,
    isBeat: Boolean,
    onStartRecording: (AppMode) -> Unit,
    onStopRecording: () -> Unit,
    onResetKeyFinder: () -> Unit,
    onStartMetronome: () -> Unit,
    onStopMetronome: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onTapTempo: () -> Unit
) {
    var appMode by remember { mutableStateOf(AppMode.HOME) }

    fun switchMode(mode: AppMode) {
        onResetKeyFinder()
        onStopMetronome()
        appMode = mode
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { CadenceNavBar(selectedMode = appMode, onModeSelected = ::switchMode) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MagicPurple.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.9f
                        )
                    )
                }
        ) {
            when (appMode) {
                AppMode.HOME -> HomeScreen(onNavigate = ::switchMode)
                AppMode.TUNER -> TunerScreen(
                    note = tunerNote,
                    cents = tunerCents,
                    isRecording = isRecording,
                    onStartRecording = { onStartRecording(AppMode.TUNER) },
                    onStopRecording = onStopRecording
                )
                AppMode.KEY_FINDER -> KeyFinderScreen(
                    notes = detectedKeyNotes,
                    isRecording = isRecording,
                    foundKey = foundKey,
                    onStart = { onStartRecording(AppMode.KEY_FINDER) },
                    onReset = onResetKeyFinder
                )
                AppMode.METRONOME -> MetronomeScreen(
                    bpm = metronomeBpm,
                    isRunning = isMetronomeRunning,
                    isBeat = isBeat,
                    onBpmChange = onBpmChange,
                    onStart = onStartMetronome,
                    onStop = onStopMetronome,
                    onTapTempo = onTapTempo
                )
                AppMode.SUGGESTER  -> SuggesterScreen()
                AppMode.FRETBOARD -> FretboardScreen()
            }
        }
    }
}

@Composable
fun CadenceNavBar(selectedMode: AppMode, onModeSelected: (AppMode) -> Unit) {
    data class NavItem(val mode: AppMode, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
    val items = listOf(
        NavItem(AppMode.HOME,       Icons.Default.Home,         "Home"),
        NavItem(AppMode.TUNER,      Icons.Default.Tune,         "Tuner"),
        NavItem(AppMode.KEY_FINDER, Icons.Default.Piano,        "Keys"),
        NavItem(AppMode.METRONOME,  Icons.Default.Timer,        "Tempo"),
        NavItem(AppMode.SUGGESTER,  Icons.Default.LibraryMusic, "Theory"),
        NavItem(AppMode.FRETBOARD,  Icons.Default.MusicNote,    "Notes"),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedMode == item.mode,
                onClick  = { onModeSelected(item.mode) },
                icon     = { Icon(item.icon, contentDescription = item.label) },
                label    = { Text(item.label) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}

private fun tempoLabel(bpm: Int): String = when {
    bpm < 60  -> "Largo"
    bpm < 66  -> "Larghetto"
    bpm < 76  -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else      -> "Prestissimo"
}

@Composable
fun MetronomeScreen(
    bpm: Int,
    isRunning: Boolean,
    isBeat: Boolean,
    onBpmChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTapTempo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val beatColor = if (isBeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val beatScale by animateFloatAsState(
        targetValue = if (isBeat) 1.28f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "beatScale"
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Metronome", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(beatScale)
                .clip(CircleShape)
                .background(beatColor)
        )
        Spacer(Modifier.height(32.dp))

        Text(
            text = "$bpm BPM",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = tempoLabel(bpm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.roundToInt()) },
            valueRange = 40f..240f,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onBpmChange((bpm - 10).coerceAtLeast(40)) }) { Text("-10") }
            OutlinedButton(onClick = { onBpmChange((bpm - 1).coerceAtLeast(40)) }) { Text("-1") }
            OutlinedButton(onClick = { onBpmChange((bpm + 1).coerceAtMost(240)) }) { Text("+1") }
            OutlinedButton(onClick = { onBpmChange((bpm + 10).coerceAtMost(240)) }) { Text("+10") }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onTapTempo,
            modifier = Modifier.size(width = 140.dp, height = 56.dp)
        ) {
            Text("Tap Tempo", fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))

        Button(onClick = if (isRunning) onStop else onStart) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}

@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    note: String,
    cents: Float,
    isRecording: Boolean
) {
    val inTune = isRecording && note != "--" && abs(cents) <= 10f
    val needleColor = when {
        !isRecording || note == "--" -> MaterialTheme.colorScheme.onSurfaceVariant
        abs(cents) <= 10f -> TuneGreen
        abs(cents) <= 25f -> EmberOrange
        else -> CrimsonError
    }
    val statusText = when {
        !isRecording -> "Tap Start Tuning"
        note == "--" -> "Listening..."
        abs(cents) <= 10f -> "IN TUNE"
        cents < 0 -> "FLAT  ♭"
        else -> "SHARP ♯"
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tuner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        // Semicircle gauge
        TunerGauge(cents = cents, needleColor = needleColor, isActive = isRecording && note != "--")

        Spacer(Modifier.height(16.dp))

        // Note name
        Text(
            text = note,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (inTune) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onBackground
        )

        // Cents readout
        Text(
            text = if (isRecording && note != "--") "${if (cents >= 0) "+" else ""}${cents.toInt()}¢" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = needleColor
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = needleColor
        )

        if (isRecording && note == "--") {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pluck the string a few times for best results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))

        if (isRecording) {
            Button(onClick = onStopRecording) {
                Text("Stop Tuning")
            }
        } else {
            Button(onClick = onStartRecording) {
                Text("Start Tuning")
            }
        }

    }
}

@Composable
private fun TunerGauge(
    cents: Float,
    needleColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedCents by animateFloatAsState(
        targetValue = if (isActive) cents else 0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "tunerNeedle"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val greenColor = TuneGreen
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier.size(width = 260.dp, height = 140.dp)
    ) {
        val strokeWidth = 12.dp.toPx()
        val needleStroke = 4.dp.toPx()
        val radius = (size.width / 2f) - strokeWidth
        val centerX = size.width / 2f
        val centerY = size.height - 8.dp.toPx()

        val arcRect = Size(radius * 2f, radius * 2f)
        val arcTopLeft = Offset(centerX - radius, centerY - radius)

        // Background arc (180° sweep from 180° start = left side to right)
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Green in-tune zone (±10¢ = ±18° out of 90°)
        val greenSweep = 36f  // 36° total for ±10¢ zone
        drawArc(
            color = greenColor.copy(alpha = 0.4f),
            startAngle = 270f - greenSweep / 2f,
            sweepAngle = greenSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Tick marks at ±50, ±25, 0
        val tickAngles = listOf(180f, 202.5f, 225f, 247.5f, 270f, 292.5f, 315f, 337.5f, 360f)
        val tickLabels = listOf("-50", "", "-25", "", "0", "", "+25", "", "+50")
        tickAngles.forEachIndexed { i, angleDeg ->
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val outerR = radius + strokeWidth / 2f
            val innerR = radius - strokeWidth / 2f - 8.dp.toPx()
            val isMajor = i % 2 == 0
            val tickInnerR = if (isMajor) innerR - 4.dp.toPx() else innerR + 4.dp.toPx()
            drawLine(
                color = onSurface.copy(alpha = 0.5f),
                start = Offset(
                    centerX + (outerR * cos(angleRad)).toFloat(),
                    centerY + (outerR * Math.sin(angleRad)).toFloat()
                ),
                end = Offset(
                    centerX + (tickInnerR * cos(angleRad)).toFloat(),
                    centerY + (tickInnerR * Math.sin(angleRad)).toFloat()
                ),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
            )
        }

        // Needle
        val needleAngleDeg = 270f + (animatedCents / 50f) * 90f
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        val needleLength = radius - strokeWidth / 2f
        drawLine(
            color = if (isActive) needleColor else onSurface.copy(alpha = 0.3f),
            start = Offset(centerX, centerY),
            end = Offset(
                centerX + (needleLength * cos(needleAngleRad)).toFloat(),
                centerY + (needleLength * Math.sin(needleAngleRad)).toFloat()
            ),
            strokeWidth = needleStroke,
            cap = StrokeCap.Round
        )

        // Center pivot dot
        drawCircle(
            color = onSurface,
            radius = 6.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
fun KeyFinderScreen(
    modifier: Modifier = Modifier,
    notes: List<String>,
    isRecording: Boolean,
    foundKey: String,
    onStart: () -> Unit,
    onReset: () -> Unit
) {
    var animatedEllipsis by remember { mutableStateOf("") }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                animatedEllipsis = "."; delay(400)
                animatedEllipsis = ".."; delay(400)
                animatedEllipsis = "..."; delay(400)
            }
        } else {
            animatedEllipsis = ""
        }
    }

    val instructionText = when {
        isRecording -> "Listening$animatedEllipsis"
        notes.size >= 3 -> "Analysis complete"
        notes.isEmpty() -> "Play 3 distinct notes"
        else -> "Play ${3 - notes.size} more note${if (3 - notes.size == 1) "" else "s"}"
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Key Finder", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = instructionText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Note pills
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { index ->
                val note = notes.getOrNull(index)
                val filled = note != null
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = note ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        val buttonText = when {
            isRecording -> "Listening..."
            notes.size >= 3 -> "Reset"
            else -> "Start Detection"
        }
        Button(onClick = if (notes.size >= 3) onReset else onStart, enabled = !isRecording) {
            Text(buttonText)
        }

        Spacer(Modifier.height(40.dp))

        // Result display
        if (foundKey.isNotEmpty()) {
            if (foundKey == "No matching key found") {
                Text(
                    text = "No matching key found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Possible Keys",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val keys = foundKey.split(", ")
                keys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TunerScreenPreview() {
    CadenceTheme {
        TunerScreen(onStartRecording = {}, onStopRecording = {}, note = "A#", cents = -12f, isRecording = true)
    }
}

@Preview(showBackground = true)
@Composable
fun KeyFinderScreenPreview() {
    CadenceTheme {
        KeyFinderScreen(notes = listOf("C", "G", "E"), isRecording = false, foundKey = "C Major", onStart = {}, onReset = {})
    }
}

@Preview(showBackground = true)
@Composable
fun MetronomeScreenPreview() {
    CadenceTheme {
        MetronomeScreen(bpm = 120, isRunning = false, isBeat = false, onBpmChange = {}, onStart = {}, onStop = {}, onTapTempo = {})
    }
}
