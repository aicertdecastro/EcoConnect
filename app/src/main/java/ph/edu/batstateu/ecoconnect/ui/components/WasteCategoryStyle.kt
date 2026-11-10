package ph.edu.batstateu.ecoconnect.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen100
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen700
import ph.edu.batstateu.ecoconnect.ui.theme.ResidualStrong
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclined
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPendingContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusSent
import ph.edu.batstateu.ecoconnect.ui.theme.TileAmber
import ph.edu.batstateu.ecoconnect.ui.theme.TileBlue
import ph.edu.batstateu.ecoconnect.ui.theme.TileTeal

/**
 * Visual style for each waste category. Entry names equal the guide's category ids
 * ([CategoryIds]) and are what gets stored in Room as `WasteItem.category`.
 */
enum class WasteCategory(
    val label: String,
    val icon: ImageVector,
    /** Light background for icon tiles. */
    val accent: Color,
    /** Strong color for headers and chips. */
    val strong: Color,
) {
    BIODEGRADABLE("Biodegradable", Icons.Outlined.Eco, EcoGreen100, EcoGreen700),
    RECYCLABLE("Recyclable", Icons.Outlined.Recycling, TileBlue, StatusSent),
    RESIDUAL("Residual", Icons.Outlined.Delete, TileTeal, ResidualStrong),
    SPECIAL("Special / Hazardous", Icons.Outlined.Warning, TileAmber, StatusDeclined),
    ;

    val id: String get() = name

    companion object {
        fun fromId(id: String): WasteCategory? = entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}

/** Style for the "not sure" guide result. */
object UnsureStyle {
    val icon: ImageVector = Icons.AutoMirrored.Outlined.HelpOutline
    val accent: Color = StatusPendingContainer
    val strong: Color = StatusPending
}
