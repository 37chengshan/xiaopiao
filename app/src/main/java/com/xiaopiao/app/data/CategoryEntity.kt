package com.xiaopiao.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.Direction

/**
 * One row per category, built-ins included (tt#70/71): order and 隐藏 are user state, so they have to
 * live in the database rather than in the enum. Built-in rows are seeded by the v3 migration and are
 * re-created by [CategoryDao.seedMissing] if one ever disappears.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    @ColumnInfo(name = "iconKey") val iconKey: String,
    val hue: String,
    /** `OUT` / `IN`; NULL = offered for both. */
    val direction: String?,
    @ColumnInfo(name = "builtIn") val builtIn: Boolean,
    val hidden: Boolean,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int,
)

fun CategoryDef.toEntity() = CategoryEntity(
    id = id, label = label, iconKey = iconKey, hue = hue,
    direction = direction?.name, builtIn = builtIn, hidden = hidden, sortOrder = sortOrder,
)

fun CategoryEntity.toDomain() = CategoryDef(
    id = id, label = label, iconKey = iconKey, hue = hue,
    direction = direction?.let { runCatching { Direction.valueOf(it) }.getOrNull() },
    builtIn = builtIn, hidden = hidden, sortOrder = sortOrder,
)
