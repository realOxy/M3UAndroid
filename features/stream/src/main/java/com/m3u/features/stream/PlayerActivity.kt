package com.m3u.features.stream

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManagerV2
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.service.MediaCommand
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.AbstractHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: StreamViewModel by viewModels()
    private val helper by lazy {
        AbstractHelper(
            activity = this,
            mainDispatcher = mainDispatcher,
            messager = messager,
            playerManager = playerManager
        )
    }

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var pref: Pref

    @Inject
    lateinit var playerManager: PlayerManagerV2

    @Inject
    lateinit var streamRepository: StreamRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var messager: Messager

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                pref = pref,
                alwaysUseDarkTheme = true,
                actions = remoteDirectionService.actions
            ) {
                StreamRoute(
                    onBackPressed = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun playFromShortcuts(streamId: Int) {
        lifecycleScope.launch {
            val stream = streamRepository.get(streamId) ?: return@launch
            val playlist = playlistRepository.get(stream.playlistUrl)
            when {
                // series can not be played from shortcuts
                playlist?.type in Playlist.SERIES_TYPES -> {}
                else -> {
                    helper.play(MediaCommand.Live(stream.id))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val shortcutStreamId = intent
            .getIntExtra(Contracts.PLAYER_SHORTCUT_STREAM_ID, -1)
            .takeIf { it != -1 }
        val recently =
            intent.getBooleanExtra(Contracts.PLAYER_SHORTCUT_STREAM_RECENTLY, false)

        shortcutStreamId?.let { playFromShortcuts(it) }
        if (recently) {
            lifecycleScope.launch {
                val stream = streamRepository.getPlayedRecently() ?: return@launch
                playFromShortcuts(stream.id)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            viewModel.release()
        }
    }
}
