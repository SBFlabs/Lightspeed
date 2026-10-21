package com.sbf.lightspeed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class LightspeedColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val dialogSurface: Color,
    val borderSubtle: Color,
    val accentOk: Color,
    val accentWarn: Color,
    val accentGold: Color,
    val accentInfo: Color,
    val accentDanger: Color,
    val accentAlert: Color
)

val DarkLightspeedColors = LightspeedColors(
    textPrimary = Color.White,
    textSecondary = Color.LightGray,
    textDisabled = Color.Gray,
    dialogSurface = Color(0xF012141A),
    borderSubtle = Color(0x33FFFFFF),
    accentOk = Color(0xFF00E676),
    accentWarn = Color(0xFFFFB300),
    accentGold = Color(0xFFFFD700),
    accentInfo = Color(0xFF00E5FF),
    accentDanger = Color(0xFFFF5252),
    accentAlert = Color(0xFFFF9800)
)

val LightLightspeedColors = LightspeedColors(
    textPrimary = Color(0xFF191C1D),
    textSecondary = Color(0xFF3F484A),
    textDisabled = Color(0xFF6F797A),
    dialogSurface = Color(0xF0FBFDFE),
    borderSubtle = Color(0x33000000),
    accentOk = Color(0xFF2E7D32),
    accentWarn = Color(0xFFB26A00),
    accentGold = Color(0xFF8D6E00),
    accentInfo = Color(0xFF006874),
    accentDanger = Color(0xFFC62828),
    accentAlert = Color(0xFFBF360C)
)

val LocalLightspeedColors = staticCompositionLocalOf { DarkLightspeedColors }

object LightspeedStyle {
    val colors: LightspeedColors
        @Composable get() = LocalLightspeedColors.current

    object FontSize {
        val caption = 10.sp
        val label = 11.sp
        val body = 12.sp
        val bodyLarge = 13.sp
        val title = 15.sp
    }

    object Shapes {
        val small = RoundedCornerShape(8.dp)
        val medium = RoundedCornerShape(12.dp)
        val large = RoundedCornerShape(16.dp)
    }
}

object LightspeedTokens {
    // Semantic Status & Accent Colors
    val StatusDanger = Color(0xFFFF3D00)
    val StatusDangerAlt = Color(0xFFFF3B30)
    val StatusDangerFlash = Color(0xFFFF453A)
    val StatusErrorSoft = Color(0xFFFF6B6B)
    val StatusErrorPulse = Color(0xFFEF4444)
    val StatusWarning = Color(0xFFFFB300)
    val StatusWarningOrange = Color(0xFFFFB74D)
    val StatusAmber = Color(0xFFD97706)
    val StatusCautionAmber = Color(0xFFFF9100)
    val StatusOk = Color(0xFF00E676)
    val StatusOkSoft = Color(0xFF81C784)
    val StatusGold = Color(0xFFFFD54F)
    val StatusGoldAlt = Color(0xFFFFDD00)
    val StatusGoldDeep = Color(0xFFFFA000)
    val StatusGoldShadow = Color(0xFF6D4C41)
    val StatusGoldBright = Color(0xFFFFF176)
    val StatusGoldBadge = Color(0xFFFFE082)
    val StatusGoldPin = Color(0x66FFD700)
    val StatusGoldPinSubtle = Color(0x18FFD700)
    val StatusTeal = Color(0xFF10A37F)
    val StatusCyan = Color(0xFF06B6D4)
    val StatusCyanSoft = Color(0xFF80DEEA)
    val StatusBlue = Color(0xFF3B82F6)
    val StatusSky = Color(0xFF81D4FA)
    val StatusSlate = Color(0xFF94A3B8)
    val StatusCrimson = Color(0xFFEA4335)

    // Dark Tactical Surfaces & Panels
    val TacticalDeepBlack = Color(0xFF0F1115)
    val TacticalSurfaceDeep = Color(0xFF04060A)
    val TacticalSurfaceDeepAlt = Color(0xFF070B12)
    val TacticalSurfaceBase = Color(0xFF070A0F)
    val TacticalSurfaceDark = Color(0xFF090D14)
    val TacticalSurfaceCard = Color(0xFF0E131C)
    val TacticalSurfacePanel = Color(0xFF0B1018)
    val TacticalSurfaceContainer = Color(0xFF0F141F)
    val TacticalSurfaceModal = Color(0xFF121824)
    val TacticalSurfaceElevated = Color(0xFF1B1F2B)
    val TacticalSurfaceTerminal = Color(0xFF0D1117)
    val TacticalSurfaceBorder = Color(0xFF141C28)
    val TacticalSurfaceBorderAlt = Color(0xFF1E2838)
    val TacticalNoticeDark = Color(0xFF263238)
    val TacticalNoticeWarm = Color(0xFF332010)
    val TacticalDeepSlate = Color(0xFF10121C)
    val TacticalDeepNavy = Color(0xFF10131A)
    val TacticalBlueprintBase = Color(0xFF141724)
    val TacticalBlueprintSub = Color(0xFF1B1E2B)
    val TacticalMooringSurface = Color(0xF0161922)
    val TacticalMenuSurface = Color(0xFF1E1E1E)
    val TacticalSubtleWhite = Color(0x22FFFFFF)
}
