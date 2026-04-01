package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Data model ────────────────────────────────────────────────────────────────

private val NOTE_NAMES         = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val NOTE_DISPLAY_NAMES = listOf("C", "C#/Db", "D", "D#/Eb", "E", "F", "F#/Gb", "G", "G#/Ab", "A", "A#/Bb", "B")
private val GENRES = listOf("Rock", "Blues", "Jazz", "Funk", "Metal", "Country", "Reggae", "Pop", "R&B")

private enum class ChordQuality(val suffix: String) {
    MAJOR(""), MINOR("m"), DOM7("7"), MAJ7("maj7"), MIN7("m7"), DIM("°"), HALF_DIM("ø7")
}

private data class ChordDegree(val semitones: Int, val quality: ChordQuality, val roman: String)
private data class Progression(val name: String, val degrees: List<ChordDegree>)

private enum class Strum(val symbol: String) {
    DOWN("↓"), UP("↑"), MUTED("✕"), REST("")
}
private data class RhythmPattern(val name: String, val timeSig: String, val beats: List<Strum>)

// Shorthand for building beat lists
private fun beats(vararg s: Strum) = s.toList()
private val D = Strum.DOWN; private val U = Strum.UP
private val X = Strum.MUTED; private val R = Strum.REST

// Subdivision labels
private val LABELS_4_4 = listOf("1","e","&","a","2","e","&","a","3","e","&","a","4","e","&","a")
private val LABELS_6_8 = listOf("1","&","a","2","&","a")
// Positions that are strong beats (shown bolder)
private val ACCENTS_4_4 = setOf(0, 4, 8, 12)
private val ACCENTS_6_8 = setOf(0, 3)

private data class GenreData(
    val progressions: List<Progression>,
    val scales: List<String>,
    val arpeggios: List<String>,
    val rhythms: List<RhythmPattern>
)

private fun chordName(rootIndex: Int, degree: ChordDegree) =
    NOTE_NAMES[(rootIndex + degree.semitones) % 12] + degree.quality.suffix

private fun relativeMinor(rootIndex: Int) = NOTE_NAMES[(rootIndex + 9) % 12] + " minor"
private fun relativeMajor(rootIndex: Int) = NOTE_NAMES[(rootIndex + 3) % 12] + " major"

// T T S T T T S  →  0 2 4 5 7 9 11
private val MAJOR_INTERVALS = listOf(0, 2, 4, 5, 7, 9, 11)
// T S T T S T T  →  0 2 3 5 7 8 10
private val MINOR_INTERVALS = listOf(0, 2, 3, 5, 7, 8, 10)

// Circle of Fifths: 12 positions clockwise from top (C at top)
// Major notes at each position: C G D A E B F# Db Ab Eb Bb F
private val COF_MAJOR_NOTES   = listOf(0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10, 5)
// Minor notes at each position (relative minor of the major at same position)
private val COF_MINOR_NOTES   = listOf(9, 4, 11, 6, 1, 8, 3, 10, 5, 0, 7, 2)
// Display names for the CoF (using conventional flat spellings on the flat side)
private val COF_MAJOR_DISPLAY = listOf("C","G","D","A","E","B","F#","Db","Ab","Eb","Bb","F")
private val COF_MINOR_DISPLAY = listOf("Am","Em","Bm","F#m","C#m","G#m","D#m","Bbm","Fm","Cm","Gm","Dm")

// Triad types: formula, intervals (semitones from root), sound description
private data class TriadType(
    val name: String,
    val suffix: String,
    val formula: String,
    val intervals: List<Int>,
    val intervalStructure: String,
    val sound: String
)
private val TRIAD_TYPES = listOf(
    TriadType("Major",      "",  "1 – 3 – 5",   listOf(0, 4, 7), "Major 3rd + Minor 3rd", "Bright, stable"),
    TriadType("Minor",      "m", "1 – ♭3 – 5",  listOf(0, 3, 7), "Minor 3rd + Major 3rd", "Dark, melancholic"),
    TriadType("Diminished", "°", "1 – ♭3 – ♭5", listOf(0, 3, 6), "Minor 3rd + Minor 3rd", "Tense, unstable"),
    TriadType("Augmented",  "+", "1 – 3 – ♯5",  listOf(0, 4, 8), "Major 3rd + Major 3rd", "Dreamy, unresolved"),
)

// Triad string sets for shape computation (strings ordered low → high pitch)
// openNotes: semitone index 0-11; openPitches: relative MIDI (E2=40 base)
private data class StringSet(
    val name: String,
    val labels: List<String>,   // [low, mid, high]
    val openNotes: List<Int>,   // [low, mid, high] note index 0-11
    val openPitches: List<Int>  // [low, mid, high] absolute pitch for octave ordering
)
private val TRIAD_STRING_SETS = listOf(
    StringSet("G · B · e", listOf("G","B","e"), listOf(7,11,4), listOf(55,59,64)),
    StringSet("D · G · B", listOf("D","G","B"), listOf(2,7,11), listOf(50,55,59)),
    StringSet("A · D · G", listOf("A","D","G"), listOf(9,2,7),  listOf(45,50,55)),
)

// Computes fret positions for all 3 inversions of a triad on a given string set.
// For each string, tries both the base fret and base+12, then picks the combination
// with the smallest span that maintains ascending pitch order.
// Returns list of Pair<frets [low,mid,high], inversionLabel>
private fun triadShapes(
    noteIndices: List<Int>,  // [root, 3rd, 5th] as note indices 0-11
    ss: StringSet
): List<Pair<List<Int>, String>> {
    val inversions = listOf(
        listOf(noteIndices[0], noteIndices[1], noteIndices[2]) to "Root",
        listOf(noteIndices[1], noteIndices[2], noteIndices[0]) to "1st",
        listOf(noteIndices[2], noteIndices[0], noteIndices[1]) to "2nd"
    )
    return inversions.map { (order, label) ->
        // Each string can play the note at its lowest fret (base) or one octave up (base+12)
        val candidates = order.mapIndexed { i, note ->
            val base = (note - ss.openNotes[i] + 12) % 12
            listOf(base, base + 12)
        }

        // Try all 8 combinations; pick the most compact shape with ascending pitch order
        var bestFrets = candidates.map { it[1] }
        var bestSpan  = Int.MAX_VALUE
        var bestMin   = Int.MAX_VALUE

        for (c0 in candidates[0]) {
            for (c1 in candidates[1]) {
                for (c2 in candidates[2]) {
                    val p0 = ss.openPitches[0] + c0
                    val p1 = ss.openPitches[1] + c1
                    val p2 = ss.openPitches[2] + c2
                    if (p0 <= p1 && p1 <= p2) {
                        val span = maxOf(c0, c1, c2) - minOf(c0, c1, c2)
                        val minF = minOf(c0, c1, c2)
                        if (span < bestSpan || (span == bestSpan && minF < bestMin)) {
                            bestSpan  = span
                            bestMin   = minF
                            bestFrets = listOf(c0, c1, c2)
                        }
                    }
                }
            }
        }

        bestFrets to label
    }
}

// CAGED shapes: name → open-chord root semitone (from C)
private data class CagedShape(val name: String, val openRoot: Int, val rootString: String)
private val CAGED_SHAPES = listOf(
    CagedShape("C", 0,  "Root on 5th string"),
    CagedShape("A", 9,  "Root on 5th string"),
    CagedShape("G", 7,  "Root on 6th string"),
    CagedShape("E", 4,  "Root on 6th string"),
    CagedShape("D", 2,  "Root on 4th string"),
)

// Capo: open-chord shapes and their root semitone from C
private val OPEN_SHAPES = listOf("C" to 0, "D" to 2, "E" to 4, "G" to 7, "A" to 9)

