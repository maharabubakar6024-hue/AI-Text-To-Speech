package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PresetTextItem(
    val label: String,
    val text: String
)

val defaultPresetTexts = listOf(
    PresetTextItem(
        "Greeting",
        "Hello! Welcome to AI Text to Speech. Experience natural AI voices with customizable pitch and speed."
    ),
    PresetTextItem(
        "Story",
        "Once upon a time in a digital realm, intelligent AI engines brought written words to life through seamless speech synthesis."
    ),
    PresetTextItem(
        "Quote",
        "The best way to predict the future is to invent it. Innovation distinguishes between a leader and a follower."
    ),
    PresetTextItem(
        "Motivation",
        "Success is not final, failure is not fatal: it is the courage to continue that counts. Stay focused and keep striving!"
    ),
    PresetTextItem(
        "Tech Update",
        "Artificial Intelligence algorithms can now generate hyper-realistic audio, transforming accessibility and content creation worldwide."
    )
)

@Composable
fun PresetChips(
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        items(defaultPresetTexts) { preset ->
            FilterChip(
                selected = false,
                onClick = { onPresetSelected(preset.text) },
                label = { Text(preset.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
