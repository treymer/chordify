package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.myapplication.ui.theme.AncientGold

private val FB_NOTE_NAMES    = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val FB_DISPLAY_NAMES = listOf("C","C#/Db","D","D#/Eb","E","F","F#/Gb","G","G#/Ab","A","A#/Bb","B")

// Standard tuning, top → bottom on diagram (high e to low E)
private val FB_OPEN_NOTES    = listOf(4, 11, 7, 2, 9, 4) // E4, B3, G3, D3, A2, E2
private val FB_STRING_LABELS = listOf("e", "B", "G", "D", "A", "E")

private val INLAY_SINGLE = listOf(3, 5, 7, 9) // single-dot fret positions within a 12-fret block

// Scale interval patterns (semitones from root)
private data class ScaleDefinition(val name: String, val intervals: List<Int>)
private val FB_SCALES = listOf(
    ScaleDefinition("Major",              listOf(0, 2, 4, 5, 7, 9, 11)),
    ScaleDefinition("Natural Minor",      listOf(0, 2, 3, 5, 7, 8, 10)),
    ScaleDefinition("Minor Pentatonic",   listOf(0, 3, 5, 7, 10)),
    ScaleDefinition("Major Pentatonic",   listOf(0, 2, 4, 7, 9)),
    ScaleDefinition("Blues",              listOf(0, 3, 5, 6, 7, 10)),
    ScaleDefinition("Dorian",             listOf(0, 2, 3, 5, 7, 9, 10)),
    ScaleDefinition("Mixolydian",         listOf(0, 2, 4, 5, 7, 9, 10)),
)

// Sentinel value: no single note selected, all-notes mode
private const val ALL_NOTES_INDEX = -1
// Sentinel value: no scale filter active
private const val NO_SCALE = -1

