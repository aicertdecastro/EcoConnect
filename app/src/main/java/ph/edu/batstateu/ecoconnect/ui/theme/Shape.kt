package ph.edu.batstateu.ecoconnect.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val EcoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded shape for CTAs and chips. */
val PillShape = RoundedCornerShape(percent = 50)

/** Curved bottom edge of the green home header. */
val HeaderShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
