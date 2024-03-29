@file:Suppress("unused")

package com.m3u.material.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m3u.material.model.LocalSpacing

@Composable
fun DialogTextField(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    placeholder: String = "",
    onTextChange: (String) -> Unit = {},
    leadingIcon: ImageVector? = null,
    trainingIcon: ImageVector? = null,
    iconTint: Color = backgroundColor,
    readOnly: Boolean = true,
    onTrainingIconClick: (() -> Unit)? = null,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                tint = iconTint.takeOrElse { MaterialTheme.colorScheme.onBackground },
                contentDescription = null
            )
        }
        TextField(
            text = text,
            onValueChange = onTextChange,
            backgroundColor = if (readOnly) Color.Transparent
            else backgroundColor.takeOrElse { theme.surface },
            contentColor = contentColor.takeOrElse { theme.onSurface },
            readOnly = readOnly,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
            placeholder = placeholder
        )

        if (onTrainingIconClick != null && trainingIcon != null) {
            IconButton(
                icon = trainingIcon,
                tint = iconTint.takeOrElse { MaterialTheme.colorScheme.onBackground },
                onClick = onTrainingIconClick,
                contentDescription = null
            )
        }
    }
}

@Composable
fun DialogItem(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        tonalElevation = 0.dp,
        color = color.takeOrElse { theme.surface },
        contentColor = contentColor.takeOrElse { theme.onSurface },
        modifier = Modifier.semantics(mergeDescendants = true) { }
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .clickable(onClick = onClick)
                .padding(spacing.medium)
                .fillMaxWidth()
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DialogTextField(
    resId: Int,
    onTextChange: (String) -> Unit,
    color: Color = MaterialTheme.colorScheme.onBackground,
    icon: ImageVector? = null,
    iconTint: Color = color,
    readOnly: Boolean = true,
    onIconClick: (() -> Unit)? = null
) {
    DialogTextField(
        text = stringResource(id = resId),
        backgroundColor = color,
        onTextChange = onTextChange,
        trainingIcon = icon,
        iconTint = iconTint,
        readOnly = readOnly,
        onTrainingIconClick = onIconClick
    )
}

@Composable
fun DialogItem(
    resId: Int,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DialogItem(
        text = stringResource(id = resId),
        color = color,
        contentColor = contentColor,
        onClick = onClick
    )
}

@Composable
fun AppDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(
        2.dp,
        MaterialTheme.colorScheme.outline
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                color = theme.background,
                contentColor = theme.onBackground,
                shape = RoundedCornerShape(LocalSpacing.current.medium),
                border = border,
                tonalElevation = spacing.medium,
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
                    .wrapContentSize()
                    .animateContentSize()
                    .then(modifier)
            ) {
                Column(
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    content = content
                )
            }
        }
    }
}

typealias OnDismissRequest = () -> Unit
typealias OnConfirm = () -> Unit
typealias OnDismiss = () -> Unit
