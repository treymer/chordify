package com.example.myapplication

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AncientGold
import com.example.myapplication.ui.theme.FadedInk
import com.example.myapplication.ui.theme.MagicPurple
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val body: String
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        emoji    = "🎸",
        title    = "Welcome to Cadence",
        subtitle = "Your complete music toolkit",
        body     = "Everything you need to play better, practice smarter, and understand music — all in one place."
    ),
    OnboardingPage(
        emoji    = "🎵",
        title    = "Chromatic Tuner",
        subtitle = "Always in tune",
        body     = "Pick up pitch in real time with a high-accuracy chromatic tuner. Just hold your phone near the strings and play."
    ),
    OnboardingPage(
        emoji    = "🔑",
        title    = "Key Finder",
        subtitle = "Discover your key",
        body     = "Play a few notes and Cadence figures out what key you're in — great for jamming along to recordings or songwriting."
    ),
    OnboardingPage(
        emoji    = "🥁",
        title    = "Metronome",
        subtitle = "Keep perfect time",
        body     = "A rock-solid audio metronome from 40 to 240 BPM. Tap the screen to set tempo by feel, or dial it in precisely."
    ),
    OnboardingPage(
        emoji    = "🎼",
        title    = "Theory & Note Finder",
        subtitle = "Know your fretboard",
        body     = "Explore chords, scales, the Circle of Fifths, and every note on the neck — all the theory you need to level up."
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pageCount  = ONBOARDING_PAGES.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope      = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MagicPurple.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.95f
                    )
                )
            }
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button — top-right
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text(
                        text  = "Skip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Pager — takes most of the height
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(page = ONBOARDING_PAGES[pageIndex])
            }

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.padding(bottom = 24.dp)
            ) {
                repeat(pageCount) { index ->
                    val isSelected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue  = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMedium
                        ),
                        label = "dotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) AncientGold
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            // Navigation row: Back (if not first), and Next / Done
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Back button — invisible on first page to keep layout stable
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Text(
                        text  = "Back",
                        color = if (pagerState.currentPage > 0)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            Color.Transparent,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                val isLastPage = pagerState.currentPage == pageCount - 1
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AncientGold,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text       = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text     = page.emoji,
            fontSize = 72.sp
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text      = page.title,
            style     = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color     = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = page.subtitle,
            style     = MaterialTheme.typography.titleMedium,
            color     = FadedInk,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = page.body,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}
