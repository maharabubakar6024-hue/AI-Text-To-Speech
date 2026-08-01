package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    maxBarHeight: Dp = 36.dp,
    barWidth: Dp = 4.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val transition = rememberInfiniteTransition(label = "waveform_anim")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val duration = 350 + (index % 5) * 120
            val delay = (index * 60) % 300

            val animatedFactor by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = if (isPlaying) 0.95f else 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, delayMillis = delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val currentHeight = if (isPlaying) maxBarHeight * animatedFactor else maxBarHeight * 0.25f

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPlaying) activeColor else inactiveColor)
            )
        }
    }
}
