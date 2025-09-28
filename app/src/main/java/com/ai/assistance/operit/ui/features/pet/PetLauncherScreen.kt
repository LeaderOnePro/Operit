package com.ai.assistance.operit.ui.features.pet

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold

@Composable
fun PetLauncherScreen() {
    val ctx = LocalContext.current
    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.desktop_pet_function_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { startPetService(ctx) }) {
                Text(text = stringResource(R.string.desktop_pet_start))
            }
        }
    }
}

private fun startPetService(context: Context) {
    try {
        val intent = Intent(context, com.ai.assistance.operit.services.PetOverlayService::class.java)
        context.startForegroundService(intent)
    } catch (e: Exception) {
        // fallback for < O
        try {
            context.startService(Intent(context, com.ai.assistance.operit.services.PetOverlayService::class.java))
        } catch (_: Exception) { }
    }
}

