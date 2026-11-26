package com.libreflow.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.libreflow.engine.PipelineState
import com.libreflow.ui.theme.FlowEmerald
import com.libreflow.ui.theme.FlowInk
import kotlin.math.sin

/**
 * 120Hz-smooth floating dictation pill.
 * Reads audio RMS strictly inside DrawScope via [rmsProvider] to avoid Compose recomposition.
 */
@Composable
fun FloatingBubbleView(
    state: PipelineState,
    rmsProvider: () -> Float,
    sizeScale: Float,
    opacity: Float,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val isListening = state is PipelineState.Listening
    val isProcessing = state is PipelineState.Processing
    val isDone = state is PipelineState.Done

    val pillHeight = 54.dp * sizeScale
    val targetWidth by animateDpAsState(
        targetValue = when {
            isListening -> 154.dp * sizeScale
            isProcessing -> 112.dp * sizeScale
            else -> pillHeight
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillWidth"
    )
    val cornerRadius = pillHeight / 2f

    val pillColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF141416)
            isProcessing -> Color(0xFF232429)
            isDone -> FlowEmerald
            else -> FlowInk
        },
        label = "pillColor"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = opacity
            }
            .width(targetWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(pillColor)
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .padding(horizontal = if (isListening || isProcessing) 14.dp * sizeScale else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isListening -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp * sizeScale)
                            .clip(CircleShape)
                            .background(Color(0xFFFF4D4F))
                    )
                    Spacer(modifier = Modifier.width(10.dp * sizeScale))
                    WaveformCanvas(
                        rmsProvider = rmsProvider,
                        isActive = true,
                        barColor = Color.White,
                        modifier = Modifier
                            .width(96.dp * sizeScale)
                            .height(26.dp * sizeScale)
                    )
                }
            }
            isProcessing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Polishing",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp * sizeScale)
                    )
                    Spacer(modifier = Modifier.width(8.dp * sizeScale))
                    WaveformCanvas(
                        rmsProvider = { 0.25f },
                        isActive = true,
                        barColor = FlowEmerald,
                        modifier = Modifier
                            .width(44.dp * sizeScale)
                            .height(20.dp * sizeScale)
                    )
                }
            }
            isDone -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Inserted",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp * sizeScale)
                )
            }
            else -> {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.libreflow.R.drawable.ic_libreflow_bars),
                    contentDescription = "Start dictation",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp * sizeScale)
                )
            }
        }
    }
}

/**
 * Pure DrawScope 60/120fps audio waveform visualizer.
 * Never triggers tree recomposition; updates phase strictly via frame clock.
 */
@Composable
fun WaveformCanvas(
    rmsProvider: () -> Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = FlowInk,
    barCount: Int = 11
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive) {
        if (!isActive) {
            phase = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    phase += dt * 7.5f
                }
                lastNanos = nanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val currentPhase = phase
        val rms = if (isActive) rmsProvider().coerceIn(0.05f, 1f) else 0.08f
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.52f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normPos = (i - barCount / 2f) / (barCount / 2f)
            val envelope = 1f - (normPos * normPos * 0.55f)
            val wave = if (isActive) {
                (0.55f + 0.45f * sin(currentPhase + i * 0.72f))
            } else {
                0.2f
            }
            val barH = (size.height * (0.18f + 0.82f * rms * wave * envelope))
                .coerceIn(barWidth, size.height)
            val left = i * slotWidth + (slotWidth - barWidth) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
