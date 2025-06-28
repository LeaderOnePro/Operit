package com.ai.assistance.operit.data.model

import android.os.Parcelable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.parcelize.Parcelize

@Parcelize
data class SerializableTypography(
    val displayLarge: SerializableTextStyle,
    val displayMedium: SerializableTextStyle,
    val displaySmall: SerializableTextStyle,
    val headlineLarge: SerializableTextStyle,
    val headlineMedium: SerializableTextStyle,
    val headlineSmall: SerializableTextStyle,
    val titleLarge: SerializableTextStyle,
    val titleMedium: SerializableTextStyle,
    val titleSmall: SerializableTextStyle,
    val bodyLarge: SerializableTextStyle,
    val bodyMedium: SerializableTextStyle,
    val bodySmall: SerializableTextStyle,
    val labelLarge: SerializableTextStyle,
    val labelMedium: SerializableTextStyle,
    val labelSmall: SerializableTextStyle
) : Parcelable

@Parcelize
data class SerializableTextStyle(
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val fontWeight: Int
) : Parcelable

fun Typography.toSerializable(): SerializableTypography {
    return SerializableTypography(
        displayLarge = this.displayLarge.toSerializable(),
        displayMedium = this.displayMedium.toSerializable(),
        displaySmall = this.displaySmall.toSerializable(),
        headlineLarge = this.headlineLarge.toSerializable(),
        headlineMedium = this.headlineMedium.toSerializable(),
        headlineSmall = this.headlineSmall.toSerializable(),
        titleLarge = this.titleLarge.toSerializable(),
        titleMedium = this.titleMedium.toSerializable(),
        titleSmall = this.titleSmall.toSerializable(),
        bodyLarge = this.bodyLarge.toSerializable(),
        bodyMedium = this.bodyMedium.toSerializable(),
        bodySmall = this.bodySmall.toSerializable(),
        labelLarge = this.labelLarge.toSerializable(),
        labelMedium = this.labelMedium.toSerializable(),
        labelSmall = this.labelSmall.toSerializable()
    )
}

fun TextStyle.toSerializable(): SerializableTextStyle {
    return SerializableTextStyle(
        fontSize = this.fontSize.value,
        lineHeight = this.lineHeight.value,
        letterSpacing = this.letterSpacing.value,
        fontWeight = this.fontWeight?.weight ?: FontWeight.Normal.weight
    )
}

fun SerializableTypography.toComposeTypography(): Typography {
    return Typography(
        displayLarge = this.displayLarge.toComposeTextStyle(),
        displayMedium = this.displayMedium.toComposeTextStyle(),
        displaySmall = this.displaySmall.toComposeTextStyle(),
        headlineLarge = this.headlineLarge.toComposeTextStyle(),
        headlineMedium = this.headlineMedium.toComposeTextStyle(),
        headlineSmall = this.headlineSmall.toComposeTextStyle(),
        titleLarge = this.titleLarge.toComposeTextStyle(),
        titleMedium = this.titleMedium.toComposeTextStyle(),
        titleSmall = this.titleSmall.toComposeTextStyle(),
        bodyLarge = this.bodyLarge.toComposeTextStyle(),
        bodyMedium = this.bodyMedium.toComposeTextStyle(),
        bodySmall = this.bodySmall.toComposeTextStyle(),
        labelLarge = this.labelLarge.toComposeTextStyle(),
        labelMedium = this.labelMedium.toComposeTextStyle(),
        labelSmall = this.labelSmall.toComposeTextStyle()
    )
}

fun SerializableTextStyle.toComposeTextStyle(): TextStyle {
    return TextStyle(
        fontSize = this.fontSize.sp,
        lineHeight = this.lineHeight.sp,
        letterSpacing = this.letterSpacing.sp,
        fontWeight = FontWeight(this.fontWeight),
        fontFamily = FontFamily.Default
    )
} 