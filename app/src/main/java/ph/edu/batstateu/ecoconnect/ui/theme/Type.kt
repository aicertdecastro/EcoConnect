package ph.edu.batstateu.ecoconnect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()
private val Sans = FontFamily.SansSerif

val EcoTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Sans),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Sans),
    bodySmall = Base.bodySmall.copy(fontFamily = Sans),
    labelLarge = Base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontFamily = Sans),
)

/** Large number used in stat cards. */
val StatValueStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)
