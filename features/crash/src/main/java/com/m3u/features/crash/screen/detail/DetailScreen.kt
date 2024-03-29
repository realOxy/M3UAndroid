package com.m3u.features.crash.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.ExtendedFloatingActionButton
import com.m3u.material.components.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.material.components.Background
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.MonoText

@Composable
internal fun DetailScreen(
    path: String,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val spacing = LocalSpacing.current
    LaunchedEffect(path) {
        viewModel.onEvent(DetailEvent.Init(path))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Background {
        Box {
            LazyColumn(
                contentPadding = PaddingValues(spacing.medium),
                modifier = modifier
            ) {
                item {
                    MonoText(
                        text = state.text,
                        color = LocalContentColor.current
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(spacing.medium)
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("SAVE") },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        viewModel.onEvent(DetailEvent.Save)
                    }
                )
            }
        }
    }
}