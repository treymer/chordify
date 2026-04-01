package com.example.myapplication

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Feature(
    val mode: AppMode,
    val emoji: String,
    val label: String,
    val description: String
)

private val FEATURES = listOf(
    Feature(AppMode.TUNER,      "🎸", "Tuner",       "Chromatic tuner"),
    Feature(AppMode.KEY_FINDER, "🔑", "Key Finder",  "Detect your key"),
    Feature(AppMode.METRONOME,  "🥁", "Metronome",   "Keep perfect time"),
    Feature(AppMode.SUGGESTER,  "🎵", "Theory",      "Chords & scales"),
    Feature(AppMode.FRETBOARD,  "🎼", "Note Finder", "Notes on the neck")
)

@Composable
fun HomeScreen(
    onNavigate: (AppMode) -> Unit,
    onOpenLegal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Info / Legal button ───────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenLegal) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = "Privacy & Terms",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Header ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.home_guitar),
                contentDescription = "Cadence",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Cadence",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Your music toolkit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Feature grid ─────────────────────────────────────────────────────
        // Pair rows: [Tuner, Key Finder], [Metronome, Ideas], then Note Finder centred
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FEATURES.chunked(2).forEach { row ->
                if (row.size == 2) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { feature ->
                            FeatureTile(
                                feature  = feature,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick  = { onNavigate(feature.mode) }
                            )
                        }
                    }
                } else {
                    // Single tile — centred at half width
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        FeatureTile(
                            feature  = row[0],
                            modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(),
                            onClick  = { onNavigate(row[0].mode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(feature: Feature, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier  = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(feature.emoji, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                feature.label,
                fontWeight = FontWeight.SemiBold,
                style      = MaterialTheme.typography.titleSmall,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                feature.description,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
