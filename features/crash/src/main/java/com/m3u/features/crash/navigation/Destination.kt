package com.m3u.features.crash.navigation

internal sealed class Destination {
    data object List : Destination()
    data class Detail(val path: String) : Destination()
}
