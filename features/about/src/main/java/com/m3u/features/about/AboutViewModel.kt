package com.m3u.features.about

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.execute
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.EmptyMessage
import com.m3u.data.api.GithubApi
import com.m3u.data.parser.VersionCatalogParser
import com.m3u.features.about.model.Contributor
import com.m3u.features.about.model.toContributor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val api: GithubApi,
    private val parser: VersionCatalogParser,
    private val client: OkHttpClient,
    @Publisher.App private val publisher: Publisher,
    private val logger: Logger
) : BaseViewModel<Unit, Unit, EmptyMessage>(Unit) {
    private val _contributors: MutableStateFlow<List<Contributor>> =
        MutableStateFlow(emptyList())
    internal val contributors: StateFlow<List<Contributor>> = _contributors.asStateFlow()

    private val versionCatalog: MutableStateFlow<List<VersionCatalogParser.Entity>> =
        MutableStateFlow(emptyList())
    internal val libraries = versionCatalog
        .map { entities ->
            val versions = entities.filterIsInstance<VersionCatalogParser.Entity.Version>()
            entities.mapNotNull { prev ->
                when (prev) {
                    is VersionCatalogParser.Entity.Library -> {
                        prev.copy(
                            ref = versions.find { it.key == prev.ref }?.value ?: prev.ref
                        )
                    }

                    else -> null
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch parent@{
            launch {
                val users = logger.execute {
                    api.contributors(
                        publisher.author,
                        publisher.repository
                    )
                } ?: emptyList()
                _contributors.value = users
                    .map { it.toContributor() }
                    .sortedByDescending { it.contributions }
            }
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/realOxy/M3UAndroid/master/gradle/libs.versions.toml")
                .build()
            val response = withContext(Dispatchers.IO) {
                client
                    .newCall(request)
                    .execute()
            }
            val input = response.body?.byteStream()
            versionCatalog.update {
                input?.use { parser.execute(it) } ?: emptyList()
            }
        }
    }

    override fun onEvent(event: Unit) {

    }
}
