package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "color_pack",
    primaryKeys = ["argb", "dark"]
)
@Immutable
@Keep
data class ColorPack(
    @ColumnInfo("argb")
    val argb: Int,
    @ColumnInfo("dark")
    val isDark: Boolean,
    @ColumnInfo("name")
    val name: String,
)