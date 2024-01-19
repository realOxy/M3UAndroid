package com.m3u.features.playlist

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.tvprovider.media.tv.TvContractCompat
import com.m3u.core.Contracts
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.refresh
import com.m3u.data.manager.MessageManager
import com.m3u.data.manager.PlayerManager
import com.m3u.features.playlist.PlaylistMessage.StreamCoverSaved
import com.m3u.features.playlist.navigation.PlaylistNavigation
import com.m3u.ui.Sort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.tvprovider.media.tv.Channel as TvChannel

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamRepository: StreamRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository,
    playerManager: PlayerManager,
    private val pref: Pref,
    @Logger.Ui private val logger: Logger,
    private val messageManager: MessageManager
) : BaseViewModel<PlaylistState, PlaylistEvent>(
    emptyState = PlaylistState()
) {
    internal val playlistUrl: StateFlow<String> =
        savedStateHandle.getStateFlow(PlaylistNavigation.TYPE_URL, "")
    private val recommend: StateFlow<String?> =
        savedStateHandle.getStateFlow(PlaylistNavigation.TYPE_RECOMMEND, null)

    override fun onEvent(event: PlaylistEvent) {
        when (event) {
            PlaylistEvent.Refresh -> refresh()
            is PlaylistEvent.Favourite -> favourite(event)
            PlaylistEvent.ScrollUp -> scrollUp()
            is PlaylistEvent.Ban -> ban(event)
            is PlaylistEvent.SavePicture -> savePicture(event)
            is PlaylistEvent.Query -> query(event)
            is PlaylistEvent.CreateShortcut -> createShortcut(event.context, event.id)
        }
    }

    private val zappingMode: StateFlow<Boolean> = pref
        .observeAsFlow { it.zappingMode }
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    internal val zapping: StateFlow<Stream?> = combine(
        zappingMode,
        playerManager.url,
        streamRepository.observeAll()
    ) { zappingMode, url, streams ->
        if (!zappingMode) null
        else streams.find { it.url == url }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    private var _refreshing = MutableStateFlow(false)
    internal val refreshing = _refreshing.asStateFlow()

    private fun refresh() {
        val url = playlistUrl.value
        playlistRepository
            .refresh(url, pref.playlistStrategy)
            .onEach { resource ->
                val refreshing = resource is Resource.Loading
                _refreshing.update { refreshing }
                val message = if (refreshing) PlaylistMessage.Refreshing else PlaylistMessage.None
                messageManager.emit(message)
            }
            .launchIn(viewModelScope)
    }

    private fun favourite(event: PlaylistEvent.Favourite) {
        viewModelScope.launch {
            val id = event.id
            val target = event.target
            streamRepository.setFavourite(id, target)
        }
    }

    private fun scrollUp() {
        writable.update {
            it.copy(
                scrollUp = eventOf(Unit)
            )
        }
    }

    private fun savePicture(event: PlaylistEvent.SavePicture) {
        val id = event.id
        viewModelScope.launch {
            val stream = streamRepository.get(id)
            if (stream == null) {
                messageManager.emit(PlaylistMessage.StreamNotFound)
                return@launch
            }
            val cover = stream.cover
            if (cover.isNullOrEmpty()) {
                messageManager.emit(PlaylistMessage.StreamCoverNotFound)
                return@launch
            }
            mediaRepository
                .savePicture(cover)
                .onEach { resource ->
                    when (resource) {
                        Resource.Loading -> {}
                        is Resource.Success -> {
                            messageManager.emit(StreamCoverSaved(resource.data.absolutePath))
                        }

                        is Resource.Failure -> {
                            logger.log(resource.message.orEmpty())
                        }
                    }
                }
                .launchIn(this)
        }
    }

    private fun ban(event: PlaylistEvent.Ban) {
        viewModelScope.launch {
            val id = event.id
            val stream = streamRepository.get(id)
            if (stream == null) {
                messageManager.emit(PlaylistMessage.StreamNotFound)
            } else {
                streamRepository.ban(stream.id, true)
            }
        }
    }

    private fun createShortcut(context: Context, id: Int) {
        val shortcutId = "stream_$id"
        viewModelScope.launch {
            val stream = streamRepository.get(id) ?: return@launch
            val bitmap = stream.cover?.let { mediaRepository.loadDrawable(it)?.toBitmap() }
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(stream.title)
                .setLongLabel(stream.url)
                .setIcon(
                    bitmap
                        ?.let { IconCompat.createWithBitmap(it) }
                        ?: IconCompat.createWithResource(context, R.drawable.round_play_arrow_24)
                )
                .setIntent(
                    Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName.createRelative(
                            context,
                            Contracts.PLAYER_ACTIVITY
                        )
                        putExtra(Contracts.PLAYER_SHORTCUT_STREAM_URL, stream.url)
                    }
                )
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
        }
    }

    private val _query: MutableStateFlow<String> = MutableStateFlow("")
    internal val query: StateFlow<String> = _query.asStateFlow()
    private fun query(event: PlaylistEvent.Query) {
        val text = event.text
        _query.update { text }
    }

    private fun List<Stream>.toChannels(recommend: String?): List<Channel> = groupBy { it.group }
        .toList()
        .map { Channel(it.first, it.second.toPersistentList()) }
        .sortedByDescending { recommend?.equals(it.title) }

    private fun List<Stream>.toSingleChannel(): List<Channel> = listOf(
        Channel("", toPersistentList())
    )

    internal val playlist: StateFlow<Playlist?> = playlistUrl.map { url ->
        playlistRepository.get(url)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val unsorted: StateFlow<List<Stream>> = combine(
        playlistUrl.flatMapLatest { url ->
            playlistRepository.observeWithStreams(url)
        },
        query
    ) { current, query ->
        current?.streams?.filter { !it.banned && it.title.contains(query, true) } ?: emptyList()
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val sorts: ImmutableList<Sort> = Sort.entries.toPersistentList()

    private val sortIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    internal val sort: StateFlow<Sort> = sortIndex
        .map { sorts[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun sort(sort: Sort) {
        sortIndex.update { sorts.indexOf(sort).coerceAtLeast(0) }
    }

    internal val channels: StateFlow<ImmutableList<Channel>> = combine(
        unsorted,
        sort,
        recommend
    ) { all, sort, recommend ->
        when (sort) {
            Sort.ASC -> all.sortedBy { it.title }.toSingleChannel()
            Sort.DESC -> all.sortedByDescending { it.title }.toSingleChannel()
            Sort.UNSPECIFIED -> all.toChannels(recommend)
        }
            .toPersistentList()
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = persistentListOf(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val message = messageManager.message
}