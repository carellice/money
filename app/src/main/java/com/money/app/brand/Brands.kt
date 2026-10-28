package com.money.app.brand

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

class Brand(
    val key: String,
    val name: String,
    val hex: Long,
    /** SVG path on a 24x24 viewport (Simple Icons). */
    val path: String? = null,
    /** Fallback text badge for brands without a free logo. */
    val monogram: String? = null,
) {
    val color: Color get() = Color(hex)

    /** True when the brand color is too light to put a white logo on it. */
    val isLight: Boolean
        get() {
            val c = color
            return 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.7f
        }

    val vector: ImageVector? by lazy {
        val data = path ?: return@lazy null
        val nodes = PathParser().parsePathString(data).toNodes()
        ImageVector.Builder(name = key, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = nodes, fill = SolidColor(Color.White))
            .build()
    }
}

class BrandGroup(val name: String, val brands: List<Brand>)

object Brands {
    val groups: List<BrandGroup> get() = brandGroups
    private val byKey by lazy { brandGroups.flatMap { it.brands }.associateBy { it.key } }

    fun find(key: String?): Brand? = key?.let { byKey[it] }

    fun groupOf(key: String?): String? = brandGroups.firstOrNull { g -> g.brands.any { it.key == key } }?.name

    fun search(query: String): List<Brand> {
        val q = query.trim().lowercase()
        // Names starting with the query first, then word starts, then any match.
        return brandGroups.flatMap { it.brands }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedBy {
                val name = it.name.lowercase()
                when {
                    q.isEmpty() -> 0
                    name.startsWith(q) -> 0
                    name.split(' ', '+', '.').any { word -> word.startsWith(q) } -> 1
                    else -> 2
                }
            }
    }
}