// Returns list of (shape, capoFret) sorted by capo fret, capped at fret 7
private fun capoSuggestions(rootIndex: Int): List<Pair<String, Int>> =
    OPEN_SHAPES.mapNotNull { (shape, shapeRoot) ->
        val capo = (rootIndex - shapeRoot + 12) % 12
        if (capo <= 7) shape to capo else null
    }.sortedBy { it.second }

// Interval table: symbol, name, semitones
private val INTERVAL_DATA = listOf(
    Triple("P1", "Unison",      0),
    Triple("m2", "Min 2nd",     1),
    Triple("M2", "Maj 2nd",     2),
    Triple("m3", "Min 3rd",     3),
    Triple("M3", "Maj 3rd",     4),
    Triple("P4", "Perf 4th",    5),
    Triple("TT", "Tritone",     6),
    Triple("P5", "Perf 5th",    7),
    Triple("m6", "Min 6th",     8),
    Triple("M6", "Maj 6th",     9),
    Triple("m7", "Min 7th",    10),
    Triple("M7", "Maj 7th",    11)
)

// Chord extensions with verified guitar-chords.org.uk URL slugs
private val EXTENSION_DATA = listOf(
    "sus2"          to "sus2",
    "sus4"          to "sus4",
    "maj9"          to "major9",
    "dom9 (9)"      to "dominant9",
    "min9 (m9)"     to "minor9",
    "dom11 (11)"    to "dominant11",
    "dom13 (13)"    to "dominant13"
)

private fun extensionPageUrl(note: String, slug: String) =
    "$GUITAR_CHORDS_BASE/${noteToSlug(note)}-$slug-chord.html"

private fun scaleNotes(rootIndex: Int, isMajor: Boolean): List<String> =
    (if (isMajor) MAJOR_INTERVALS else MINOR_INTERVALS)
        .map { NOTE_DISPLAY_NAMES[(rootIndex + it) % 12] }

// ── guitar-chords.org.uk URL helpers ─────────────────────────────────────────

private const val GUITAR_CHORDS_BASE = "https://www.guitar-chords.org.uk"

private fun googleSearchUrl(note: String, suffix: String) =
    "https://www.google.com/search?q=${(note + " " + suffix).replace(" ", "+")}"

// Chord slug (x-sharp format, used by chords section)
private fun noteToSlug(note: String): String = when (note) {
    "C"  -> "c";   "C#" -> "c-sharp"
    "D"  -> "d";   "D#" -> "d-sharp"
    "E"  -> "e";   "F"  -> "f"
    "F#" -> "f-sharp"; "G" -> "g"
    "G#" -> "g-sharp"; "A" -> "a"
    "A#" -> "a-sharp"; "B" -> "b"
    else -> note.lowercase()
}

// Major / natural minor / harmonic minor scales + arpeggios:
//   D# = e-flat, G# = a-flat, A# = b-flat
// (verified from site directory listing)
private fun noteToMajMinScaleSlug(note: String): String = when (note) {
    "D#" -> "e-flat"; "G#" -> "a-flat"; "A#" -> "b-flat"
    else -> noteToSlug(note)
}

// Pentatonic + blues scales:
//   D# = e-flat, G# = g-sharp, A# = b-flat
// (G# uses g-sharp here, not a-flat — verified from site directory listing)
private fun noteToPentaBluesSlug(note: String): String = when (note) {
    "D#" -> "e-flat"; "A#" -> "b-flat"
    else -> noteToSlug(note) // G# → "g-sharp" naturally
}

// Mode URLs vary per mode — the site uses different enharmonic slugs
// depending on which mode pages it actually created. Derived from the
// full directory listing of guitar-chords.org.uk/modes/.
private fun modeUrl(note: String, mode: String): String {
    val slug: String? = when (mode) {
        "dorian" -> when (note) {
            "C" -> "c"; "C#" -> "csharp"; "D" -> "d"; "D#" -> "eflat"
            "E" -> "e"; "F" -> "f"; "F#" -> "fsharp"; "G" -> "g"
            "G#" -> "gsharp"; "A" -> "a"; "A#" -> "bflat"; "B" -> "b"
            else -> null
        }
        "mixolydian" -> when (note) {
            "C" -> "c"; "C#" -> "csharp"; "D" -> "d"; "D#" -> "eflat"
            "E" -> "e"; "F" -> "f"; "F#" -> "fsharp"; "G" -> "g"
            "G#" -> "aflat"; "A" -> "a"; "A#" -> "bflat"; "B" -> "b"
            else -> null
        }
        "lydian" -> when (note) {
            "C" -> "c"; "C#" -> "dflat"; "D" -> "d"; "D#" -> "eflat"
            "E" -> "e"; "F" -> "f"; "F#" -> "fsharp"; "G" -> "g"
            "G#" -> "aflat"; "A" -> "a"; "A#" -> "bflat"; "B" -> "b"
            else -> null
        }
        "phrygian" -> when (note) {
            // D# uses dsharp (not eflat), G# uses gsharp — both verified present
            "C" -> "c"; "C#" -> "csharp"; "D" -> "d"; "D#" -> "dsharp"
            "E" -> "e"; "F" -> "f"; "F#" -> "fsharp"; "G" -> "g"
            "G#" -> "gsharp"; "A" -> "a"; "A#" -> "bflat"; "B" -> "b"
            else -> null
        }
        else -> null
    }
    return if (slug != null) "$GUITAR_CHORDS_BASE/modes/$slug-$mode-mode.html" else ""
}

private fun qualityToSlug(quality: ChordQuality): String = when (quality) {
    ChordQuality.MAJOR    -> "major"
    ChordQuality.MINOR    -> "minor"
    ChordQuality.DOM7     -> "dominant7"
    ChordQuality.MAJ7     -> "major7"
    ChordQuality.MIN7     -> "minor7"
    ChordQuality.DIM      -> "diminished"
    ChordQuality.HALF_DIM -> "half-diminished"
}

private fun chordPageUrl(note: String, quality: ChordQuality) =
    "$GUITAR_CHORDS_BASE/${noteToSlug(note)}-${qualityToSlug(quality)}-chord.html"

// Diatonic triad quality for a given interval in the selected key.
// Major formula: I ii iii IV V vi vii° (Major, Minor, Minor, Major, Major, Minor, Dim)
// Minor formula: i ii° III iv v VI VII (Minor, Dim, Major, Minor, Minor, Major, Major)
private fun diatonicQuality(semitones: Int, isMajor: Boolean): ChordQuality =
    if (isMajor) when (semitones % 12) {
        0  -> ChordQuality.MAJOR
        2  -> ChordQuality.MINOR
        4  -> ChordQuality.MINOR
        5  -> ChordQuality.MAJOR
        7  -> ChordQuality.MAJOR
        9  -> ChordQuality.MINOR
        11 -> ChordQuality.DIM
        else -> ChordQuality.MAJOR
    } else when (semitones % 12) {
        0  -> ChordQuality.MINOR
        2  -> ChordQuality.DIM
        3  -> ChordQuality.MAJOR
        5  -> ChordQuality.MINOR
        7  -> ChordQuality.MINOR
        8  -> ChordQuality.MAJOR
        10 -> ChordQuality.MAJOR
        else -> ChordQuality.MINOR
    }

// For basic triads (Major/Minor) use the diatonic formula; keep genre-specific
// qualities (DOM7, MAJ7, MIN7, DIM, HALF_DIM) exactly as defined in the progression.
private fun effectiveQuality(degree: ChordDegree, isMajor: Boolean): ChordQuality =
    when (degree.quality) {
        ChordQuality.MAJOR, ChordQuality.MINOR -> diatonicQuality(degree.semitones, isMajor)
        else -> degree.quality
    }

