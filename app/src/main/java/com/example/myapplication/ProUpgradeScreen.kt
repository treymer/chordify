package com.example.myapplication

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AncientGold
import com.example.myapplication.ui.theme.CandleLight
import com.example.myapplication.ui.theme.GoldContainer
import com.example.myapplication.ui.theme.MagicPurple
import com.example.myapplication.ui.theme.OakBorder
import com.example.myapplication.ui.theme.TavernBrown
import com.example.myapplication.ui.theme.WarmParchment
import com.example.myapplication.ui.theme.WarmWood

private data class ProFeature(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val THEORY_FEATURES = listOf(
    ProFeature(Icons.Default.AutoAwesome, "Chord Progressions",  "Common and complex progressions for any key"),
    ProFeature(Icons.Default.AutoAwesome, "Scales & Arpeggios",  "Major, minor, pentatonic, modes, and more"),
    ProFeature(Icons.Default.AutoAwesome, "Rhythm Patterns",     "Strumming and picking patterns for every style"),
    ProFeature(Icons.Default.AutoAwesome, "Circle of Fifths",    "Visual key relationships at a glance"),
    ProFeature(Icons.Default.AutoAwesome, "CAGED System",        "Unlock the entire fretboard with 5 shapes"),
    ProFeature(Icons.Default.AutoAwesome, "Triads",              "Three-note chord voicings across all strings"),
)

private val NOTE_FINDER_FEATURES = listOf(
    ProFeature(Icons.Default.MusicNote, "Interactive Fretboard", "Tap any fret to identify the note"),
    ProFeature(Icons.Default.MusicNote, "Scale Highlighting",    "Visualize scales and modes across the neck"),
)

/**
 * Full-screen paywall shown when a non-Pro user taps Theory or Note Finder.
 *
 * @param targetMode  The AppMode the user was trying to reach (SUGGESTER or FRETBOARD).
 *                    Used for context-aware feature listing.
 * @param billingManager  BillingManager instance to launch the purchase flow.
 * @param onBack      Called when the user taps the back button.
 */
@Composable
fun ProUpgradeScreen(
    targetMode: AppMode,
    billingManager: BillingManager,
    onBack: () -> Unit
) {
    val activity = LocalContext.current as? Activity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TavernBrown)
            .drawBehind {
                // MagicPurple radial glow from top — matches the main app background treatment
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MagicPurple.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.1f
                    )
                )
                // Warm gold glow from bottom for depth
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AncientGold.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height),
                        radius = size.width * 0.8f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Back button ───────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(
                        text = "< Back",
                        color = AncientGold.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Hero icon ─────────────────────────────────────────────────────
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = AncientGold,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Headline ──────────────────────────────────────────────────────
            Text(
                text = "Cadence Pro",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = AncientGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (targetMode) {
                    AppMode.SUGGESTER -> "Unlock the full Theory toolkit — progressions, scales, CAGED, triads, and more."
                    AppMode.FRETBOARD -> "Unlock the interactive Note Finder with scale highlighting across the entire neck."
                    else              -> "Unlock the full suite of Pro music theory tools."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = WarmParchment.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Feature cards ─────────────────────────────────────────────────
            val primaryFeatures = when (targetMode) {
                AppMode.FRETBOARD -> NOTE_FINDER_FEATURES
                else              -> THEORY_FEATURES
            }
            val secondaryFeatures = when (targetMode) {
                AppMode.FRETBOARD -> THEORY_FEATURES
                else              -> NOTE_FINDER_FEATURES
            }
            val secondaryLabel = when (targetMode) {
                AppMode.FRETBOARD -> "Also includes Theory tab:"
                else              -> "Also includes Note Finder tab:"
            }

            ProFeatureCard(
                title = when (targetMode) {
                    AppMode.FRETBOARD -> "Note Finder"
                    else              -> "Theory"
                },
                features = primaryFeatures
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = secondaryLabel,
                color = AncientGold.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(Modifier.height(6.dp))

            ProFeatureCard(
                title = when (targetMode) {
                    AppMode.FRETBOARD -> "Theory"
                    else              -> "Note Finder"
                },
                features = secondaryFeatures,
                dimmed = true
            )

            Spacer(Modifier.height(32.dp))

            // ── Price callout ─────────────────────────────────────────────────
            Text(
                text = "One-time purchase — no subscription",
                color = WarmParchment.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // ── Primary CTA ───────────────────────────────────────────────────
            Button(
                onClick = { activity?.let { billingManager.launchPurchaseFlow(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AncientGold,
                    contentColor   = TavernBrown
                )
            ) {
                Text(
                    text = "Unlock Pro — $1.99",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Restore purchase ──────────────────────────────────────────────
            OutlinedButton(
                onClick = { billingManager.restorePurchases() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AncientGold.copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, OakBorder)
            ) {
                Text(
                    text = "Restore Purchase",
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Payment processed securely by Google Play",
                color = WarmParchment.copy(alpha = 0.35f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProFeatureCard(
    title: String,
    features: List<ProFeature>,
    dimmed: Boolean = false
) {
    val alpha = if (dimmed) 0.6f else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WarmWood.copy(alpha = if (dimmed) 0.6f else 1f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (dimmed) OakBorder.copy(alpha = 0.5f) else AncientGold.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = AncientGold.copy(alpha = alpha),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            features.forEach { feature ->
                ProFeatureRow(feature = feature, alpha = alpha)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProFeatureRow(feature: ProFeature, alpha: Float) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = AncientGold.copy(alpha = alpha),
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = feature.title,
                fontWeight = FontWeight.SemiBold,
                color = CandleLight.copy(alpha = alpha),
                fontSize = 14.sp
            )
            Text(
                text = feature.description,
                color = WarmParchment.copy(alpha = alpha * 0.7f),
                fontSize = 12.sp
            )
        }
    }
}
