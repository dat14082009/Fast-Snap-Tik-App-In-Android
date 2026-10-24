package com.stone.tiktoksave.ui.theme

import androidx.compose.ui.graphics.Color
import com.stone.tiktoksave.data.ThemeChoice

/**
 * Bảng token màu duy nhất của app. Màn hình chỉ dùng MaterialTheme.colorScheme,
 * không rải mã màu trong từng màn hình.
 */
object Brand {
    /** Vàng đậm làm nền cho giao diện Vàng-trắng. */
    val Yellow = Color(0xFFF9A825)

    /** Nâu đậm cùng tông vàng: dùng cho nút và chữ trên nền trắng để đủ độ tương phản. */
    val YellowInk = Color(0xFF3B2A00)
}

data class Palette(
    val isDark: Boolean,
    val darkStatusIcons: Boolean,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val error: Color
)

object Palettes {
    /** Trắng: nền sáng, chữ tối. */
    private val White = Palette(
        isDark = false,
        darkStatusIcons = true,
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1B1B1B),
        surface = Color(0xFFF3F3F3),
        onSurface = Color(0xFF1B1B1B),
        primary = Color(0xFF1B1B1B),
        onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFFC8C8C8),
        error = Color(0xFFB3261E)
    )

    /** Đen: nền tối, chữ sáng. */
    private val Black = Palette(
        isDark = true,
        darkStatusIcons = false,
        background = Color(0xFF000000),
        onBackground = Color(0xFFF2F2F2),
        surface = Color(0xFF1C1C1C),
        onSurface = Color(0xFFF2F2F2),
        primary = Color(0xFFF2F2F2),
        onPrimary = Color(0xFF111111),
        outline = Color(0xFF5A5A5A),
        error = Color(0xFFFF8A80)
    )

    /** Vàng-trắng: nền vàng, chữ trắng; thẻ nội dung trắng, nút nâu đậm chữ trắng. */
    private val YellowWhite = Palette(
        isDark = false,
        darkStatusIcons = true,
        background = Brand.Yellow,
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        onSurface = Brand.YellowInk,
        primary = Brand.YellowInk,
        onPrimary = Color(0xFFFFFFFF),
        outline = Brand.YellowInk,
        error = Color(0xFFB3261E)
    )

    fun of(choice: ThemeChoice): Palette = when (choice) {
        ThemeChoice.WHITE -> White
        ThemeChoice.BLACK -> Black
        ThemeChoice.YELLOW_WHITE -> YellowWhite
    }
}
