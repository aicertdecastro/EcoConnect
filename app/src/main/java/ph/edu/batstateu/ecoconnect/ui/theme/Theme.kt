package ph.edu.batstateu.ecoconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = EcoGreen700,
    onPrimary = Color.White,
    primaryContainer = EcoGreen100,
    onPrimaryContainer = EcoGreen900,
    secondary = EcoGreen500,
    onSecondary = Color.White,
    secondaryContainer = EcoGreen50,
    onSecondaryContainer = EcoGreen900,
    tertiary = EcoGreen800,
    onTertiary = Color.White,
    background = EcoBackground,
    onBackground = EcoInk,
    surface = EcoSurface,
    onSurface = EcoInk,
    surfaceVariant = EcoGreen50,
    onSurfaceVariant = EcoInkMuted,
    surfaceContainer = EcoSurface,
    surfaceContainerHigh = EcoSurface,
    surfaceContainerHighest = EcoGreen50,
    outline = EcoOutline,
    outlineVariant = EcoGreen100,
)

private val DarkColors = darkColorScheme(
    primary = EcoGreen300,
    onPrimary = EcoGreen900,
    primaryContainer = EcoGreen800,
    onPrimaryContainer = EcoGreen100,
    secondary = EcoGreen300,
    onSecondary = EcoGreen900,
    secondaryContainer = EcoDarkSurfaceVariant,
    onSecondaryContainer = EcoGreen100,
    tertiary = EcoGreen500,
    background = EcoDarkBackground,
    onBackground = EcoDarkOnSurface,
    surface = EcoDarkSurface,
    onSurface = EcoDarkOnSurface,
    surfaceVariant = EcoDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB5C1AF),
    surfaceContainer = EcoDarkSurface,
    surfaceContainerHigh = EcoDarkSurfaceVariant,
    surfaceContainerHighest = EcoDarkSurfaceVariant,
    outline = Color(0xFF55604F),
    outlineVariant = Color(0xFF3A4536),
)

/** EcoConnect theme. Dynamic color is intentionally off so the brand greens always show. */
@Composable
fun EcoConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EcoTypography,
        shapes = EcoShapes,
        content = content,
    )
}
