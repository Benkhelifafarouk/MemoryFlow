package com.memoryflow.app.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Respects the system "remove animations" accessibility setting. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Circular power control used to activate a learning group.
 * The dial, the glow and the icon are drawn from scratch so the app has its own visual identity.
 */
@Composable
fun PowerDial(
    active: Boolean,
    accent: Color,
    dialSize: Dp,
    enabled: Boolean = true,
    dialLabel: String? = null,
    onToggle: () -> Unit
) {
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.48f, stiffness = 360f),
        label = "dialProgress"
    )
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 420),
        label = "dialGlow"
    )

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseValue"
    )
    val pulseAmount = if (active && !reduced) pulse else 0f

    val discColor by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(if (reduced) 0 else 350),
        label = "discColor"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val glyphColor = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(dialSize)
            .toggleable(
                value = active,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
            .semantics { dialLabel?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val stroke = radius * 0.11f
            val centerPoint = Offset(size.width / 2f, size.height / 2f)

            if (glow > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.30f * glow + 0.10f * pulseAmount),
                            accent.copy(alpha = 0.06f * glow),
                            Color.Transparent
                        ),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius,
                    center = centerPoint
                )
            }

            drawCircle(
                color = trackColor.copy(alpha = 0.55f),
                radius = radius - stroke / 2f,
                center = centerPoint,
                style = Stroke(width = stroke)
            )

            if (progress > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent.copy(alpha = 0.65f), accent, accent.copy(alpha = 0.85f), accent.copy(alpha = 0.65f))
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = discColor,
                radius = radius - stroke * 2.1f,
                center = centerPoint
            )

            // Power glyph: an open ring with a stem.
            val glyphRadius = radius * 0.30f
            drawArc(
                color = glyphColor,
                startAngle = -52f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = Offset(centerPoint.x - glyphRadius, centerPoint.y - glyphRadius + glyphRadius * 0.18f),
                size = Size(glyphRadius * 2f, glyphRadius * 2f),
                style = Stroke(width = stroke * 0.78f, cap = StrokeCap.Round)
            )
            drawLine(
                color = glyphColor,
                start = Offset(centerPoint.x, centerPoint.y - glyphRadius * 1.22f),
                end = Offset(centerPoint.x, centerPoint.y + glyphRadius * 0.18f),
                strokeWidth = stroke * 0.78f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Card surface that lights up with an accent border + glow when active. */
@Composable
fun GlowCard(
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (active) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
        label = "cardBorder"
    )
    val shape = RoundedCornerShape(26.dp)
    val base = if (active) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.16f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(base)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (caption != null) {
            Text(text = caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RetentionBar(retention: Double, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(retention.coerceIn(0.0, 1.0).toFloat())
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✦", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Simple seven day activity chart drawn with Canvas (no chart library needed). */
@Composable
fun WeekChart(
    values: List<Int>,
    labels: List<String>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEachIndexed { index, value ->
                val fraction = value.toFloat() / maxValue
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (value == 0) "" else value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((6 + 58 * fraction).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                if (index == values.lastIndex) accent
                                else accent.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == labels.lastIndex) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HorizontalGradientBar(colors: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(colors))
    )
}
