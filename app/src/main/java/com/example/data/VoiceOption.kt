package com.example.data

import java.util.Locale

data class VoiceOption(
    val id: String,
    val name: String,
    val locale: Locale,
    val gender: String = "Neutral",
    val description: String = ""
) {
    val displayName: String
        get() = "$name (${locale.displayLanguage}${if (gender.isNotBlank()) " - $gender" else ""})"
}
