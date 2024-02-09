package com.m3u.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.Message
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberSnackHostState(
    message: Message = Message.Dynamic.EMPTY
): SnackHostState = remember(message) {
    SnackHostStateImpl(message)
}

@Stable
abstract class SnackHostState {
    abstract var message: Message
    abstract var isPressed: Boolean
        internal set
}

@Stable
private class SnackHostStateImpl(
    message: Message
) : SnackHostState() {
    override var message: Message by mutableStateOf(message)
    override var isPressed: Boolean by mutableStateOf(false)
}

@Composable
fun SnackHost(
    modifier: Modifier = Modifier,
    state: SnackHostState = rememberSnackHostState()
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val duration = LocalDuration.current
    val feedback = LocalHapticFeedback.current

    val message = state.message

    val television = message.type == Message.TYPE_TELEVISION

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(Unit) {
        snapshotFlow { isPressed }
            .collectLatest {
                if (it) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                state.isPressed = it
            }
    }

    val currentContainerColor by animateColorAsState(
        targetValue = when (message.type) {
            Message.TYPE_TELEVISION -> theme.onBackground
            else -> when (message.level) {
                Message.LEVEL_ERROR -> theme.error
                Message.LEVEL_WARN -> theme.tertiary
                else -> theme.primary
            }
        },
        label = "snack-host-color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = when (message.type) {
            Message.TYPE_TELEVISION -> theme.background
            else -> when (message.level) {
                Message.LEVEL_ERROR -> theme.onError
                Message.LEVEL_WARN -> theme.onTertiary
                else -> theme.onPrimary
            }
        },
        label = "snack-host-color"
    )
    val currentScale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        label = "snack-host-scale"
    )
    AnimatedVisibility(
        visible = message.level != Message.LEVEL_EMPTY,
        enter = slideInVertically(
            animationSpec = spring()
        ) { it } + fadeIn(
            animationSpec = spring()
        ),
        exit = slideOutVertically(
            animationSpec = spring()
        ) { it } + fadeOut(
            animationSpec = spring()
        ),
        modifier = Modifier
            .graphicsLayer {
                scaleX = currentScale
                scaleY = currentScale
            }
            .padding(spacing.medium)
            .then(modifier)
    ) {
        LaunchedEffect(Unit) {
            delay(duration.fast.milliseconds)
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = currentContainerColor,
                contentColor = currentContentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(0.dp),
            onClick = { },
            interactionSource = interactionSource,
            modifier = Modifier.animateContentSize()
        ) {
            val text = AppSnackHostDefaults.formatText(message)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = spacing.small
                )
            ) {
                when {
                    television -> {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null
                        )
                    }
                }
                Crossfade(
                    targetState = isPressed,
                    label = "snake-host-text"
                ) { isPressed ->
                    Text(
                        text = text,
                        maxLines = if (isPressed) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
    }
}

object AppSnackHostDefaults {
    @Composable
    fun formatText(message: Message): String {
        return when (message) {
            is Message.Static -> {
                val args = remember(message.formatArgs) {
                    message.formatArgs.flatMap {
                        when (it) {
                            is Array<*> -> it.toList().filterNotNull()
                            is Collection<*> -> it.toList().filterNotNull()
                            else -> listOf(it)
                        }
                    }.toTypedArray()
                }
                stringResource(message.resId, *args)
            }

            is Message.Dynamic -> message.value
        }.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT)
            else it.toString()
        }
    }
}