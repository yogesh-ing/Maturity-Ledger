package com.afec.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Typography scale — PRD §06 (Roboto; sizes/weights from the HTML spec).
val AfecTypography = Typography(
    displayLarge = TextStyle( // 36/44 onboarding hero
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle( // 48/48 "XX Days Left" hero numeral
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle( // 28/36 screen titles
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    titleLarge = TextStyle( // 22/28 card group headers
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle( // 16/24 card titles
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle( // 14/20 compact titles
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle( // 16/24 primary body / inputs
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle( // 14/20 secondary text
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle( // 12/16 tertiary text
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle( // 14/20 buttons, chips
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle( // 12/16 badges
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle( // 11/16 overline / badges
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

// Corner radius — PRD §06: sm 8 / md 12 / lg 16 / xl 24 / full pills.
val AfecShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
