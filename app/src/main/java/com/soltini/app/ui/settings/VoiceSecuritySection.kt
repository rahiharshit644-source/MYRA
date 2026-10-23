package com.soltini.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soltini.app.ui.VoiceProfilesSection
import com.soltini.app.voiceprint.ui.VoiceprintSettingsScreen
import com.soltini.app.voiceprint.ui.VoiceprintViewModel

@Composable
fun VoiceSecuritySection(
    context: Context,
    onOpenVoiceEnrollment: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val voiceprintViewModel: VoiceprintViewModel = viewModel()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            VoiceprintSettingsScreen(
                viewModel = voiceprintViewModel,
                onNavigateToEnrollment = onOpenVoiceEnrollment,
                onNavigateBack = onNavigateBack
            )
        }
        Spacer(Modifier.height(16.dp))
        VoiceProfilesSection(context = context)
    }
}
