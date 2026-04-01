package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Privacy Policy", "Terms of Service")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text      = "Legal",
                        style     = MaterialTheme.typography.titleLarge,
                        color     = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = {
                            Text(
                                text  = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedTab == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> PrivacyPolicyContent()
                1 -> TermsOfServiceContent()
            }
        }
    }
}

@Composable
private fun PrivacyPolicyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        LegalHeading("Privacy Policy")
        LegalMeta("Effective date: April 1, 2026")

        Spacer(Modifier.height(16.dp))

        LegalSectionTitle("Our commitment to your privacy")
        LegalBody(
            "Cadence is a local, offline music utility. We built it to be useful without " +
            "requiring any information about you. This policy explains exactly what data " +
            "the app handles and, more importantly, what it does not."
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Information we collect")
        LegalBody(
            "None. Cadence does not collect, store, transmit, or share any personal " +
            "information. The app does not require an account and does not contact any " +
            "server at any point."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Microphone access")
        LegalBody(
            "The Tuner and Key Finder features request microphone permission to capture " +
            "audio from your device. This audio is processed entirely on-device, in " +
            "real time, to detect pitch. No audio data is ever recorded, stored on disk, " +
            "or sent anywhere. The permission is only active when you tap Start in the " +
            "Tuner or Key Finder and is released the moment you stop."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Analytics & advertising")
        LegalBody(
            "Cadence contains no analytics SDKs, no crash-reporting services, and no " +
            "advertising networks. We do not track how you use the app."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Third-party services")
        LegalBody(
            "Cadence does not integrate with any third-party service. The only " +
            "third-party code included is TarsosDSP, an open-source audio processing " +
            "library used for pitch detection. TarsosDSP runs entirely on-device and " +
            "has no network component."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Data stored on your device")
        LegalBody(
            "The app stores one flag in Android's SharedPreferences to remember whether " +
            "you have completed the first-launch walkthrough. This data never leaves your " +
            "device and is removed when you uninstall the app."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Children's privacy")
        LegalBody(
            "Because Cadence collects no personal information from anyone, there are no " +
            "special considerations for users under the age of 13."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Changes to this policy")
        LegalBody(
            "If our practices ever change, we will update this document and increment " +
            "the version number visible in the app store listing. We encourage you to " +
            "review this page periodically."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Contact")
        LegalBody(
            "Questions or concerns about this policy can be directed to the developer " +
            "through the contact link on the app's Play Store page."
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TermsOfServiceContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        LegalHeading("Terms of Service")
        LegalMeta("Effective date: April 1, 2026")

        Spacer(Modifier.height(16.dp))

        LegalSectionTitle("Acceptance of terms")
        LegalBody(
            "By downloading or using Cadence (\"the App\"), you agree to be bound by " +
            "these Terms of Service. If you do not agree, please uninstall the App."
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("License")
        LegalBody(
            "We grant you a personal, non-exclusive, non-transferable, revocable license " +
            "to use the App on any Android device you own or control, solely for your " +
            "personal, non-commercial purposes."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Permitted use")
        LegalBody(
            "You may use Cadence to tune instruments, practice with a metronome, identify " +
            "musical keys, explore music theory, and find notes on the fretboard. " +
            "You may not: reverse-engineer, decompile, or disassemble the App; use the " +
            "App for any unlawful purpose; or attempt to extract its source code."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Intellectual property")
        LegalBody(
            "The App, including its code, design, graphics, and text, is the property " +
            "of the developer and is protected by applicable intellectual property laws. " +
            "Nothing in these Terms transfers any ownership rights to you."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Microphone permission")
        LegalBody(
            "Certain features of the App require microphone access. You are responsible " +
            "for ensuring you have the right to record audio in your environment. The " +
            "developer is not responsible for any issues arising from your use of the " +
            "microphone feature in any jurisdiction."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Disclaimer of warranties")
        LegalBody(
            "The App is provided \"as is\" and \"as available\" without warranty of any " +
            "kind. We do not warrant that the App will be error-free, uninterrupted, or " +
            "accurate. Musical tools like the tuner should be used as a guide, not as a " +
            "substitute for professional tuning equipment where precision is critical."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Limitation of liability")
        LegalBody(
            "To the fullest extent permitted by law, the developer shall not be liable " +
            "for any indirect, incidental, special, or consequential damages arising from " +
            "your use of, or inability to use, the App."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Changes to these terms")
        LegalBody(
            "We may update these Terms at any time. Continued use of the App after " +
            "changes are posted constitutes your acceptance of the revised Terms."
        )

        Spacer(Modifier.height(20.dp))

        LegalSectionTitle("Governing law")
        LegalBody(
            "These Terms are governed by the laws of the jurisdiction in which the " +
            "developer is located, without regard to conflict-of-law principles."
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ── Typography helpers ────────────────────────────────────────────────────────

@Composable
private fun LegalHeading(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LegalMeta(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text  = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LegalSectionTitle(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LegalBody(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.bodyMedium,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 22.sp
    )
}
