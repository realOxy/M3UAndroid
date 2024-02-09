package com.m3u.features.setting.fragments

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.m3u.data.database.model.Stream
import com.m3u.features.setting.BackingUpAndRestoringState
import com.m3u.features.setting.UriWrapper
import com.m3u.features.setting.components.BannedStreamItem
import com.m3u.features.setting.components.LocalStorageButton
import com.m3u.features.setting.components.LocalStorageSwitch
import com.m3u.features.setting.components.RemoteControlSubscribeSwitch
import com.m3u.i18n.R.string
import com.m3u.material.components.Button
import com.m3u.material.components.PlaceholderField
import com.m3u.material.components.TonalButton
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun SubscriptionsFragment(
    contentPadding: PaddingValues,
    title: String,
    url: String,
    uriWrapper: UriWrapper,
    localStorage: Boolean,
    forTv: Boolean,
    backingUpOrRestoring: BackingUpAndRestoringState,
    banneds: ImmutableList<Stream>,
    onBanned: (Int) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    onLocalStorage: () -> Unit,
    onForTv: () -> Unit,
    openDocument: (Uri) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current

    val tv = isTelevision()

    val focusRequester = remember { FocusRequester() }

    LazyColumn(
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
    ) {
        if (banneds.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(string.feat_setting_label_muted_streams),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(
                                vertical = spacing.extraSmall,
                                horizontal = spacing.medium
                            )
                    )
                    banneds.forEach { stream ->
                        BannedStreamItem(
                            stream = stream,
                            onBanned = { onBanned(stream.id) }
                        )
                    }
                }
            }
        }

        item {
            PlaceholderField(
                text = title,
                placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
                onValueChange = onTitle,
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusRequester.requestFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Crossfade(
                targetState = localStorage,
                label = "url"
            ) { localStorage ->
                if (!localStorage) {
                    PlaceholderField(
                        text = url,
                        placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
                        onValueChange = onUrl,
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onSubscribe()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    LocalStorageButton(
                        uriWrapper = uriWrapper,
                        onTitle = onTitle,
                        openDocument = openDocument,
                    )
                }
            }
        }

        if (!tv) {
            item {
                LocalStorageSwitch(
                    checked = localStorage,
                    onChanged = onLocalStorage
                )
                RemoteControlSubscribeSwitch(
                    checked = forTv,
                    onChanged = onForTv
                )
            }
        }

        item {
            Column {
                Button(
                    text = stringResource(string.feat_setting_label_subscribe),
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!tv) {
                    TonalButton(
                        text = stringResource(string.feat_setting_label_parse_from_clipboard),
                        enabled = !localStorage,
                        onClick = {
                            onClipboard(clipboardManager.getText()?.text.orEmpty())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (!tv) {
            item {
                Column {
                    TonalButton(
                        text = stringResource(string.feat_setting_label_backup),
                        enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                        onClick = backup,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TonalButton(
                        text = stringResource(string.feat_setting_label_restore),
                        enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                        onClick = restore,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Spacer(Modifier.imePadding())
        }
    }
}
