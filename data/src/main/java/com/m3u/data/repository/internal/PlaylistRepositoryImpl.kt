package com.m3u.data.repository.internal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.util.basic.startsWithAny
import com.m3u.core.util.readFileContent
import com.m3u.core.util.readFileName
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.PlaylistWithStreams
import com.m3u.data.database.model.Stream
import com.m3u.data.parser.m3u.M3UParser
import com.m3u.data.parser.m3u.toStream
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamLive
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.XtreamSerial
import com.m3u.data.parser.xtream.XtreamStreamInfo
import com.m3u.data.parser.xtream.XtreamVod
import com.m3u.data.parser.xtream.asStream
import com.m3u.data.parser.xtream.toStream
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.i18n.R.string
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.Reader
import javax.inject.Inject

private const val BUFFER_XTREAM_CAPACITY = 100
private const val BUFFER_RESTORE_CAPACITY = 400

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val streamDao: StreamDao,
    logger: Logger,
    client: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val pref: Pref,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    private val logger = logger.prefix("playlist-repos")
    private val client = client
        .newBuilder()
        .addInterceptor(
            ChuckerInterceptor.Builder(context)
                .maxContentLength(10240)
                .build()
        )
        .build()

    override suspend fun m3u(
        title: String,
        url: String,
        callback: (count: Int, total: Int) -> Unit
    ) {
        suspend fun parse(
            playlistUrl: String,
            input: InputStream
        ): List<Stream> = logger.execute {
            m3uParser.execute(input).map { it.toStream(playlistUrl, 0L) }
        } ?: emptyList()

        suspend fun acquireNetwork(url: String): List<Stream> {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = withContext(ioDispatcher) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) return emptyList()
            val input = response.body?.byteStream()
            return input?.use { parse(url, it) } ?: emptyList()
        }

        suspend fun acquireAndroid(url: String): List<Stream> {
            val uri = Uri.parse(url)
            val input = context.contentResolver.openInputStream(uri)
            return input?.use { parse(url, it) } ?: emptyList()
        }

        var currentCount = 0
        callback(currentCount, -1)
        withContext(ioDispatcher) {
            try {
                val actualUrl = url.actualUrl()
                val streams = when {
                    url.isSupportedNetworkUrl() -> acquireNetwork(actualUrl)
                    url.isSupportedAndroidUrl() -> acquireAndroid(actualUrl)
                    else -> emptyList() // never reach here!
                }
                currentCount += streams.size
                callback(currentCount, -1)

                val playlist = playlistDao.getByUrl(actualUrl)?.copy(
                    title = title
                ) ?: Playlist(title, actualUrl, source = DataSource.M3U)
                playlistDao.insertOrReplace(playlist)

                streamDao.compareAndUpdate(
                    strategy = pref.playlistStrategy,
                    url = url,
                    update = streams
                )
            } catch (e: FileNotFoundException) {
                error(context.getString(string.data_error_file_not_found))
            } catch (e: Exception) {
                logger.log(e)
            }
        }
    }

    override suspend fun xtream(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?
    ): Unit = withContext(ioDispatcher) {
        val input = XtreamInput(basicUrl, username, password, type)
        val (
            liveCategories,
            vodCategories,
            serialCategories,
            allowedOutputFormats,
            serverProtocol,
            port
        ) = xtreamParser.output(input)

        val livePlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_LIVE),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.getByUrl(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }
        val vodPlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_VOD),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.getByUrl(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }
        val seriesPlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_SERIES),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.getByUrl(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }

        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES

        if (requiredLives) {
            unsubscribe(livePlaylist.url)
            playlistDao.insertOrReplace(livePlaylist)
        }
        if (requiredVods) {
            unsubscribe(vodPlaylist.url)
            playlistDao.insertOrReplace(vodPlaylist)
        }
        if (requiredSeries) {
            unsubscribe(seriesPlaylist.url)
            playlistDao.insertOrReplace(seriesPlaylist)
        }

        val buffer = mutableListOf<Stream>()
        val mutex = Mutex()

        xtreamParser
            .entityOutputs(input)
            .map { current ->
                when (current) {
                    is XtreamLive -> {
                        current.toStream(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = livePlaylist.url,
                            category = liveCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty(),
                            containerExtension = allowedOutputFormats.first()
                        )
                    }

                    is XtreamVod -> {
                        current.toStream(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = vodPlaylist.url,
                            category = vodCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty()
                        )
                    }

                    // we save serial as stream
                    // when we click the serial stream, we should call serialInfo api
                    // for its episodes.
                    is XtreamSerial -> {
                        current.asStream(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = seriesPlaylist.url,
                            category = serialCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty()
                        )
                    }
                }
            }
            .onEach { stream ->
                buffer += stream
                if (buffer.size >= BUFFER_XTREAM_CAPACITY) {
                    mutex.withLock {
                        // check again
                        if (buffer.size >= BUFFER_XTREAM_CAPACITY) {
                            streamDao.insertOrReplaceAll(*buffer.toTypedArray())
                            buffer.clear()
                        }
                    }
                }
            }
            .onCompletion {
                // lock again
                mutex.withLock {
                    streamDao.insertOrReplaceAll(*buffer.toTypedArray())
                }
            }
            .launchIn(this)
    }

    override suspend fun refresh(url: String) = logger.sandBox {
        val playlist = checkNotNull(get(url)) { "Cannot find playlist: $url" }
        check(!playlist.fromLocal) { "refreshing is not needed for local storage playlist." }

        when (playlist.source) {
            DataSource.M3U -> {
                SubscriptionWorker.m3u(workManager, playlist.title, url)
            }

            DataSource.Xtream -> {
                val xtreamInput = XtreamInput.decodeFromPlaylistUrl(url)
                SubscriptionWorker.xtream(
                    workManager = workManager,
                    title = playlist.title,
                    url = url,
                    basicUrl = xtreamInput.basicUrl,
                    username = xtreamInput.username,
                    password = xtreamInput.password
                )
            }

            else -> throw IllegalStateException("Refresh data source ${playlist.source} is unsupported currently.")
        }
    }

    override suspend fun backupOrThrow(uri: Uri): Unit = withContext(ioDispatcher) {
        val json = Json {
            prettyPrint = false
        }
        val all = playlistDao.getAllWithStreams()
        context.contentResolver.openOutputStream(uri)?.use {
            val writer = it.bufferedWriter()
            all.forEach { (playlist, streams) ->
                if (playlist.fromLocal) {
                    logger.log("The playlist is from local storage, skipped. ($playlist)")
                    return@forEach
                }
                logger.sandBox {
                    val encodedPlaylist = json.encodeToString(playlist)
                    val wrappedPlaylist = BackupOrRestoreContracts.wrapPlaylist(encodedPlaylist)
                    writer.appendLine(wrappedPlaylist)
                }

                streams.forEach { stream ->
                    logger.sandBox {
                        val encodedStream = json.encodeToString(stream)
                        val wrappedStream = BackupOrRestoreContracts.wrapStream(encodedStream)
                        logger.log(wrappedStream)
                        writer.appendLine(wrappedStream)
                    }
                }
            }
            writer.flush()
        }
    }

    override suspend fun restoreOrThrow(uri: Uri) = logger.sandBox {
        withContext(ioDispatcher) {
            val json = Json {
                ignoreUnknownKeys = true
            }
            val mutex = Mutex()
            context.contentResolver.openInputStream(uri)?.use {
                val reader = it.bufferedReader()

                val streams = mutableListOf<Stream>()
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                    val encodedStream = BackupOrRestoreContracts.unwrapStream(line)
                    when {
                        encodedPlaylist != null -> logger.sandBox {
                            val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                            playlistDao.insertOrReplace(playlist)
                        }

                        encodedStream != null -> logger.sandBox {
                            val stream = json.decodeFromString<Stream>(encodedStream)
                            streams.add(stream)
                            if (streams.size >= BUFFER_RESTORE_CAPACITY) {
                                mutex.withLock {
                                    if (streams.size >= BUFFER_RESTORE_CAPACITY) {
                                        streamDao.insertOrReplaceAll(*streams.toTypedArray())
                                        streams.clear()
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
                mutex.withLock {
                    streamDao.insertOrReplaceAll(*streams.toTypedArray())
                }
            }
        }
    }

    override suspend fun pinOrUnpinCategory(url: String, category: String) = logger.sandBox {
        playlistDao.updatePinnedCategories(url) { prev ->
            if (category in prev) prev - category
            else prev + category
        }
    }

    override suspend fun hideOrUnhideCategory(url: String, category: String) = logger.sandBox {
        playlistDao.hideOrUnhideCategory(url, category)
    }

    override fun observeAll(): Flow<List<Playlist>> = playlistDao
        .observeAll()
        .catch {
            logger.log(it)
            emit(emptyList())
        }

    override fun observePlaylistUrls(): Flow<List<String>> = playlistDao
        .observePlaylistUrls()
        .catch {
            logger.log(it)
            emit(emptyList())
        }

    override fun observe(url: String): Flow<Playlist?> = playlistDao
        .observeByUrl(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override fun observeWithStreams(url: String): Flow<PlaylistWithStreams?> = playlistDao
        .observeByUrlWithStreams(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override suspend fun getWithStreams(url: String): PlaylistWithStreams? = logger.execute {
        playlistDao.getByUrlWithStreams(url)
    }

    override suspend fun get(url: String): Playlist? = logger.execute {
        playlistDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Playlist? = logger.execute {
        val playlist = playlistDao.getByUrl(url)
        streamDao.deleteByPlaylistUrl(url)
        playlist?.also {
            playlistDao.delete(it)
        }
    }

    override suspend fun onEditPlaylistTitle(url: String, title: String) = logger.sandBox {
        playlistDao.rename(url, title)
    }

    override suspend fun onEditPlaylistUserAgent(url: String, userAgent: String) = logger.sandBox {
        playlistDao.updateUserAgent(url, userAgent)
    }

    override fun observeAllCounts(): Flow<List<PlaylistWithCount>> =
        playlistDao.observeAllCounts()
            .catch { emit(emptyList()) }

    override suspend fun readEpisodesOrThrow(series: Stream): List<XtreamStreamInfo.Episode> {
        val playlist = checkNotNull(get(series.playlistUrl)) { "playlist is not exist" }
        val seriesInfo = xtreamParser.getSeriesInfoOrThrow(
            input = XtreamInput.decodeFromPlaylistUrl(playlist.url),
            seriesId = Url(series.url).pathSegments.last().toInt()
        )
        return seriesInfo.episodes.flatMap { it.value }
    }

    private val filenameWithTimezone: String get() = "File_${System.currentTimeMillis()}"

    // Modified with `inline`
    private inline fun Reader.forEachLine(action: (String) -> Unit): Unit =
        useLines { it.forEach(action) }

    private fun String.isSupportedNetworkUrl(): Boolean = startsWithAny(
        "http://",
        "https://",
        ignoreCase = true
    )

    private fun String.isSupportedAndroidUrl(): Boolean = startsWithAny(
        ContentResolver.SCHEME_FILE,
        ContentResolver.SCHEME_CONTENT,
        ignoreCase = true
    )

    private suspend fun String.actualUrl(): String {
        return when {
            isSupportedNetworkUrl() -> this
            isSupportedAndroidUrl() -> {
                val uri = Uri.parse(this)
                if (uri.scheme == ContentResolver.SCHEME_FILE) {
                    return uri.toString()
                }
                withContext(ioDispatcher) {
                    val resolver = context.contentResolver
                    val filename = uri.readFileName(resolver) ?: filenameWithTimezone
                    val content = uri.readFileContent(resolver).orEmpty()
                    val file = File(context.filesDir, filename)
                    file.writeText(content)

                    val newUrl = Uri.decode(file.toUri().toString())
                    playlistDao.updateUrl(this@actualUrl, newUrl)
                    newUrl
                }
            }

            else -> error("unsupported url scheme: $this")
        }
    }
}
