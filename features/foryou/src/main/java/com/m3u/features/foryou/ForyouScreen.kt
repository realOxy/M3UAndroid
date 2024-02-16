package com.m3u.features.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.features.foryou.components.ForyouDialog
import com.m3u.features.foryou.components.OnRename
import com.m3u.features.foryou.components.OnUnsubscribe
import com.m3u.features.foryou.components.PlaylistGallery
import com.m3u.features.foryou.components.PlaylistGalleryPlaceholder
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.components.recommend.RecommendGallery
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.split
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import com.m3u.ui.ResumeEvent
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val tv = isTelevision()
    val title = stringResource(string.ui_title_foryou)

    val details by viewModel.details.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()

    EventHandler(resume) {
        helper.deep = 0
        helper.title = title.title()
        helper.actions = persistentListOf(
            Action(
                icon = Icons.Rounded.Add,
                contentDescription = "add",
                onClick = navigateToSettingPlaylistManagement
            )
        )
    }

    Background {
        Box(modifier) {
            ForyouScreen(
                details = details,
                recommend = recommend,
                rowCount = pref.rowCount,
                contentPadding = contentPadding,
                navigateToPlaylist = navigateToPlaylist,
                navigateToStream = navigateToStream,
                navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                unsubscribe = { viewModel.unsubscribe(it) },
                rename = { playlistUrl, target -> viewModel.rename(playlistUrl, target) },
                modifier = Modifier
                    .fillMaxSize()
                    .thenIf(!tv && pref.godMode) {
                        Modifier.interceptVolumeEvent { event ->
                            pref.rowCount = when (event) {
                                KeyEvent.KEYCODE_VOLUME_UP -> (pref.rowCount - 1).coerceAtLeast(1)
                                KeyEvent.KEYCODE_VOLUME_DOWN -> (pref.rowCount + 1).coerceAtMost(2)
                                else -> return@interceptVolumeEvent
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    details: ImmutableList<PlaylistDetail>,
    recommend: Recommend,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var dialog: ForyouDialog by remember { mutableStateOf(ForyouDialog.Idle) }
    Background(modifier) {
        Box {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.fillMaxSize()
            ) {
                val showRecommend = recommend.isNotEmpty()
                val showPlaylist = details.isNotEmpty()
                val (topContentPadding, otherContentPadding) =
                    contentPadding split if (showRecommend) WindowInsetsSides.Top else null
                if (showRecommend) {
                    Box(Modifier.padding(topContentPadding)) {
                        RecommendGallery(
                            recommend = recommend,
                            navigateToStream = navigateToStream,
                            navigateToPlaylist = navigateToPlaylist,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (showPlaylist) {
                    PlaylistGallery(
                        rowCount = actualRowCount,
                        details = details,
                        navigateToPlaylist = navigateToPlaylist,
                        onMenu = { dialog = ForyouDialog.Selections(it) },
                        contentPadding = otherContentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(
                                LocalHazeState.current,
                                HazeDefaults.style(MaterialTheme.colorScheme.surface)
                            )
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        PlaylistGalleryPlaceholder(
                            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            ForyouDialog(
                status = dialog,
                update = { dialog = it },
                unsubscribe = unsubscribe,
                rename = rename
            )
        }
    }

    BackHandler(dialog != ForyouDialog.Idle) {
        dialog = ForyouDialog.Idle
    }
}