// Dynamic Roman numeral — uses the correct degree name for the selected key so
// Minor mode shows VII/VI/III (natural minor) rather than bVII/bVI/bIII.
private fun dynamicRoman(semitones: Int, quality: ChordQuality, isMajor: Boolean): String {
    val base = if (isMajor) when (semitones % 12) {
        0  -> "I";  1  -> "bII"; 2  -> "II";  3  -> "bIII"; 4  -> "III"
        5  -> "IV"; 6  -> "bV";  7  -> "V";   8  -> "bVI";  9  -> "VI"
        10 -> "bVII"; 11 -> "VII"; else -> "?"
    } else when (semitones % 12) {
        0  -> "I";  1  -> "bII"; 2  -> "II";  3  -> "III";  4  -> "bIV"
        5  -> "IV"; 6  -> "bV";  7  -> "V";   8  -> "VI";   9  -> "bVII"
        10 -> "VII"; 11 -> "VII#"; else -> "?"
    }
    return when (quality) {
        ChordQuality.MAJOR    -> base
        ChordQuality.MINOR    -> base.lowercase()
        ChordQuality.DOM7     -> "${base}7"
        ChordQuality.MAJ7     -> "${base}maj7"
        ChordQuality.MIN7     -> "${base.lowercase()}7"
        ChordQuality.DIM      -> "${base.lowercase()}°"
        ChordQuality.HALF_DIM -> "${base.lowercase()}ø7"
    }
}

private fun itemKey(item: String) =
    item.substringBefore("(").substringBefore("  ").trim()

private fun scalePageUrl(rootNote: String, item: String): String {
    val key = itemKey(item)
    return when {
        key.startsWith("Pentatonic Minor") ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToPentaBluesSlug(rootNote)}-minorpentatonic.html"
        key.startsWith("Pentatonic Major") ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToPentaBluesSlug(rootNote)}-majorpentatonic.html"
        key.startsWith("Blues")            ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToPentaBluesSlug(rootNote)}-bluesscale.html"
        key.startsWith("Natural Minor")    ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToMajMinScaleSlug(rootNote)}-natural-minor-scale.html"
        key.startsWith("Harmonic Minor")   ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToMajMinScaleSlug(rootNote)}-harmonic-minor-scale.html"
        key.startsWith("Major")            ->
            "$GUITAR_CHORDS_BASE/guitarscales/${noteToMajMinScaleSlug(rootNote)}-major-scale.html"
        key.startsWith("Mixolydian")       -> modeUrl(rootNote, "mixolydian")
        key.startsWith("Dorian")           -> modeUrl(rootNote, "dorian")
        key.startsWith("Lydian")           -> modeUrl(rootNote, "lydian")
        key.startsWith("Phrygian")         -> modeUrl(rootNote, "phrygian")
        else -> return googleSearchUrl(rootNote, "${key.lowercase()} scale guitar")
    }
}

private fun arpeggioPageUrl(rootNote: String, item: String): String {
    val key = itemKey(item)
    val n = noteToMajMinScaleSlug(rootNote)
    val slug = when {
        key.startsWith("Major Triad")     -> "major"
        key.startsWith("Minor Triad")     -> "minor"
        key.startsWith("Dominant 7th")    -> "7"
        key.startsWith("Minor 7th")       -> "minor7"
        key.startsWith("Major 7th")       -> "major7"
        key.startsWith("Diminished")      -> "diminished"
        else -> return googleSearchUrl(rootNote, "${key.lowercase()} arpeggio guitar")
    }
    return "$GUITAR_CHORDS_BASE/arpeggios/$n-$slug-arpeggios.html"
}

// ── Genre data ────────────────────────────────────────────────────────────────