@Composable
fun FretboardScreen(modifier: Modifier = Modifier) {
    // -1 means "All Notes" mode; 0-11 means a specific note
    var selectedNoteIndex by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 12 })
    val scope = rememberCoroutineScope()
    val noteIndex = pagerState.currentPage

    // Scale mode state
    var scaleRootIndex by remember { mutableStateOf(0) }
    var selectedScaleIndex by remember { mutableStateOf(NO_SCALE) }

    // Sync pager -> selectedNoteIndex when user swipes
    // (Keep them in sync: pager drives the display when in single-note mode)

    // Which note indices to highlight: ALL_NOTES_INDEX → all 12; scale active → scale notes; else → single note
    val highlightSet: Set<Int> = when {
        selectedScaleIndex != NO_SCALE && selectedNoteIndex != ALL_NOTES_INDEX -> {
            // Single-note mode with scale: treat selected note as root, show all scale notes
            val scale = FB_SCALES[selectedScaleIndex]
            scale.intervals.map { (selectedNoteIndex + it) % 12 }.toSet()
        }
        selectedNoteIndex == ALL_NOTES_INDEX && selectedScaleIndex != NO_SCALE -> {
            // All-notes mode with scale: use separate root selector
            val scale = FB_SCALES[selectedScaleIndex]
            scale.intervals.map { (scaleRootIndex + it) % 12 }.toSet()
        }
        selectedNoteIndex == ALL_NOTES_INDEX -> (0..11).toSet()
        else -> setOf(noteIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ───────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(
                "Note Finder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a note, swipe, or use All Notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            // Note chip row with "All Notes" button prepended
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                // All Notes chip
                FilterChip(
                    selected = selectedNoteIndex == ALL_NOTES_INDEX,
                    onClick = { selectedNoteIndex = ALL_NOTES_INDEX },
                    label = { Text("All") },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FB_NOTE_NAMES.forEachIndexed { i, _ ->
                    FilterChip(
                        selected = selectedNoteIndex == i && selectedNoteIndex != ALL_NOTES_INDEX,
                        onClick  = {
                            selectedNoteIndex = i
                            scope.launch { pagerState.animateScrollToPage(i) }
                        },
                        label    = { Text(FB_DISPLAY_NAMES[i]) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            // Scale filter row — always enabled and interactive
            Spacer(Modifier.height(6.dp))
            Text(
                "Scale filter:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = selectedScaleIndex == NO_SCALE,
                    onClick = { selectedScaleIndex = NO_SCALE },
                    label = { Text("None") },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FB_SCALES.forEachIndexed { i, scale ->
                    FilterChip(
                        selected = selectedScaleIndex == i,
                        onClick = { selectedScaleIndex = i },
                        label = { Text(scale.name) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            // Root note selector — only shown when in All Notes mode with a scale selected
            if (selectedNoteIndex == ALL_NOTES_INDEX && selectedScaleIndex != NO_SCALE) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Root:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FB_NOTE_NAMES.forEachIndexed { i, _ ->
                        FilterChip(
                            selected = scaleRootIndex == i,
                            onClick = { scaleRootIndex = i },
                            label = { Text(FB_DISPLAY_NAMES[i]) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                val scaleNoteNames = FB_SCALES[selectedScaleIndex].intervals
                    .map { FB_DISPLAY_NAMES[(scaleRootIndex + it) % 12] }
                Spacer(Modifier.height(2.dp))
                Text(
                    scaleNoteNames.joinToString(" – "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // In single-note + scale mode, show the scale notes summary (selected chip IS the root)
            if (selectedNoteIndex != ALL_NOTES_INDEX && selectedScaleIndex != NO_SCALE) {
                val scaleNoteNames = FB_SCALES[selectedScaleIndex].intervals
                    .map { FB_DISPLAY_NAMES[(selectedNoteIndex + it) % 12] }
                Spacer(Modifier.height(2.dp))
                Text(
                    scaleNoteNames.joinToString(" – "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Fretboard display ─────────────────────────────────────────────────
        if (selectedNoteIndex == ALL_NOTES_INDEX || selectedScaleIndex != NO_SCALE) {
            // Static full-neck view: used for All Notes mode, or any time a scale is active
            val topLabel = when {
                selectedNoteIndex != ALL_NOTES_INDEX && selectedScaleIndex != NO_SCALE ->
                    "Frets 0 – 12  (${FB_DISPLAY_NAMES[selectedNoteIndex]} ${FB_SCALES[selectedScaleIndex].name})"
                selectedScaleIndex != NO_SCALE ->
                    "Frets 0 – 12  (${FB_DISPLAY_NAMES[scaleRootIndex]} ${FB_SCALES[selectedScaleIndex].name})"
                else ->
                    "Frets 0 – 12  (all notes)"
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    topLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                // Determine the root to highlight in gold: selected note (single+scale) or
                // the explicit root selector (all+scale); null when no scale is active.
                val diagramRoot: Int? = when {
                    selectedScaleIndex != NO_SCALE && selectedNoteIndex != ALL_NOTES_INDEX ->
                        selectedNoteIndex
                    selectedScaleIndex != NO_SCALE ->
                        scaleRootIndex
                    else -> null
                }
                FretboardDiagram(
                    highlightedNotes = highlightSet,
                    showAllLabels = true,
                    rootNoteIndex = diagramRoot,
                    firstFret = 0, lastFret = 12
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Frets 13 – 24  (one octave higher)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                FretboardDiagram(
                    highlightedNotes = highlightSet,
                    showAllLabels = true,
                    rootNoteIndex = diagramRoot,
                    firstFret = 13, lastFret = 24
                )
                Spacer(Modifier.height(16.dp))
            }
        } else {
            // Single-note pager mode (no scale selected)
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Frets 0 – 12",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    FretboardDiagram(
                        highlightedNotes = setOf(page),
                        showAllLabels = false,
                        firstFret = 0, lastFret = 12
                    )

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Frets 13 – 24  (same notes, one octave higher)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    FretboardDiagram(
                        highlightedNotes = setOf(page),
                        showAllLabels = false,
                        firstFret = 13, lastFret = 24
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// Dot colors for all-notes mode: cycle through a palette so adjacent notes are visually distinct.
private val NOTE_COLORS = listOf(
    Color(0xFF7C4DFF), // C  – purple
    Color(0xFF2979FF), // C#
    Color(0xFF00BCD4), // D  – cyan
    Color(0xFF00897B), // D#
    Color(0xFF43A047), // E  – green
    Color(0xFFC0CA33), // F  – lime
    Color(0xFFFFA000), // F# – amber
    Color(0xFFE64A19), // G  – deep orange
    Color(0xFFD81B60), // G# – pink
    Color(0xFF8E24AA), // A  – deep purple
    Color(0xFF1E88E5), // A# – blue
    Color(0xFF00ACC1), // B  – teal
)

@Composable
private fun FretboardDiagram(
    highlightedNotes: Set<Int>,   // note indices (0-11) to show dots for
    showAllLabels: Boolean,        // true = label every dot with its note name
    firstFret: Int,
    lastFret: Int,
    rootNoteIndex: Int? = null,    // when non-null, this note index always renders in AncientGold
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimary    = MaterialTheme.colorScheme.onPrimary
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val hasOpenString = firstFret == 0
    val numFretSlots = lastFret - (if (hasOpenString) 0 else firstFret - 1)
    val totalSlots   = if (hasOpenString) numFretSlots + 1 else numFretSlots

    fun fretToSlot(fret: Int) = if (hasOpenString) fret else fret - firstFret

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val numStrings = 6
        val labelW    = 20.dp.toPx()
        val fretNumH  = 22.dp.toPx()
        val nutW      = if (hasOpenString) 6.dp.toPx() else 0f
        val dotRadius = 9.dp.toPx()

        val boardLeft   = labelW + 4.dp.toPx()
        val boardRight  = size.width
        val boardTop    = dotRadius + 4.dp.toPx()
        val boardBottom = size.height - fretNumH

        val slotW         = (boardRight - boardLeft - nutW) / totalSlots
        val stringSpacing = (boardBottom - boardTop) / (numStrings - 1)

        fun slotX(s: Int) = boardLeft + nutW + slotW * (s + 0.5f)

        // ── Strings ──────────────────────────────────────────────────────────
        FB_STRING_LABELS.forEachIndexed { si, label ->
            val y = boardTop + stringSpacing * si
            val strokePx = when (si) {
                5 -> 3.dp.toPx(); 4 -> 2.5.dp.toPx(); 3 -> 2.dp.toPx()
                else -> 1.5.dp.toPx()
            }
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.55f),
                start       = Offset(boardLeft, y),
                end         = Offset(boardRight, y),
                strokeWidth = strokePx
            )
            val lm = textMeasurer.measure(label, TextStyle(fontSize = 11.sp, color = onSurfaceVar))
            drawText(lm, topLeft = Offset(0f, y - lm.size.height / 2f))
        }

        // ── Nut ──────────────────────────────────────────────────────────────
        if (hasOpenString) {
            drawLine(
                color       = onSurface,
                start       = Offset(boardLeft + nutW / 2, boardTop - 4.dp.toPx()),
                end         = Offset(boardLeft + nutW / 2, boardBottom + 4.dp.toPx()),
                strokeWidth = nutW,
                cap         = StrokeCap.Square
            )
        }

        // ── Fret wires ───────────────────────────────────────────────────────
        if (!hasOpenString) {
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.4f),
                start       = Offset(boardLeft, boardTop),
                end         = Offset(boardLeft, boardBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        for (i in 1..numFretSlots) {
            val x = boardLeft + nutW + slotW * i
            drawLine(
                color       = onSurfaceVar.copy(alpha = 0.4f),
                start       = Offset(x, boardTop),
                end         = Offset(x, boardBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // ── Inlay markers ────────────────────────────────────────────────────
        val markerY = boardTop + stringSpacing * 2.5f
        for (fret in firstFret..lastFret) {
            if (fret == 0) continue
            val mod = fret % 12
            val slot = fretToSlot(fret)
            when {
                mod in INLAY_SINGLE -> drawCircle(
                    color  = onSurfaceVar.copy(alpha = 0.25f),
                    radius = 5.dp.toPx(),
                    center = Offset(slotX(slot), markerY)
                )
                mod == 0 -> {
                    drawCircle(onSurfaceVar.copy(alpha = 0.25f), 4.dp.toPx(),
                        Offset(slotX(slot), boardTop + stringSpacing * 1.5f))
                    drawCircle(onSurfaceVar.copy(alpha = 0.25f), 4.dp.toPx(),
                        Offset(slotX(slot), boardTop + stringSpacing * 3.5f))
                }
            }
        }

        // ── Fret numbers ─────────────────────────────────────────────────────
        val labelFrets = buildSet {
            add(firstFret); add(lastFret)
            for (fret in firstFret..lastFret) {
                if (fret == 0) continue
                val mod = fret % 12
                if (mod in INLAY_SINGLE || mod == 0) add(fret)
            }
        }
        for (fret in labelFrets) {
            val slot = fretToSlot(fret)
            val lm = textMeasurer.measure(
                fret.toString(),
                TextStyle(fontSize = 10.sp, color = onSurfaceVar.copy(alpha = 0.6f))
            )
            drawText(lm, topLeft = Offset(slotX(slot) - lm.size.width / 2f, boardBottom + 2.dp.toPx()))
        }

        // ── Note dots ────────────────────────────────────────────────────────
        FB_OPEN_NOTES.forEachIndexed { si, openNote ->
            val y = boardTop + stringSpacing * si
            for (fret in firstFret..lastFret) {
                val noteAtFret = (openNote + fret) % 12
                if (noteAtFret in highlightedNotes) {
                    val slot = fretToSlot(fret)
                    val x    = slotX(slot)
                    // Color priority:
                    //   1. Root note (when a scale is active) → AncientGold so it always stands out
                    //   2. Scale-mode non-root notes           → per-note rainbow palette
                    //   3. All-notes mode (no scale)           → per-note palette for easy ID
                    //   4. Single-note mode (no scale)         → theme primary
                    val dotColor = when {
                        rootNoteIndex != null && noteAtFret == rootNoteIndex -> AncientGold
                        rootNoteIndex != null                                -> NOTE_COLORS[noteAtFret]
                        showAllLabels                                        -> NOTE_COLORS[noteAtFret]
                        else                                                 -> primaryColor
                    }
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                    val label = FB_NOTE_NAMES[noteAtFret]
                    val nm = textMeasurer.measure(
                        label,
                        TextStyle(fontSize = 7.sp, color = onPrimary, fontWeight = FontWeight.Bold)
                    )
                    drawText(nm, topLeft = Offset(x - nm.size.width / 2f, y - nm.size.height / 2f))
                }
            }
        }
    }
}
