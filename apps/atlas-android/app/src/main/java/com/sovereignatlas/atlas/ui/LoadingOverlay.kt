// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.ui.hud.AtlasMotionMath
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun AtlasLoadingOverlay(
    visible: Boolean,
    statusLabel: String? = null,
    reducedMotion: Boolean = false,
) {
    val enterDuration = if (reducedMotion) 80 else 220
    val exitDuration = if (reducedMotion) 80 else 320
    var stageStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible, statusLabel) {
        if (visible && statusLabel == null) {
            stageStep = 0
            while (true) {
                delay(850L)
                stageStep += 1
            }
        }
    }
    val effectiveStatus = statusLabel ?: AtlasMotionMath.bootTelemetryStage(stageStep)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(enterDuration)),
        exit = fadeOut(animationSpec = tween(exitDuration)),
    ) {
        val transition = rememberInfiniteTransition(label = "atlasBootLoader")
        val sweepAngle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "radarSweep",
        )
        val counterRingAngle by transition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "counterRing",
        )
        val activeSweep = if (reducedMotion) 32f else sweepAngle
        val activeCounter = if (reducedMotion) 0f else counterRingAngle

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF10151C))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.bg_sovereign_topography),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.44f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xCC10151C),
                                Color(0xE60D1218),
                                Color(0xFA0A0E13),
                            ),
                        ),
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(168.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.minDimension * 0.46f
                        val midRadius = size.minDimension * 0.35f
                        val innerRadius = size.minDimension * 0.24f

                        drawCircle(
                            color = Color(0xE6141C24),
                            radius = outerRadius,
                            center = center,
                        )
                        drawCircle(
                            color = Color(0x59A8B5C1),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = 1.6.dp.toPx()),
                        )

                        for (deg in 0 until 360 step 10) {
                            val rad = Math.toRadians(deg.toDouble())
                            val isMajor = deg % 90 == 0
                            val tickInner = outerRadius - if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                            val start = Offset(
                                x = center.x + (tickInner * cos(rad)).toFloat(),
                                y = center.y + (tickInner * sin(rad)).toFloat(),
                            )
                            val end = Offset(
                                x = center.x + (outerRadius * cos(rad)).toFloat(),
                                y = center.y + (outerRadius * sin(rad)).toFloat(),
                            )
                            drawLine(
                                color = if (isMajor) Color(0xFFA8B5C1) else Color(0x55A8B5C1),
                                start = start,
                                end = end,
                                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            )
                        }

                        rotate(degrees = activeCounter, pivot = center) {
                            val arcTopLeft = Offset(center.x - midRadius, center.y - midRadius)
                            val arcSize = Size(midRadius * 2f, midRadius * 2f)
                            for (quad in 0 until 4) {
                                drawArc(
                                    color = if (quad % 2 == 0) Color(0x9939C5F3) else Color(0x80F4B85C),
                                    startAngle = quad * 90f + 12f,
                                    sweepAngle = 54f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }

                        rotate(degrees = activeSweep, pivot = center) {
                            val sweepTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                            val sweepSize = Size(outerRadius * 2f, outerRadius * 2f)
                            drawArc(
                                color = Color(0x1839C5F3),
                                startAngle = -68f,
                                sweepAngle = 68f,
                                useCenter = true,
                                topLeft = sweepTopLeft,
                                size = sweepSize,
                            )
                            drawArc(
                                color = Color(0x3339C5F3),
                                startAngle = -28f,
                                sweepAngle = 28f,
                                useCenter = true,
                                topLeft = sweepTopLeft,
                                size = sweepSize,
                            )
                            drawLine(
                                color = Color(0xFF39C5F3),
                                start = center,
                                end = Offset(center.x + outerRadius - 2.dp.toPx(), center.y),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }

                        drawCircle(
                            color = Color(0xFF10151C),
                            radius = innerRadius,
                            center = center,
                        )
                        drawCircle(
                            color = Color(0x6639C5F3),
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_atlas_emblem),
                        contentDescription = "Sovereign Atlas Emblem",
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = Color(0xEB141C24),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x4DA8B5C1)),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "SOVEREIGN ATLAS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.4.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = effectiveStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF39C5F3),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (reducedMotion) {
                            LinearProgressIndicator(
                                progress = { 0.65f },
                                color = Color(0xFF39C5F3),
                                trackColor = Color(0xFF1B2631),
                                modifier = Modifier
                                    .width(210.dp)
                                    .height(3.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                color = Color(0xFF39C5F3),
                                trackColor = Color(0xFF1B2631),
                                modifier = Modifier
                                    .width(210.dp)
                                    .height(3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AtlasActivityBanner(
    visible: Boolean,
    label: String,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animMs = if (reducedMotion) 60 else 180
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(animMs)) + expandVertically(tween(animMs)),
        exit = fadeOut(tween(animMs)) + shrinkVertically(tween(animMs)),
        modifier = modifier,
    ) {
        Surface(
            color = Color(0xEB141C24),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0x8039C5F3)),
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (!reducedMotion) {
                    CircularProgressIndicator(
                        color = Color(0xFF39C5F3),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0),
                )
            }
        }
    }
}