private val GENRE_DATA = mapOf(
    "Rock" to GenreData(
        progressions = listOf(
            Progression("Classic Rock", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(5, ChordQuality.MAJOR, "IV"),
                ChordDegree(7, ChordQuality.MAJOR, "V")
            )),
            Progression("Anthem  (I – V – vi – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            )),
            Progression("Minor Rock  (i – bVII – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Grunge  (I – bVII – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            ))
        ),
        scales = listOf("Pentatonic Minor", "Blues Scale", "Natural Minor (Aeolian)", "Major (Ionian)"),
        arpeggios = listOf(
            "Major Triad  (1 – 3 – 5)",
            "Minor Triad  (1 – b3 – 5)",
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Power Chord  (1 – 5)"
        ),
        rhythms = listOf(
            RhythmPattern("Classic Rock", "4/4",
                beats(D,R,R,R, D,R,U,R, R,R,U,R, D,R,U,R)),
            RhythmPattern("Power Strokes", "4/4",
                beats(D,R,R,R, D,R,R,R, D,R,R,R, D,R,R,R)),
            RhythmPattern("Straight 8ths", "4/4",
                beats(D,R,U,R, D,R,U,R, D,R,U,R, D,R,U,R)),
            RhythmPattern("Punk Rock", "4/4",
                beats(D,R,D,R, D,R,D,R, D,R,D,R, D,R,D,R)),
            RhythmPattern("Rock Ballad", "6/8",
                beats(D,R,U, D,U,U))
        )
    ),
    "Blues" to GenreData(
        progressions = listOf(
            Progression("12-Bar Blues  (I7 – IV7 – V7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Minor Blues  (i7 – iv7 – V7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.MIN7, "iv7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Slow Blues  (I7 – IV7 – I7 – V7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("Texas Shuffle  (I7 – IV7 – I7 – V7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            ))
        ),
        scales = listOf("Blues Scale  (1 – b3 – 4 – b5 – 5 – b7)", "Pentatonic Minor", "Mixolydian", "Dorian"),
        arpeggios = listOf(
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Major 6th  (1 – 3 – 5 – 6)",
            "Diminished  (1 – b3 – b5)",
            "Major Triad  (1 – 3 – 5)"
        ),
        rhythms = listOf(
            RhythmPattern("Shuffle", "4/4",
                beats(D,R,U,R, D,R,U,R, D,R,U,R, D,R,U,R)),
            RhythmPattern("12-Bar Accent", "4/4",
                beats(D,R,R,R, R,R,U,R, D,R,R,R, R,R,U,R)),
            RhythmPattern("Slow Blues Ballad", "6/8",
                beats(D,R,U, D,U,R)),
            RhythmPattern("Jump Blues", "4/4",
                beats(D,R,U,R, D,U,R,U, D,R,U,R, D,U,R,U)),
            RhythmPattern("Slow Drag", "4/4",
                beats(D,R,R,R, D,R,R,U, R,R,D,R, D,R,U,R))
        )
    ),
    "Jazz" to GenreData(
        progressions = listOf(
            Progression("ii – V – I", listOf(
                ChordDegree(2, ChordQuality.MIN7, "ii7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7")
            )),
            Progression("Turnaround  (I – vi – ii – V)", listOf(
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7"),
                ChordDegree(9, ChordQuality.MIN7, "vi7"),
                ChordDegree(2, ChordQuality.MIN7, "ii7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            )),
            Progression("Rhythm Changes  (I – IV – iii – VI)", listOf(
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(4, ChordQuality.MIN7, "iii7"),
                ChordDegree(9, ChordQuality.DOM7, "VI7")
            )),
            Progression("Minor ii – V – i", listOf(
                ChordDegree(2, ChordQuality.HALF_DIM, "iiø7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(0, ChordQuality.MIN7, "im7")
            ))
        ),
        scales = listOf("Dorian", "Mixolydian", "Major (Ionian)", "Lydian", "Bebop Major"),
        arpeggios = listOf(
            "Major 7th  (1 – 3 – 5 – 7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Half-diminished  (1 – b3 – b5 – b7)",
            "Diminished  (1 – b3 – b5)",
            "Major 6th  (1 – 3 – 5 – 6)"
        ),
        rhythms = listOf(
            RhythmPattern("Freddie Green", "4/4",
                beats(D,R,R,R, D,R,R,R, D,R,R,R, D,R,R,R)),
            RhythmPattern("Bossa Nova", "4/4",
                beats(D,R,R,U, R,U,D,R, D,R,R,U, R,U,D,R)),
            RhythmPattern("Jazz Waltz", "6/8",
                beats(D,R,U, R,U,R)),
            RhythmPattern("Swing Comp", "4/4",
                beats(D,R,R,R, R,R,U,R, D,R,R,R, R,R,U,R)),
            RhythmPattern("Latin Jazz", "4/4",
                beats(D,R,U,R, R,D,R,U, D,R,R,U, R,D,R,U))
        )
    ),
    "Funk" to GenreData(
        progressions = listOf(
            Progression("Two-Chord Groove  (I7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("Minor Funk  (i7 – IV7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7")
            )),
            Progression("James Brown  (I7 – bVII7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(10, ChordQuality.DOM7, "bVII7")
            )),
            Progression("Dorian Vamp  (i7 – iv7)", listOf(
                ChordDegree(0, ChordQuality.MIN7, "i7"),
                ChordDegree(5, ChordQuality.MIN7, "iv7")
            ))
        ),
        scales = listOf("Pentatonic Minor", "Dorian", "Mixolydian"),
        arpeggios = listOf(
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Major 7th  (1 – 3 – 5 – 7)",
            "9th Chord  (1 – 3 – 5 – b7 – 9)",
            "Minor Triad  (1 – b3 – 5)"
        ),
        rhythms = listOf(
            RhythmPattern("16th Groove", "4/4",
                beats(D,R,U,U, D,R,U,R, D,U,R,U, D,R,U,R)),
            RhythmPattern("Ghost Notes", "4/4",
                beats(D,R,X,U, D,R,X,R, D,U,X,U, D,R,X,U)),
            RhythmPattern("Two-Chord Vamp", "4/4",
                beats(D,R,R,U, R,U,R,U, D,R,R,U, R,U,R,U)),
            RhythmPattern("Clavinet", "4/4",
                beats(X,D,R,U, X,D,R,U, X,D,R,U, X,D,R,U)),
            RhythmPattern("Syncopated Stab", "4/4",
                beats(R,D,R,U, D,R,U,D, R,U,D,R, U,D,R,U))
        )
    ),
    "Metal" to GenreData(
        progressions = listOf(
            Progression("Power Progression  (i – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Tritone / Dark  (i – bII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(1, ChordQuality.MAJOR, "bII")
            )),
            Progression("Epic Minor  (i – iv – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(5, ChordQuality.MINOR, "iv"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            )),
            Progression("Thrash  (i – bVII – bVI – bVII)", listOf(
                ChordDegree(0, ChordQuality.MINOR, "i"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII"),
                ChordDegree(8, ChordQuality.MAJOR, "bVI"),
                ChordDegree(10, ChordQuality.MAJOR, "bVII")
            ))
        ),
        scales = listOf("Natural Minor (Aeolian)", "Phrygian", "Harmonic Minor", "Pentatonic Minor", "Diminished"),
        arpeggios = listOf(
            "Minor Triad  (1 – b3 – 5)",
            "Diminished  (1 – b3 – b5)",
            "Power Chord  (1 – 5)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Half-diminished  (1 – b3 – b5 – b7)",
            "Major Triad  (1 – 3 – 5)"
        ),
        rhythms = listOf(
            RhythmPattern("Gallop", "4/4",
                beats(D,R,D,U, D,R,D,U, D,R,D,U, D,R,D,U)),
            RhythmPattern("16th Chug", "4/4",
                beats(D,D,D,D, D,D,D,D, D,D,D,D, D,D,D,D)),
            RhythmPattern("Syncopated Riff", "4/4",
                beats(D,R,R,U, D,D,R,U, D,R,R,U, D,D,R,R)),
            RhythmPattern("Palm Mute Groove", "4/4",
                beats(X,X,X,D, X,X,D,X, X,X,X,D, D,D,D,R)),
            RhythmPattern("Half-Time Breakdown", "4/4",
                beats(D,R,R,R, R,R,R,R, D,R,R,U, D,R,R,R))
        )
    ),
    "Country" to GenreData(
        progressions = listOf(
            Progression("Classic Country  (I – IV – V)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(5, ChordQuality.MAJOR, "IV"),
                ChordDegree(7, ChordQuality.MAJOR, "V")
            )),
            Progression("Modern Country  (I – V – vi – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            )),
            Progression("Western Swing  (I – VI7 – II7 – V7)", listOf(
                ChordDegree(0, ChordQuality.MAJ7,  "Imaj7"),
                ChordDegree(9, ChordQuality.DOM7,  "VI7"),
                ChordDegree(2, ChordQuality.DOM7,  "II7"),
                ChordDegree(7, ChordQuality.DOM7,  "V7")
            ))
        ),
        scales    = listOf("Pentatonic Major", "Major (Ionian)", "Mixolydian", "Harmonic Minor"),
        arpeggios = listOf(
            "Major Triad  (1 – 3 – 5)",
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Major 7th  (1 – 3 – 5 – 7)",
            "Major 6th  (1 – 3 – 5 – 6)",
            "Minor Triad  (1 – b3 – 5)"
        ),
        rhythms = listOf(
            RhythmPattern("Boom-Chick", "4/4",
                beats(D,R,R,R, U,R,R,R, D,R,R,R, U,R,R,R)),
            RhythmPattern("Country Shuffle", "4/4",
                beats(D,R,U,R, D,R,U,R, D,R,U,R, D,R,U,R)),
            RhythmPattern("Train Beat", "4/4",
                beats(D,R,R,U, D,R,U,R, D,R,R,U, D,R,U,R)),
            RhythmPattern("Country Waltz", "6/8",
                beats(D,R,U, D,R,U)),
            RhythmPattern("Chicken Pickin'", "4/4",
                beats(D,R,U,U, D,R,U,R, D,U,U,R, D,R,U,U))
        )
    ),
    "Reggae" to GenreData(
        progressions = listOf(
            Progression("Minor Riddim  (i – VII – VI – VII)", listOf(
                ChordDegree(0,  ChordQuality.MINOR, "i"),
                ChordDegree(10, ChordQuality.MAJOR, "VII"),
                ChordDegree(8,  ChordQuality.MAJOR, "VI"),
                ChordDegree(10, ChordQuality.MAJOR, "VII")
            )),
            Progression("Roots  (I – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            )),
            Progression("Lover's Rock  (I – V – vi – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            ))
        ),
        scales    = listOf("Natural Minor (Aeolian)", "Dorian", "Pentatonic Minor", "Major (Ionian)"),
        arpeggios = listOf(
            "Minor Triad  (1 – b3 – 5)",
            "Major Triad  (1 – 3 – 5)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Major 7th  (1 – 3 – 5 – 7)",
            "Dominant 7th  (1 – 3 – 5 – b7)"
        ),
        rhythms = listOf(
            RhythmPattern("One Drop Skank", "4/4",
                beats(R,R,U,R, R,R,U,R, R,R,U,R, R,R,U,R)),
            RhythmPattern("Rockers", "4/4",
                beats(R,R,U,R, D,R,U,R, R,R,U,R, D,R,U,R)),
            RhythmPattern("Ska Upstroke", "4/4",
                beats(D,R,U,R, R,R,U,R, R,R,U,R, R,R,U,R)),
            RhythmPattern("Bubble", "4/4",
                beats(R,U,R,U, R,U,R,U, R,U,R,U, R,U,R,U)),
            RhythmPattern("Dancehall", "4/4",
                beats(R,R,U,U, D,R,U,R, R,R,U,U, D,R,U,R))
        )
    ),
    "Pop" to GenreData(
        progressions = listOf(
            Progression("4 Chords  (I – V – vi – IV)", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV")
            )),
            Progression("Minor Start  (vi – IV – I – V)", listOf(
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(5, ChordQuality.MAJOR, "IV"),
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(7, ChordQuality.MAJOR, "V")
            )),
            Progression("I – IV – vi – V", listOf(
                ChordDegree(0, ChordQuality.MAJOR, "I"),
                ChordDegree(5, ChordQuality.MAJOR, "IV"),
                ChordDegree(9, ChordQuality.MINOR, "vi"),
                ChordDegree(7, ChordQuality.MAJOR, "V")
            ))
        ),
        scales    = listOf("Major (Ionian)", "Natural Minor (Aeolian)", "Pentatonic Major", "Pentatonic Minor"),
        arpeggios = listOf(
            "Major Triad  (1 – 3 – 5)",
            "Minor Triad  (1 – b3 – 5)",
            "Major 7th  (1 – 3 – 5 – 7)",
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Dominant 7th  (1 – 3 – 5 – b7)"
        ),
        rhythms = listOf(
            RhythmPattern("Pop Strum", "4/4",
                beats(D,R,R,R, D,R,U,R, R,U,D,R, R,U,D,R)),
            RhythmPattern("Upbeat Pop", "4/4",
                beats(D,R,U,U, D,R,U,R, R,U,D,R, U,R,U,R)),
            RhythmPattern("Pop Ballad", "6/8",
                beats(D,U,U, D,U,U)),
            RhythmPattern("Teen Pop", "4/4",
                beats(D,R,U,R, D,U,R,U, D,R,U,R, D,U,R,U)),
            RhythmPattern("Modern Syncopated", "4/4",
                beats(D,R,R,U, D,U,R,D, R,U,D,R, U,R,D,R))
        )
    ),
    "R&B" to GenreData(
        progressions = listOf(
            Progression("Soul  (ii7 – V7 – Imaj7)", listOf(
                ChordDegree(2, ChordQuality.MIN7, "ii7"),
                ChordDegree(7, ChordQuality.DOM7, "V7"),
                ChordDegree(0, ChordQuality.MAJ7, "Imaj7")
            )),
            Progression("Neo-Soul  (i7 – VII – VI – V7)", listOf(
                ChordDegree(0,  ChordQuality.MIN7, "i7"),
                ChordDegree(10, ChordQuality.MAJOR, "VII"),
                ChordDegree(8,  ChordQuality.MAJOR, "VI"),
                ChordDegree(7,  ChordQuality.DOM7,  "V7")
            )),
            Progression("Classic Soul  (I7 – IV7 – V7)", listOf(
                ChordDegree(0, ChordQuality.DOM7, "I7"),
                ChordDegree(5, ChordQuality.DOM7, "IV7"),
                ChordDegree(7, ChordQuality.DOM7, "V7")
            ))
        ),
        scales    = listOf("Pentatonic Minor", "Dorian", "Natural Minor (Aeolian)", "Major (Ionian)"),
        arpeggios = listOf(
            "Minor 7th  (1 – b3 – 5 – b7)",
            "Dominant 7th  (1 – 3 – 5 – b7)",
            "Major 7th  (1 – 3 – 5 – 7)",
            "Major 6th  (1 – 3 – 5 – 6)",
            "9th Chord  (1 – 3 – 5 – b7 – 9)"
        ),
        rhythms = listOf(
            RhythmPattern("Soul Groove", "4/4",
                beats(D,R,X,U, D,R,X,R, D,U,X,U, D,R,X,U)),
            RhythmPattern("Neo-Soul 16ths", "4/4",
                beats(D,R,U,X, R,U,D,R, X,U,R,U, D,R,X,U)),
            RhythmPattern("R&B Ballad", "6/8",
                beats(D,R,U, D,U,R)),
            RhythmPattern("Smooth R&B", "4/4",
                beats(D,R,U,R, R,U,D,R, R,R,U,R, D,R,U,R)),
            RhythmPattern("Church Groove", "4/4",
                beats(D,R,U,U, R,D,R,U, D,R,U,R, R,U,D,R))
        )
    )
)

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
fun SuggesterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cadence_prefs", Context.MODE_PRIVATE) }

    var selectedGenre by remember { mutableStateOf(prefs.getString("suggester_genre", "Rock") ?: "Rock") }
    var selectedKey by remember { mutableStateOf(prefs.getString("suggester_key", "C") ?: "C") }
    var isMajor by remember { mutableStateOf(prefs.getBoolean("suggester_is_major", true)) }
    val pagerState = rememberPagerState(pageCount = { 9 })
    val selectedTab = pagerState.currentPage
    val scope = rememberCoroutineScope()

    val rootIndex = NOTE_NAMES.indexOf(selectedKey).coerceAtLeast(0)
    val data = GENRE_DATA[selectedGenre] ?: GENRE_DATA["Rock"]!!

    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    // When Major: relative minor is 9 semitones up (C major → A minor)
    // When Minor: relative major is 3 semitones up (A minor → C major)
    val relativeKeyLabel = if (isMajor)
        "Relative minor: ${NOTE_NAMES[(rootIndex + 9) % 12]} minor"
    else
        "Relative major: ${NOTE_NAMES[(rootIndex + 3) % 12]} major"

    val scaleNotesList = scaleNotes(rootIndex, isMajor)

    Column(modifier = modifier.fillMaxSize()) {

        // Genre selector
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text("Genre", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                GENRES.forEach { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = {
                            selectedGenre = genre
                            prefs.edit().putString("suggester_genre", genre).apply()
                        },
                        label = { Text(genre) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // Key + Major/Minor selector
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = isMajor,
                    onClick = {
                        isMajor = true
                        prefs.edit().putBoolean("suggester_is_major", true).apply()
                    },
                    label = { Text("Major") }
                )
                FilterChip(
                    selected = !isMajor,
                    onClick = {
                        isMajor = false
                        prefs.edit().putBoolean("suggester_is_major", false).apply()
                    },
                    label = { Text("Minor") }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    selectedKey = NOTE_NAMES[(rootIndex - 1 + 12) % 12]
                    prefs.edit().putString("suggester_key", selectedKey).apply()
                }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Semitone down")
                }
                IconButton(onClick = {
                    selectedKey = NOTE_NAMES[(rootIndex + 1) % 12]
                    prefs.edit().putString("suggester_key", selectedKey).apply()
                }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Semitone up")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                NOTE_NAMES.forEachIndexed { i, note ->
                    FilterChip(
                        selected = selectedKey == note,
                        onClick = {
                            selectedKey = note
                            prefs.edit().putString("suggester_key", note).apply()
                        },
                        label = { Text(NOTE_DISPLAY_NAMES[i]) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                scaleNotesList.joinToString(" – "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = {
                    if (isMajor) {
                        selectedKey = NOTE_NAMES[(rootIndex + 9) % 12]
                        isMajor = false
                        prefs.edit().putString("suggester_key", selectedKey).putBoolean("suggester_is_major", false).apply()
                    } else {
                        selectedKey = NOTE_NAMES[(rootIndex + 3) % 12]
                        isMajor = true
                        prefs.edit().putString("suggester_key", selectedKey).putBoolean("suggester_is_major", true).apply()
                    }
                }
            ) {
                Text(relativeKeyLabel, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            // Capo helper
            val capos = capoSuggestions(rootIndex)
            if (capos.isNotEmpty()) {
                Text(
                    "Capo shortcuts:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    capos.forEach { (shape, fret) ->
                        val label = if (fret == 0) "$shape (no capo)" else "Capo $fret → $shape shape"
                        SuggestionChip(
                            onClick = {},
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            listOf("Progression", "Scales", "Arpeggios", "Rhythm", "Intervals", "Extensions", "Circle", "CAGED", "Triads").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text     = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> ProgressionsTab(
                    progressions = data.progressions,
                    genre = selectedGenre,
                    rootIndex = rootIndex,
                    rootNote = selectedKey,
                    isMajor = isMajor,
                    favorites = favorites,
                    onToggleFavorite = ::toggleFavorite
                )
                1 -> ItemListTab(data.scales, selectedKey, ::scalePageUrl)
                2 -> ItemListTab(data.arpeggios, selectedKey, ::arpeggioPageUrl)
                3 -> RhythmTab(data.rhythms)
                4 -> IntervalsTab(rootIndex)
                5 -> ExtensionsTab(selectedKey)
                6 -> CircleOfFifthsTab(
                    rootIndex = rootIndex,
                    isMajor = isMajor,
                    onKeyChange = { note, major ->
                        selectedKey = note
                        isMajor = major
                    }
                )
                7 -> CAGEDTab(rootIndex = rootIndex, rootNote = selectedKey)
                8 -> TriadsTab(rootIndex = rootIndex, rootNote = selectedKey, isMajor = isMajor)
            }
        }
    }
}

@Composable
private fun ProgressionsTab(
    progressions: List<Progression>,
    genre: String,
    rootIndex: Int,
    rootNote: String,
    isMajor: Boolean,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Saved progressions section
        if (favorites.isNotEmpty()) {
            Text(
                "Saved",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            favorites.forEach { favKey ->
                val parts = favKey.split("|")
                if (parts.size == 4) {
                    val favGenre = parts[0]
                    val favProgressionName = parts[1]
                    val favRootNote = parts[2]
                    val favIsMajor = parts[3].toBoolean()
                    val favRootIndex = NOTE_NAMES.indexOf(favRootNote)
                    val favProgression = GENRE_DATA[favGenre]?.progressions?.find { it.name == favProgressionName }
                    if (favProgression != null && favRootIndex != -1) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            favProgression.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "$favGenre · $favRootNote ${if (favIsMajor) "major" else "minor"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onToggleFavorite(favKey) }) {
                                        Icon(
                                            Icons.Filled.Bookmark,
                                            contentDescription = "Remove from saved",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    favProgression.degrees.joinToString(" – ") { d ->
                                        NOTE_NAMES[(favRootIndex + d.semitones) % 12] + effectiveQuality(d, favIsMajor).suffix
                                    },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    favProgression.degrees
                                        .distinctBy { d ->
                                            NOTE_NAMES[(favRootIndex + d.semitones) % 12] + effectiveQuality(d, favIsMajor).suffix
                                        }
                                        .forEach { degree ->
                                            val note = NOTE_NAMES[(favRootIndex + degree.semitones) % 12]
                                            val eq   = effectiveQuality(degree, favIsMajor)
                                            val url  = chordPageUrl(note, eq)
                                            TextButton(onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }) {
                                                Text(note + eq.suffix, fontSize = 13.sp)
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        progressions.forEach { progression ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val favKey = "$genre|${progression.name}|$rootNote|$isMajor"
                    val isSaved = favKey in favorites
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            progression.name,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onToggleFavorite(favKey) }) {
                            Icon(
                                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                                contentDescription = if (isSaved) "Remove from saved" else "Save progression",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progression.degrees.joinToString(" – ") { d ->
                            dynamicRoman(d.semitones, effectiveQuality(d, isMajor), isMajor)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progression.degrees.joinToString(" – ") { d ->
                            NOTE_NAMES[(rootIndex + d.semitones) % 12] + effectiveQuality(d, isMajor).suffix
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Chord diagrams:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        progression.degrees
                            .distinctBy { d ->
                                NOTE_NAMES[(rootIndex + d.semitones) % 12] + effectiveQuality(d, isMajor).suffix
                            }
                            .forEach { degree ->
                                val note = NOTE_NAMES[(rootIndex + degree.semitones) % 12]
                                val eq   = effectiveQuality(degree, isMajor)
                                val name = note + eq.suffix
                                val url  = chordPageUrl(note, eq)
                                TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) {
                                    Text(name, fontSize = 13.sp)
                                }
                            }
                    }
                }
            }
        }

        // All diatonic chords in the selected key
        val diatonicSemitones = if (isMajor)
            listOf(0, 2, 4, 5, 7, 9, 11)
        else
            listOf(0, 2, 3, 5, 7, 8, 10)

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "All chords in key",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Build your own progressions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    diatonicSemitones.forEach { semitones ->
                        val quality = diatonicQuality(semitones, isMajor)
                        val note = NOTE_NAMES[(rootIndex + semitones) % 12]
                        val name = note + quality.suffix
                        val roman = dynamicRoman(semitones, quality, isMajor)
                        val url  = chordPageUrl(note, quality)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                roman,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }) {
                                Text(name, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemListTab(
    items: List<String>,
    rootNote: String,
    buildUrl: (rootNote: String, item: String) -> String
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val url = buildUrl(rootNote, item)
                    if (url.isNotEmpty()) {
                        val isSearch = url.contains("google.com")
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Text(if (isSearch) "Search" else "Diagram", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmTab(patterns: List<RhythmPattern>) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple("↓", "Down strum",  MaterialTheme.colorScheme.primary),
                Triple("↑", "Up strum",    MaterialTheme.colorScheme.error),
                Triple("✕", "Muted strum", MaterialTheme.colorScheme.onSurfaceVariant)
            ).forEach { (symbol, label, color) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        patterns.forEach { pattern ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row: name + time signature badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pattern.name,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            pattern.timeSig,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BeatGrid(pattern)
                }
            }
        }
    }
}

@Composable
private fun IntervalsTab(rootIndex: Int) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Intervals from ${NOTE_DISPLAY_NAMES[rootIndex]}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        INTERVAL_DATA.forEach { (symbol, name, semitones) ->
            val resultNote = NOTE_DISPLAY_NAMES[(rootIndex + semitones) % 12]
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Symbol badge
                    Text(
                        symbol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    // Interval name
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Semitone count
                    Text(
                        if (semitones == 1) "1 semitone" else "$semitones semitones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    // Result note
                    Text(
                        resultNote,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionsTab(rootNote: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Chord Extensions for $rootNote",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        EXTENSION_DATA.forEach { (label, slug) ->
            val chordName = "$rootNote${label.substringBefore(" ")}"
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(chordName, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary)
                        if (label.contains("(")) {
                            Text(
                                label.substringAfter("(").trimEnd(')'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = {
                        val url = extensionPageUrl(rootNote, slug)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Text("Diagram", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeatGrid(pattern: RhythmPattern) {
    val labels  = if (pattern.timeSig == "6/8") LABELS_6_8 else LABELS_4_4
    val accents = if (pattern.timeSig == "6/8") ACCENTS_6_8 else ACCENTS_4_4
    val primary = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.error
    val muted   = MaterialTheme.colorScheme.onSurfaceVariant

    // For 4/4 split into two rows of 8; 6/8 is a single row of 6
    val rows = if (pattern.timeSig == "6/8") listOf(pattern.beats.indices.toList())
               else listOf((0..7).toList(), (8..15).toList())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { indices ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                indices.forEach { i ->
                    val strum   = pattern.beats.getOrElse(i) { Strum.REST }
                    val isAccent = i in accents
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Subdivision label
                        Text(
                            labels[i],
                            fontSize = if (isAccent) 11.sp else 10.sp,
                            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAccent) MaterialTheme.colorScheme.onSurface
                                    else muted
                        )
                        Spacer(Modifier.height(4.dp))
                        // Strum arrow
                        Text(
                            strum.symbol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (strum) {
                                Strum.DOWN  -> primary
                                Strum.UP    -> upColor
                                Strum.MUTED -> muted
                                Strum.REST  -> muted.copy(alpha = 0f)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Circle of Fifths ──────────────────────────────────────────────────────────

@Composable
private fun CircleOfFifthsTab(
    rootIndex: Int,
    isMajor: Boolean,
    onKeyChange: (note: String, isMajor: Boolean) -> Unit
) {
    val primary          = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimary        = MaterialTheme.colorScheme.onPrimary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceVariant   = MaterialTheme.colorScheme.surfaceVariant
    val onSurface        = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer     = rememberTextMeasurer()

    // Which COF position is currently highlighted
    val majorHighlightPos = COF_MAJOR_NOTES.indexOf(if (isMajor) rootIndex else (rootIndex + 3) % 12)
    val minorHighlightPos = COF_MINOR_NOTES.indexOf(if (isMajor) (rootIndex + 9) % 12 else rootIndex)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Circle of Fifths",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Tap a key to select it  ·  outer = major  ·  inner = minor",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val r  = sqrt(dx * dx + dy * dy)
                        val outerR = min(cx, cy) * 0.95f
                        val midR   = outerR * 0.62f
                        val innerR = outerR * 0.38f

                        if (r in innerR..outerR) {
                            val angle = atan2(dy.toDouble(), dx.toDouble())
                            // Normalize to [0, 2π) starting from top, clockwise
                            val normalized = ((angle + PI / 2.0 + PI / 12.0 + 2 * PI) % (2 * PI))
                            val pos = (normalized / (PI / 6.0)).toInt() % 12

                            if (r > midR) {
                                // Outer ring → major key
                                onKeyChange(NOTE_NAMES[COF_MAJOR_NOTES[pos]], true)
                            } else {
                                // Inner ring → minor key
                                onKeyChange(NOTE_NAMES[COF_MINOR_NOTES[pos]], false)
                            }
                        }
                    }
                }
        ) {
            val cx     = size.width / 2f
            val cy     = size.height / 2f
            val outerR = min(cx, cy) * 0.95f
            val midR   = outerR * 0.62f
            val innerR = outerR * 0.38f

            for (pos in 0..11) {
                val startAngle = -105f + pos * 30f
                val sweep      = 29f // small gap between segments

                // ── Outer ring: major keys ────────────────────────────────
                val isMajorSel = pos == majorHighlightPos
                val majorFill  = if (isMajorSel) primary else surfaceVariant

                val majorPath = Path().apply {
                    val outerRect = Rect(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
                    val midRect   = Rect(cx - midR,   cy - midR,   cx + midR,   cy + midR)
                    arcTo(outerRect, startAngle, sweep, forceMoveTo = true)
                    arcTo(midRect,   startAngle + sweep, -sweep, forceMoveTo = false)
                    close()
                }
                drawPath(majorPath, majorFill)

                // ── Inner ring: minor keys ────────────────────────────────
                val isMinorSel = pos == minorHighlightPos
                val minorFill  = if (isMinorSel) primaryContainer else surfaceVariant.copy(alpha = 0.6f)

                val minorPath = Path().apply {
                    val midRect   = Rect(cx - midR,   cy - midR,   cx + midR,   cy + midR)
                    val innerRect = Rect(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                    arcTo(midRect,   startAngle, sweep, forceMoveTo = true)
                    arcTo(innerRect, startAngle + sweep, -sweep, forceMoveTo = false)
                    close()
                }
                drawPath(minorPath, minorFill)

                // ── Text labels ───────────────────────────────────────────
                val midAngleRad = (startAngle + sweep / 2f) * (PI / 180.0)

                // Major label (outer ring)
                val majorTextR = (outerR + midR) / 2f
                val majorX = cx + majorTextR * cos(midAngleRad).toFloat()
                val majorY = cy + majorTextR * sin(midAngleRad).toFloat()
                val majorLabel = COF_MAJOR_DISPLAY[pos]
                val majorLm = textMeasurer.measure(
                    majorLabel,
                    TextStyle(
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isMajorSel) onPrimary else onSurface
                    )
                )
                drawText(majorLm, topLeft = Offset(
                    majorX - majorLm.size.width / 2f,
                    majorY - majorLm.size.height / 2f
                ))

                // Minor label (inner ring)
                val minorTextR = (midR + innerR) / 2f
                val minorX = cx + minorTextR * cos(midAngleRad).toFloat()
                val minorY = cy + minorTextR * sin(midAngleRad).toFloat()
                val minorLabel = COF_MINOR_DISPLAY[pos]
                val minorLm = textMeasurer.measure(
                    minorLabel,
                    TextStyle(
                        fontSize = 9.sp,
                        color    = if (isMinorSel) onPrimaryContainer else onSurfaceVariant
                    )
                )
                drawText(minorLm, topLeft = Offset(
                    minorX - minorLm.size.width / 2f,
                    minorY - minorLm.size.height / 2f
                ))
            }
        }
    }
}

// ── CAGED System ─────────────────────────────────────────────────────────────

@Composable
private fun CAGEDTab(rootIndex: Int, rootNote: String) {
    val primary      = MaterialTheme.colorScheme.primary
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    // Compute fret position for each shape and sort by fret ascending
    val positions = CAGED_SHAPES.map { shape ->
        val fret = (rootIndex - shape.openRoot + 12) % 12
        Pair(shape, fret)
    }.sortedBy { (_, fret) -> fret }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "CAGED System — $rootNote",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Five positions that cover the entire neck. Sorted by fret.",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVar
        )
        Spacer(Modifier.height(4.dp))

        positions.forEach { (shape, fret) ->
            Card(
                modifier  = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shape letter badge
                    Text(
                        shape.name,
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color      = primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${rootNote} — ${shape.name} shape",
                            fontWeight = FontWeight.SemiBold,
                            style      = MaterialTheme.typography.bodyLarge,
                            color      = onSurface
                        )
                        val fretLabel = if (fret == 0) "Open position (fret 0)" else "Fret $fret"
                        Text(
                            fretLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = primary
                        )
                        Text(
                            shape.rootString,
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVar
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: Each shape connects to the next up the neck — C→A→G→E→D→C — forming a continuous loop.",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVar
        )
    }
}

// ── Triads ────────────────────────────────────────────────────────────────────

private data class DiatonicTriad(
    val roman: String,
    val triad: TriadType,
    val noteIndex: Int,
    val noteNames: List<String>  // display names for root, 3rd, 5th
)

private fun diatonicTriads(rootIndex: Int, isMajor: Boolean): List<DiatonicTriad> {
    val scale = if (isMajor) MAJOR_INTERVALS else MINOR_INTERVALS
    val romanBases = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    return (0..6).map { i ->
        val noteIndex = (rootIndex + scale[i]) % 12
        // Interval of the 3rd and 5th built from this scale degree
        val thirdSemitones = (scale[(i + 2) % 7] + if (i + 2 >= 7) 12 else 0) - scale[i]
        val fifthSemitones = (scale[(i + 4) % 7] + if (i + 4 >= 7) 12 else 0) - scale[i]

        val triad = TRIAD_TYPES.first {
            it.intervals[1] == thirdSemitones && it.intervals[2] == fifthSemitones
        }

        val base = romanBases[i]
        val roman = when (triad.suffix) {
            ""  -> base
            "m" -> base.lowercase()
            "°" -> base.lowercase() + "°"
            "+" -> base + "+"
            else -> base
        }

        val noteNames = triad.intervals.map { NOTE_DISPLAY_NAMES[(noteIndex + it) % 12] }
        DiatonicTriad(roman, triad, noteIndex, noteNames)
    }
}

@Composable
private fun TriadsTab(rootIndex: Int, rootNote: String, isMajor: Boolean) {
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVar   = MaterialTheme.colorScheme.surfaceVariant

    val keyLabel = "$rootNote ${if (isMajor) "major" else "minor"}"
    val triads   = diatonicTriads(rootIndex, isMajor)

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Triads — $keyLabel",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Each scale degree produces a triad by stacking thirds. Shapes are shown for three string sets in root position, 1st inversion, and 2nd inversion.",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVar
        )
        Spacer(Modifier.height(2.dp))

        // Compact formula reference
        Card(
            modifier  = Modifier.fillMaxWidth(),
            colors    = CardDefaults.cardColors(containerColor = surfaceVar.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Triad formulas",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVar
                )
                TRIAD_TYPES.forEach { t ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.name}  ${t.formula}", style = MaterialTheme.typography.bodySmall)
                        Text(t.intervalStructure, style = MaterialTheme.typography.bodySmall, color = onSurfaceVar)
                    }
                }
            }
        }

        Text(
            "Diatonic triads in $keyLabel",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceVar
        )

        triads.forEach { dt ->
            val chordName   = NOTE_NAMES[dt.noteIndex] + dt.triad.suffix
            val noteIndices = dt.triad.intervals.map { (dt.noteIndex + it) % 12 }

            Card(
                modifier  = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            dt.roman,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = onSurfaceVar
                        )
                        Text(
                            chordName,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Text(dt.triad.name, style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
                        Spacer(Modifier.weight(1f))
                        Row {
                            dt.noteNames.forEachIndexed { i, note ->
                                if (i > 0) Text(" – ", style = MaterialTheme.typography.bodySmall, color = onSurfaceVar)
                                Text(
                                    note,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (i == 0) MaterialTheme.colorScheme.primary else onSurfaceVar
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // One row of shapes per string set
                    TRIAD_STRING_SETS.forEach { ss ->
                        val shapes = triadShapes(noteIndices, ss)
                        Text(
                            "Strings: ${ss.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVar,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            shapes.forEach { (frets, invLabel) ->
                                MiniTriadDiagram(
                                    frets     = frets,
                                    strLabels = ss.labels,
                                    openNotes = ss.openNotes,
                                    rootNote  = noteIndices[0],
                                    label     = invLabel,
                                    modifier  = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        Text(
            "Tip: These are the chords that sound natural together in this key. Mix and match to build your own progressions.",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVar
        )
    }
}

@Composable
private fun MiniTriadDiagram(
    frets: List<Int>,        // [low, mid, high] string frets
    strLabels: List<String>, // [low, mid, high] string labels
    openNotes: List<Int>,    // [low, mid, high] open string note indices (0-11)
    rootNote: Int,           // note index 0-11 of the triad root
    label: String,           // "Root", "1st", "2nd"
    modifier: Modifier = Modifier
) {
    val primary       = MaterialTheme.colorScheme.primary
    val onPrimary     = MaterialTheme.colorScheme.onPrimary
    val secondary     = MaterialTheme.colorScheme.secondary
    val onSecondary   = MaterialTheme.colorScheme.onSecondary
    val onSurface     = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar  = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    // Display top→bottom as high→low string (standard chord diagram orientation)
    val dispFrets     = frets.reversed()
    val dispLabels    = strLabels.reversed()
    val dispOpenNotes = openNotes.reversed()

    val hasOpen   = frets.any { it == 0 }
    val startFret = if (hasOpen) 0 else frets.min()
    val numSlots  = maxOf(4, frets.max() - startFret + 1)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
        Spacer(Modifier.height(2.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(82.dp)) {
            val dotR      = 6.dp.toPx()
            // String label column | open-string indicator column | nut | fret slots
            val strLabelW = 13.dp.toPx()
            val openIndW  = 14.dp.toPx()   // always present; open circles drawn here
            val nutW      = if (hasOpen) 5.dp.toPx() else 0f

            val left  = strLabelW + openIndW    // x where fret area begins
            val right = size.width - 2.dp.toPx()

            // Vertical: ensure dots never clip top/bottom or overlap "fr N" label
            val firstStrY = dotR + 3.dp.toPx()
            val lastStrY  = size.height - dotR - 16.dp.toPx()
            val strGap    = (lastStrY - firstStrY) / 2f

            fun sy(si: Int) = firstStrY + si * strGap
            val slotW = (right - left - nutW) / numSlots
            fun cx(slot: Int) = left + nutW + slotW * slot + slotW / 2f

            // String lines (top = high, bottom = low)
            for (si in 0..2) {
                val y = sy(si)
                drawLine(
                    color = onSurfaceVar.copy(alpha = 0.45f),
                    start = Offset(left, y), end = Offset(right, y),
                    strokeWidth = 1.5.dp.toPx()
                )
                // String label (G, B, e etc.)
                val lm = textMeasurer.measure(
                    dispLabels[si],
                    TextStyle(fontSize = 8.sp, color = onSurfaceVar)
                )
                drawText(lm, topLeft = Offset(0f, y - lm.size.height / 2f))
            }

            // Nut or left border of fret area
            if (hasOpen) {
                drawLine(
                    onSurface,
                    Offset(left + nutW / 2f, firstStrY - 3.dp.toPx()),
                    Offset(left + nutW / 2f, lastStrY + 3.dp.toPx()),
                    strokeWidth = nutW,
                    cap = androidx.compose.ui.graphics.StrokeCap.Square
                )
            } else {
                drawLine(
                    onSurfaceVar.copy(alpha = 0.3f),
                    Offset(left, firstStrY), Offset(left, lastStrY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Fret wires
            for (f in 1..numSlots) {
                val x = left + nutW + slotW * f
                drawLine(
                    onSurfaceVar.copy(alpha = 0.25f),
                    Offset(x, firstStrY), Offset(x, lastStrY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Dots + note names
            for (si in 0..2) {
                val fret      = dispFrets[si]
                val y         = sy(si)
                val noteIdx   = (dispOpenNotes[si] + fret) % 12
                val noteName  = NOTE_NAMES[noteIdx]
                val isRoot    = noteIdx == rootNote
                val dotColor  = if (isRoot) secondary else primary
                val textColor = if (isRoot) onSecondary else onPrimary

                if (fret == 0) {
                    // Open string: outlined circle in the openIndW area
                    val cx = strLabelW + openIndW / 2f
                    drawCircle(
                        color  = dotColor,
                        radius = dotR,
                        center = Offset(cx, y),
                        style  = Stroke(width = 1.5.dp.toPx())
                    )
                    val nm = textMeasurer.measure(
                        noteName,
                        TextStyle(fontSize = 6.sp, color = dotColor, fontWeight = FontWeight.Bold)
                    )
                    drawText(nm, topLeft = Offset(cx - nm.size.width / 2f, y - nm.size.height / 2f))
                } else {
                    val slot = fret - startFret
                    if (slot in 0 until numSlots) {
                        val cx = cx(slot)
                        drawCircle(dotColor, dotR, Offset(cx, y))
                        val nm = textMeasurer.measure(
                            noteName,
                            TextStyle(fontSize = 6.sp, color = textColor, fontWeight = FontWeight.Bold)
                        )
                        drawText(nm, topLeft = Offset(cx - nm.size.width / 2f, y - nm.size.height / 2f))
                    }
                }
            }

            // Fret position label for non-open shapes
            if (!hasOpen) {
                val lm = textMeasurer.measure(
                    "fr $startFret",
                    TextStyle(fontSize = 7.sp, color = onSurfaceVar)
                )
                drawText(lm, topLeft = Offset(left, lastStrY + dotR + 2.dp.toPx()))
            }
        }
    }
}
