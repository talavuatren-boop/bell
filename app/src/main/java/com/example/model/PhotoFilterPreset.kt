package com.example.model

import androidx.compose.ui.graphics.Color

enum class FilterCategory(val label: String) {
    BASE("Gốc"),
    LEICA("Leica"),
    FILM("Máy ảnh")
}

enum class FilterSampleSubject {
    STREET_GOLDEN,
    PORTRAIT_SKIN,
    CAFE_ANALOG,
    SCENIC_BLUE
}

data class ColorGradeSpec(
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val exposureBias: Float = 0f,
    val shadowFade: Float = 0f,
    val rGain: Float = 1f,
    val gGain: Float = 1f,
    val bGain: Float = 1f,
    val rWarmBias: Float = 0f,
    val gWarmBias: Float = 0f,
    val bWarmBias: Float = 0f,
    val rgCross: Float = 0f,
    val gbCross: Float = 0f,
    val isMonochrome: Boolean = false
)

enum class PhotoFilterPreset(
    val label: String,
    val category: FilterCategory = FilterCategory.FILM,
    val isLeica: Boolean = false,
    val isWarm: Boolean = false,
    val swatchColors: List<Color> = listOf(Color(0xFFECEFF4), Color(0xFF9BA3B0)),
    val grade: ColorGradeSpec? = null
) {
    // --- Base ---
    NATURAL(
        label = "Gốc",
        category = FilterCategory.BASE,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFFECEFF4), Color(0xFF9BA3B0))
    ),

    // --- 6 Original Leica Signatures ---
    LEICA_VIBRANT(
        label = "Leica Vivid",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = false,
        swatchColors = listOf(Color(0xFFFF4B3E), Color(0xFFE58E26), Color(0xFF1F7AE0))
    ),
    LEICA_AUTHENTIC(
        label = "Leica Nat",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = true,
        swatchColors = listOf(Color(0xFFB57C50), Color(0xFF3E444E), Color(0xFF1A1B20))
    ),
    LEICA_BW_NAT(
        label = "Leica BW",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = false,
        swatchColors = listOf(Color(0xFFE0E2E5), Color(0xFF636870), Color(0xFF23262A))
    ),
    LEICA_BW_HC(
        label = "Leica HC",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = false,
        swatchColors = listOf(Color(0xFFFFFFFF), Color(0xFF202020), Color(0xFF000000))
    ),
    LEICA_SEPIA(
        label = "Leica Sepia",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = true,
        swatchColors = listOf(Color(0xFFE0C097), Color(0xFF8C6239), Color(0xFF3E2714))
    ),
    LEICA_BLUE(
        label = "Leica Blue",
        category = FilterCategory.LEICA,
        isLeica = true,
        isWarm = false,
        swatchColors = listOf(Color(0xFF7FD8FF), Color(0xFF1D4E89), Color(0xFF0E1B33))
    ),

    // --- 9 Original Camera / Film Filters ---
    PORTRA(
        label = "Portra 400",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = true,
        swatchColors = listOf(Color(0xFFFFD5B8), Color(0xFFE29D7D), Color(0xFF7CA7BA))
    ),
    FUJI_PRO(
        label = "Fuji Pro",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFF7ED6DF), Color(0xFF22A6B3), Color(0xFF2B4C59))
    ),
    VIVID(
        label = "Rực rỡ",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFFFF5E57), Color(0xFFFF9F43))
    ),
    WARM(
        label = "Ấm áp",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = true,
        swatchColors = listOf(Color(0xFFFFCC70), Color(0xFFC850C0))
    ),
    COOL(
        label = "Mát lạnh",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFF4BCFFA), Color(0xFF3B3B98))
    ),
    FILM(
        label = "Phim 35mm",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFF55E6C1), Color(0xFF2C3A47))
    ),
    VINTAGE(
        label = "Cổ điển",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = true,
        swatchColors = listOf(Color(0xFFD1A36C), Color(0xFF6D4C3D))
    ),
    CINEMATIC(
        label = "Điện ảnh",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFF0FB9B1), Color(0xFFFF7F50))
    ),
    BW(
        label = "Đen trắng",
        category = FilterCategory.FILM,
        isLeica = false,
        isWarm = false,
        swatchColors = listOf(Color(0xFFBDC3C7), Color(0xFF2C3E50))
    ),

    // =========================================================================
    // 80 NEW LEICA FILTERS (Warm-rich signature collection)
    // Group A (1-20): Leica Golden Hour & Amber
    // =========================================================================
    LEICA_AMBER_GOLD(
        "Leica Hổ Phách", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF6B93B), Color(0xFFB7540B)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.09f, rGain = 1.10f, gGain = 1.03f, bGain = 0.87f, rWarmBias = 11f, gWarmBias = 5f, bWarmBias = -10f, rgCross = 0.03f)
    ),
    LEICA_HONEY_SUN(
        "Leica Mật Ong", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFAD390), Color(0xFFCC7722)),
        ColorGradeSpec(saturation = 1.08f, contrast = 1.05f, exposureBias = 4f, rGain = 1.09f, gGain = 1.04f, bGain = 0.88f, rWarmBias = 10f, gWarmBias = 6f, bWarmBias = -8f)
    ),
    LEICA_GOLDEN_HOUR(
        "Leica Giờ Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFB142), Color(0xFF8E3B16)),
        ColorGradeSpec(saturation = 1.16f, contrast = 1.10f, rGain = 1.13f, gGain = 1.03f, bGain = 0.84f, rWarmBias = 14f, gWarmBias = 5f, bWarmBias = -12f, rgCross = 0.04f)
    ),
    LEICA_SUNSET_GLOW(
        "Leica Hoàng Hôn", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFF793F), Color(0xFF7D2A24)),
        ColorGradeSpec(saturation = 1.18f, contrast = 1.12f, rGain = 1.15f, gGain = 1.00f, bGain = 0.85f, rWarmBias = 15f, gWarmBias = 2f, bWarmBias = -9f)
    ),
    LEICA_WARM_SUMMILUX(
        "Summilux Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE6A15C), Color(0xFF2E2722)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.11f, shadowFade = 3f, rGain = 1.08f, gGain = 1.02f, bGain = 0.90f, rWarmBias = 8f, gWarmBias = 3f, bWarmBias = -7f, rgCross = 0.02f)
    ),
    LEICA_NOCTILUX_WARM(
        "Noctilux Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD18E4B), Color(0xFF191512)),
        ColorGradeSpec(saturation = 0.98f, contrast = 1.16f, exposureBias = -3f, rGain = 1.09f, gGain = 1.01f, bGain = 0.88f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -8f)
    ),
    LEICA_APO_AMBER(
        "APO Hổ Phách", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFECA754), Color(0xFF3A2D24)),
        ColorGradeSpec(saturation = 1.10f, contrast = 1.13f, rGain = 1.09f, gGain = 1.03f, bGain = 0.89f, rWarmBias = 9f, gWarmBias = 4f, bWarmBias = -7f)
    ),
    LEICA_M11_WARM(
        "Leica M11 Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE0A96D), Color(0xFF282C34)),
        ColorGradeSpec(saturation = 1.06f, contrast = 1.08f, rGain = 1.07f, gGain = 1.02f, bGain = 0.91f, rWarmBias = 7f, gWarmBias = 3f, bWarmBias = -6f)
    ),
    LEICA_Q3_GOLDEN(
        "Leica Q3 Nắng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF3BA6B), Color(0xFF3E3328)),
        ColorGradeSpec(saturation = 1.11f, contrast = 1.07f, exposureBias = 3f, rGain = 1.08f, gGain = 1.04f, bGain = 0.90f, rWarmBias = 9f, gWarmBias = 5f, bWarmBias = -7f)
    ),
    LEICA_SL3_SOLAR(
        "Leica SL3 Nắng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFC069), Color(0xFF47362B)),
        ColorGradeSpec(saturation = 1.14f, contrast = 1.10f, rGain = 1.11f, gGain = 1.04f, bGain = 0.88f, rWarmBias = 11f, gWarmBias = 5f, bWarmBias = -8f)
    ),
    LEICA_BRASS_PATINA(
        "Leica Đồng Thau", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFCFA256), Color(0xFF3C3323)),
        ColorGradeSpec(saturation = 0.94f, contrast = 1.12f, shadowFade = 5f, rGain = 1.08f, gGain = 1.03f, bGain = 0.84f, rWarmBias = 8f, gWarmBias = 5f, bWarmBias = -10f)
    ),
    LEICA_CHAMPAGNE(
        "Leica Sâm Panh", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF5E1C8), Color(0xFF8C735B)),
        ColorGradeSpec(saturation = 0.95f, contrast = 1.02f, exposureBias = 6f, shadowFade = 4f, rGain = 1.06f, gGain = 1.03f, bGain = 0.92f, rWarmBias = 8f, gWarmBias = 5f, bWarmBias = -4f)
    ),
    LEICA_SAFFRON_M(
        "Leica Nghệ Tây", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF29D38), Color(0xFF703B14)),
        ColorGradeSpec(saturation = 1.17f, contrast = 1.08f, rGain = 1.12f, gGain = 1.03f, bGain = 0.82f, rWarmBias = 13f, gWarmBias = 5f, bWarmBias = -12f, rgCross = 0.04f)
    ),
    LEICA_CARAMEL_35(
        "Leica Caramel", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD48C54), Color(0xFF4E2F1B)),
        ColorGradeSpec(saturation = 1.02f, contrast = 1.09f, shadowFade = 6f, rGain = 1.09f, gGain = 1.00f, bGain = 0.85f, rWarmBias = 10f, gWarmBias = 3f, bWarmBias = -9f)
    ),
    LEICA_MARIGOLD(
        "Leica Cúc Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFBD59), Color(0xFF9C5E1A)),
        ColorGradeSpec(saturation = 1.19f, contrast = 1.06f, rGain = 1.11f, gGain = 1.05f, bGain = 0.84f, rWarmBias = 12f, gWarmBias = 7f, bWarmBias = -10f)
    ),
    LEICA_OCHRE_STREET(
        "Leica Phố Đất", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC98D4F), Color(0xFF332920)),
        ColorGradeSpec(saturation = 0.92f, contrast = 1.14f, rGain = 1.08f, gGain = 1.01f, bGain = 0.86f, rWarmBias = 8f, gWarmBias = 3f, bWarmBias = -9f)
    ),
    LEICA_WHEAT_FIELD(
        "Leica Lúa Chín", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE7C77A), Color(0xFF6B5B35)),
        ColorGradeSpec(saturation = 1.05f, contrast = 1.04f, exposureBias = 4f, rGain = 1.07f, gGain = 1.05f, bGain = 0.87f, rWarmBias = 9f, gWarmBias = 6f, bWarmBias = -8f)
    ),
    LEICA_SOLSTICE(
        "Leica Hạ Chí", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFA84C), Color(0xFF543322)),
        ColorGradeSpec(saturation = 1.15f, contrast = 1.11f, rGain = 1.12f, gGain = 1.03f, bGain = 0.85f, rWarmBias = 13f, gWarmBias = 5f, bWarmBias = -10f)
    ),
    LEICA_DAWN_GLINT(
        "Leica Bình Minh", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFCAA2), Color(0xFF7B574C)),
        ColorGradeSpec(saturation = 1.06f, contrast = 1.03f, exposureBias = 7f, rGain = 1.08f, gGain = 1.02f, bGain = 0.92f, rWarmBias = 11f, gWarmBias = 4f, bWarmBias = -3f)
    ),
    LEICA_SUNLIT_STONE(
        "Leica Đá Nắng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD8B892), Color(0xFF4F463D)),
        ColorGradeSpec(saturation = 0.89f, contrast = 1.10f, shadowFade = 4f, rGain = 1.07f, gGain = 1.02f, bGain = 0.89f, rWarmBias = 7f, gWarmBias = 3f, bWarmBias = -7f)
    ),

    // =========================================================================
    // Group B (21-40): Leica Terracotta, Copper & Warm Portrait Skin
    // =========================================================================
    LEICA_COPPER_M(
        "Leica Đồng Đỏ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD9724B), Color(0xFF42231B)),
        ColorGradeSpec(saturation = 1.05f, contrast = 1.12f, rGain = 1.12f, gGain = 0.99f, bGain = 0.86f, rWarmBias = 12f, gWarmBias = 1f, bWarmBias = -8f)
    ),
    LEICA_TERRACOTTA(
        "Leica Đất Nung", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFCC6B49), Color(0xFF5B3126)),
        ColorGradeSpec(saturation = 1.03f, contrast = 1.08f, shadowFade = 5f, rGain = 1.11f, gGain = 0.98f, bGain = 0.87f, rWarmBias = 11f, gWarmBias = 2f, bWarmBias = -7f)
    ),
    LEICA_BRONZE_LUX(
        "Leica Đồng Hun", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFAE7747), Color(0xFF2B2018)),
        ColorGradeSpec(saturation = 0.91f, contrast = 1.15f, exposureBias = -2f, rGain = 1.09f, gGain = 1.00f, bGain = 0.84f, rWarmBias = 9f, gWarmBias = 2f, bWarmBias = -10f)
    ),
    LEICA_PEACH_SKIN(
        "Leica Da Đào", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFC1A6), Color(0xFFB36A5E)),
        ColorGradeSpec(saturation = 1.03f, contrast = 1.02f, exposureBias = 6f, shadowFade = 4f, rGain = 1.08f, gGain = 1.02f, bGain = 0.94f, rWarmBias = 10f, gWarmBias = 4f, bWarmBias = -2f, rgCross = 0.02f)
    ),
    LEICA_ROSE_GOLD(
        "Leica Vàng Hồng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE8A598), Color(0xFF6B4244)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.04f, exposureBias = 4f, rGain = 1.09f, gGain = 0.99f, bGain = 0.94f, rWarmBias = 10f, gWarmBias = 1f, bWarmBias = -1f)
    ),
    LEICA_APRICOT_50(
        "Leica Mơ Chín", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF6A87E), Color(0xFF8C4E35)),
        ColorGradeSpec(saturation = 1.09f, contrast = 1.05f, exposureBias = 3f, rGain = 1.10f, gGain = 1.02f, bGain = 0.89f, rWarmBias = 11f, gWarmBias = 4f, bWarmBias = -6f)
    ),
    LEICA_CORAL_DUSK(
        "Leica San Hô", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFF866B), Color(0xFF633238)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.07f, rGain = 1.12f, gGain = 0.99f, bGain = 0.90f, rWarmBias = 12f, gWarmBias = 1f, bWarmBias = -4f)
    ),
    LEICA_RUST_VINTAGE(
        "Leica Gỉ Sét", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFB95A3A), Color(0xFF3A231D)),
        ColorGradeSpec(saturation = 0.93f, contrast = 1.13f, shadowFade = 7f, rGain = 1.11f, gGain = 0.97f, bGain = 0.85f, rWarmBias = 10f, gWarmBias = 0f, bWarmBias = -8f)
    ),
    LEICA_SIENNA_PRO(
        "Leica Sienna", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC86D44), Color(0xFF472C20)),
        ColorGradeSpec(saturation = 1.01f, contrast = 1.10f, rGain = 1.10f, gGain = 0.99f, bGain = 0.86f, rWarmBias = 10f, gWarmBias = 2f, bWarmBias = -8f)
    ),
    LEICA_CLAY_WARM(
        "Leica Gốm Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD0957A), Color(0xFF59423A)),
        ColorGradeSpec(saturation = 0.90f, contrast = 1.05f, shadowFade = 8f, rGain = 1.07f, gGain = 1.00f, bGain = 0.90f, rWarmBias = 8f, gWarmBias = 2f, bWarmBias = -4f)
    ),
    LEICA_BLUSH_LUX(
        "Leica Má Hồng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF3B3AE), Color(0xFF7D4E57)),
        ColorGradeSpec(saturation = 1.02f, contrast = 1.03f, exposureBias = 5f, rGain = 1.08f, gGain = 1.00f, bGain = 0.95f, rWarmBias = 9f, gWarmBias = 2f, bWarmBias = 0f)
    ),
    LEICA_WARM_SKIN(
        "Leica Chân Dung", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF1C0A1), Color(0xFF705141)),
        ColorGradeSpec(saturation = 1.01f, contrast = 1.04f, exposureBias = 4f, rGain = 1.07f, gGain = 1.02f, bGain = 0.92f, rWarmBias = 8f, gWarmBias = 4f, bWarmBias = -4f, rgCross = 0.02f)
    ),
    LEICA_CINNAMON_M(
        "Leica Quế Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC57B57), Color(0xFF44281E)),
        ColorGradeSpec(saturation = 0.99f, contrast = 1.11f, shadowFade = 4f, rGain = 1.10f, gGain = 1.00f, bGain = 0.85f, rWarmBias = 11f, gWarmBias = 2f, bWarmBias = -9f)
    ),
    LEICA_SANDSTONE(
        "Leica Sa Thạch", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE0BA93), Color(0xFF6B5340)),
        ColorGradeSpec(saturation = 0.94f, contrast = 1.06f, exposureBias = 3f, shadowFade = 5f, rGain = 1.08f, gGain = 1.02f, bGain = 0.88f, rWarmBias = 9f, gWarmBias = 4f, bWarmBias = -7f)
    ),
    LEICA_AUTUMN_LEAF(
        "Leica Lá Thu", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE67E22), Color(0xFF6E2C1B)),
        ColorGradeSpec(saturation = 1.16f, contrast = 1.10f, rGain = 1.14f, gGain = 1.00f, bGain = 0.82f, rWarmBias = 13f, gWarmBias = 2f, bWarmBias = -11f)
    ),
    LEICA_MAPLE_WARM(
        "Leica Phong Đỏ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD35400), Color(0xFF511E17)),
        ColorGradeSpec(saturation = 1.14f, contrast = 1.12f, rGain = 1.15f, gGain = 0.97f, bGain = 0.84f, rWarmBias = 14f, gWarmBias = 0f, bWarmBias = -9f)
    ),
    LEICA_BURNT_UMBER(
        "Leica Nâu Cháy", FilterCategory.LEICA, true, true,
        listOf(Color(0xFF9E5A3C), Color(0xFF291812)),
        ColorGradeSpec(saturation = 0.88f, contrast = 1.16f, exposureBias = -3f, rGain = 1.09f, gGain = 0.98f, bGain = 0.84f, rWarmBias = 9f, gWarmBias = 1f, bWarmBias = -9f)
    ),
    LEICA_VELVET_PEACH(
        "Leica Nhung Đào", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF4B69C), Color(0xFF5E3A36)),
        ColorGradeSpec(saturation = 0.97f, contrast = 1.02f, shadowFade = 9f, rGain = 1.08f, gGain = 1.01f, bGain = 0.91f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -3f)
    ),
    LEICA_TANGERINE_LITE(
        "Leica Quýt Ngọt", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFF9A5A), Color(0xFF883E1E)),
        ColorGradeSpec(saturation = 1.15f, contrast = 1.06f, exposureBias = 3f, rGain = 1.13f, gGain = 1.02f, bGain = 0.85f, rWarmBias = 12f, gWarmBias = 4f, bWarmBias = -9f)
    ),
    LEICA_RED_EARTH(
        "Leica Đất Đỏ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFBF543B), Color(0xFF3E1C19)),
        ColorGradeSpec(saturation = 1.06f, contrast = 1.13f, rGain = 1.13f, gGain = 0.97f, bGain = 0.86f, rWarmBias = 12f, gWarmBias = 0f, bWarmBias = -8f)
    ),

    // =========================================================================
    // Group C (41-60): Leica Café, Warm Film & Candlelight
    // =========================================================================
    LEICA_ESPRESSO(
        "Leica Espresso", FilterCategory.LEICA, true, true,
        listOf(Color(0xFF8E6448), Color(0xFF1F1612)),
        ColorGradeSpec(saturation = 0.86f, contrast = 1.18f, exposureBias = -4f, rGain = 1.08f, gGain = 0.99f, bGain = 0.85f, rWarmBias = 8f, gWarmBias = 2f, bWarmBias = -8f)
    ),
    LEICA_ROASTED_M(
        "Leica Rang Mộc", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFAC7953), Color(0xFF332318)),
        ColorGradeSpec(saturation = 0.92f, contrast = 1.12f, shadowFade = 5f, rGain = 1.09f, gGain = 1.00f, bGain = 0.86f, rWarmBias = 9f, gWarmBias = 2f, bWarmBias = -8f)
    ),
    LEICA_WARM_LATTE(
        "Leica Latte Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD9B89C), Color(0xFF6B5344)),
        ColorGradeSpec(saturation = 0.90f, contrast = 1.01f, exposureBias = 5f, shadowFade = 10f, rGain = 1.07f, gGain = 1.02f, bGain = 0.90f, rWarmBias = 8f, gWarmBias = 4f, bWarmBias = -4f)
    ),
    LEICA_TOASTED_OAK(
        "Leica Gỗ Sồi", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFBA8D63), Color(0xFF433224)),
        ColorGradeSpec(saturation = 0.95f, contrast = 1.09f, shadowFade = 4f, rGain = 1.08f, gGain = 1.01f, bGain = 0.87f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -8f)
    ),
    LEICA_CANDLE_28(
        "Leica Ánh Nến", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFBC6B), Color(0xFF3E2212)),
        ColorGradeSpec(saturation = 1.08f, contrast = 1.10f, exposureBias = -2f, rGain = 1.15f, gGain = 1.03f, bGain = 0.79f, rWarmBias = 15f, gWarmBias = 5f, bWarmBias = -14f)
    ),
    LEICA_TUNGSTEN_WARM(
        "Leica Đèn Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFFFD185), Color(0xFF5E4323)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.08f, rGain = 1.12f, gGain = 1.04f, bGain = 0.83f, rWarmBias = 12f, gWarmBias = 6f, bWarmBias = -11f)
    ),
    LEICA_CAFE_VIENNA(
        "Leica Vienna", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFCBA079), Color(0xFF47372E)),
        ColorGradeSpec(saturation = 0.93f, contrast = 1.07f, shadowFade = 6f, rGain = 1.08f, gGain = 1.01f, bGain = 0.89f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -5f)
    ),
    LEICA_COGNAC_M(
        "Leica Cognac", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFB86B35), Color(0xFF331B0E)),
        ColorGradeSpec(saturation = 1.03f, contrast = 1.14f, rGain = 1.12f, gGain = 1.00f, bGain = 0.82f, rWarmBias = 12f, gWarmBias = 2f, bWarmBias = -11f)
    ),
    LEICA_CHESTNUT(
        "Leica Hạt Dẻ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFA66646), Color(0xFF362119)),
        ColorGradeSpec(saturation = 0.94f, contrast = 1.11f, shadowFade = 5f, rGain = 1.09f, gGain = 0.99f, bGain = 0.86f, rWarmBias = 10f, gWarmBias = 2f, bWarmBias = -8f)
    ),
    LEICA_TEAK_WOOD(
        "Leica Gỗ Tếch", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC18C5D), Color(0xFF463222)),
        ColorGradeSpec(saturation = 0.96f, contrast = 1.09f, rGain = 1.09f, gGain = 1.01f, bGain = 0.86f, rWarmBias = 10f, gWarmBias = 3f, bWarmBias = -8f)
    ),
    LEICA_KODACHROME_W(
        "Leica Chrome Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE58F4E), Color(0xFF283444)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.15f, rGain = 1.11f, gGain = 1.01f, bGain = 0.89f, rWarmBias = 10f, gWarmBias = 3f, bWarmBias = -7f, rgCross = 0.03f)
    ),
    LEICA_PORTRA_WARM(
        "Leica Portra Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF3C6A5), Color(0xFF7A685F)),
        ColorGradeSpec(saturation = 1.03f, contrast = 1.03f, exposureBias = 5f, shadowFade = 5f, rGain = 1.08f, gGain = 1.03f, bGain = 0.91f, rWarmBias = 10f, gWarmBias = 5f, bWarmBias = -4f)
    ),
    LEICA_ANALOG_WARM(
        "Leica Phim Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFDFA977), Color(0xFF4B433E)),
        ColorGradeSpec(saturation = 0.97f, contrast = 1.07f, shadowFade = 8f, rGain = 1.08f, gGain = 1.02f, bGain = 0.88f, rWarmBias = 9f, gWarmBias = 4f, bWarmBias = -6f)
    ),
    LEICA_FADED_WARM(
        "Leica Phai Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE5C4A1), Color(0xFF6E6257)),
        ColorGradeSpec(saturation = 0.85f, contrast = 0.96f, exposureBias = 4f, shadowFade = 14f, rGain = 1.07f, gGain = 1.02f, bGain = 0.89f, rWarmBias = 9f, gWarmBias = 4f, bWarmBias = -4f)
    ),
    LEICA_SILK_CREAM(
        "Leica Lụa Kem", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF7E4CE), Color(0xFF8C7A6B)),
        ColorGradeSpec(saturation = 0.93f, contrast = 1.00f, exposureBias = 7f, shadowFade = 8f, rGain = 1.06f, gGain = 1.03f, bGain = 0.92f, rWarmBias = 8f, gWarmBias = 5f, bWarmBias = -3f)
    ),
    LEICA_IVORY_LUX(
        "Leica Ngà Voi", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFF0DEC2), Color(0xFF7D7260)),
        ColorGradeSpec(saturation = 0.90f, contrast = 1.04f, exposureBias = 5f, shadowFade = 6f, rGain = 1.06f, gGain = 1.04f, bGain = 0.90f, rWarmBias = 8f, gWarmBias = 6f, bWarmBias = -5f)
    ),
    LEICA_PARCHMENT(
        "Leica Giấy Cổ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFDEC39B), Color(0xFF6A5842)),
        ColorGradeSpec(saturation = 0.82f, contrast = 1.03f, shadowFade = 10f, rGain = 1.08f, gGain = 1.03f, bGain = 0.85f, rWarmBias = 10f, gWarmBias = 5f, bWarmBias = -9f)
    ),
    LEICA_WARM_SEPIA_M(
        "Leica Nâu Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD6A979), Color(0xFF4D3420)),
        ColorGradeSpec(saturation = 0.35f, contrast = 1.12f, rGain = 1.12f, gGain = 1.01f, bGain = 0.80f, rWarmBias = 10f, gWarmBias = 3f, bWarmBias = -11f)
    ),
    LEICA_PLATINUM_WARM(
        "Leica Bạch Kim", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE3D5C5), Color(0xFF484038)),
        ColorGradeSpec(contrast = 1.10f, shadowFade = 4f, rGain = 1.06f, gGain = 1.02f, bGain = 0.92f, rWarmBias = 6f, gWarmBias = 3f, bWarmBias = -5f, isMonochrome = true)
    ),
    LEICA_SELENIUM_GOLD(
        "Leica Đơn Sắc Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD8C2A8), Color(0xFF2B221B)),
        ColorGradeSpec(contrast = 1.22f, rGain = 1.09f, gGain = 1.01f, bGain = 0.88f, rWarmBias = 8f, gWarmBias = 2f, bWarmBias = -8f, isMonochrome = true)
    ),

    // =========================================================================
    // Group D (61-80): Leica Olive Warm, Brass Cinema & Classic M-Series
    // =========================================================================
    LEICA_OLIVE_GOLD(
        "Leica Ô Liu Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFB5AC68), Color(0xFF3E3B22)),
        ColorGradeSpec(saturation = 0.94f, contrast = 1.10f, rGain = 1.06f, gGain = 1.05f, bGain = 0.84f, rWarmBias = 7f, gWarmBias = 7f, bWarmBias = -10f)
    ),
    LEICA_MOSS_WARM(
        "Leica Rêu Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFF9EA16B), Color(0xFF2F3524)),
        ColorGradeSpec(saturation = 0.90f, contrast = 1.09f, shadowFade = 5f, rGain = 1.04f, gGain = 1.06f, bGain = 0.86f, rWarmBias = 5f, gWarmBias = 6f, bWarmBias = -8f)
    ),
    LEICA_DESERT_SAGE(
        "Leica Sa Mạc", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD5BE90), Color(0xFF5A5643)),
        ColorGradeSpec(saturation = 0.88f, contrast = 1.06f, exposureBias = 3f, shadowFade = 6f, rGain = 1.07f, gGain = 1.04f, bGain = 0.87f, rWarmBias = 8f, gWarmBias = 5f, bWarmBias = -8f)
    ),
    LEICA_PINE_AMBER(
        "Leica Thông Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFF8D9C5B), Color(0xFF242E1E)),
        ColorGradeSpec(saturation = 0.96f, contrast = 1.13f, rGain = 1.05f, gGain = 1.05f, bGain = 0.85f, rWarmBias = 6f, gWarmBias = 5f, bWarmBias = -9f)
    ),
    LEICA_DUSK_COPPER(
        "Leica Chạng Vạng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD1865E), Color(0xFF2B2D3E)),
        ColorGradeSpec(saturation = 1.05f, contrast = 1.12f, rGain = 1.10f, gGain = 0.99f, bGain = 0.92f, rWarmBias = 10f, gWarmBias = 1f, bWarmBias = -4f)
    ),
    LEICA_TWILIGHT_GOLD(
        "Leica Hoàng Kim", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFEAB543), Color(0xFF2C3A47)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.14f, rGain = 1.11f, gGain = 1.03f, bGain = 0.88f, rWarmBias = 11f, gWarmBias = 4f, bWarmBias = -6f)
    ),
    LEICA_CINE_AMBER(
        "Leica Cine Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFDF924D), Color(0xFF1E2D36)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.16f, rGain = 1.11f, gGain = 1.01f, bGain = 0.89f, rWarmBias = 10f, gWarmBias = 2f, bWarmBias = -6f, rgCross = 0.03f)
    ),
    LEICA_NOIR_WARM(
        "Leica Noir Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC29B70), Color(0xFF141210)),
        ColorGradeSpec(saturation = 0.68f, contrast = 1.20f, exposureBias = -4f, rGain = 1.09f, gGain = 1.01f, bGain = 0.85f, rWarmBias = 8f, gWarmBias = 3f, bWarmBias = -8f)
    ),
    LEICA_CHROME_LUX(
        "Leica Chrome M", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE09F67), Color(0xFF2D3138)),
        ColorGradeSpec(saturation = 1.07f, contrast = 1.14f, rGain = 1.09f, gGain = 1.02f, bGain = 0.90f, rWarmBias = 8f, gWarmBias = 3f, bWarmBias = -6f)
    ),
    LEICA_URBAN_BRASS(
        "Leica Phố Cổ", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC99864), Color(0xFF33302B)),
        ColorGradeSpec(saturation = 0.93f, contrast = 1.13f, shadowFade = 4f, rGain = 1.08f, gGain = 1.01f, bGain = 0.87f, rWarmBias = 8f, gWarmBias = 3f, bWarmBias = -7f)
    ),
    LEICA_CLASSIC_M9(
        "Leica CCD M9", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFD98B4E), Color(0xFF2B3E52)),
        ColorGradeSpec(saturation = 1.14f, contrast = 1.15f, rGain = 1.10f, gGain = 1.02f, bGain = 0.91f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -5f)
    ),
    LEICA_M10_PUNCH(
        "Leica M10 Sắc", FilterCategory.LEICA, true, false,
        listOf(Color(0xFFD63031), Color(0xFF2D3436)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.16f, rGain = 1.07f, gGain = 1.02f, bGain = 1.03f, rWarmBias = 3f, gWarmBias = 0f, bWarmBias = -2f)
    ),
    LEICA_HIGH_ART_W(
        "Leica Nghệ Thuật", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE6AC7E), Color(0xFF3C2E2B)),
        ColorGradeSpec(saturation = 1.01f, contrast = 1.15f, shadowFade = 5f, rGain = 1.10f, gGain = 1.01f, bGain = 0.88f, rWarmBias = 10f, gWarmBias = 3f, bWarmBias = -7f)
    ),
    LEICA_STREET_GRIT(
        "Leica Đường Phố", FilterCategory.LEICA, true, false,
        listOf(Color(0xFF95A5A6), Color(0xFF1E2228)),
        ColorGradeSpec(saturation = 0.78f, contrast = 1.19f, shadowFade = 4f, rGain = 1.03f, gGain = 1.01f, bGain = 0.98f, rWarmBias = 2f, gWarmBias = 0f, bWarmBias = -2f)
    ),
    LEICA_SHADOW_WARM(
        "Leica Bóng Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFC28E62), Color(0xFF1D1714)),
        ColorGradeSpec(saturation = 0.95f, contrast = 1.16f, exposureBias = -3f, rGain = 1.09f, gGain = 1.01f, bGain = 0.86f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -8f)
    ),
    LEICA_FOREST_LUX(
        "Leica Rừng Sâu", FilterCategory.LEICA, true, false,
        listOf(Color(0xFF4A7C59), Color(0xFF1B2B22)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.13f, rGain = 0.96f, gGain = 1.07f, bGain = 0.93f, rWarmBias = -2f, gWarmBias = 4f, bWarmBias = -4f)
    ),
    LEICA_TEAL_AMBER(
        "Leica Xanh Cam", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFE58E3C), Color(0xFF165B6D)),
        ColorGradeSpec(saturation = 1.11f, contrast = 1.14f, rGain = 1.09f, gGain = 1.01f, bGain = 0.95f, rWarmBias = 8f, gWarmBias = 1f, bWarmBias = -2f, gbCross = 0.04f)
    ),
    LEICA_SILVER_WARM(
        "Leica Bạc Ấm", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFDCD3C9), Color(0xFF45403B)),
        ColorGradeSpec(contrast = 1.13f, rGain = 1.05f, gGain = 1.01f, bGain = 0.93f, rWarmBias = 5f, gWarmBias = 2f, bWarmBias = -4f, isMonochrome = true)
    ),
    LEICA_BW_WARM_TONE(
        "Leica BW Vàng", FilterCategory.LEICA, true, true,
        listOf(Color(0xFFEADBC8), Color(0xFF2D251D)),
        ColorGradeSpec(contrast = 1.18f, shadowFade = 5f, rGain = 1.08f, gGain = 1.02f, bGain = 0.86f, rWarmBias = 9f, gWarmBias = 4f, bWarmBias = -8f, isMonochrome = true)
    ),
    LEICA_MONOCHROM_S(
        "Leica Mono S", FilterCategory.LEICA, true, false,
        listOf(Color(0xFFE5E7EB), Color(0xFF111317)),
        ColorGradeSpec(contrast = 1.24f, rGain = 1.02f, gGain = 1.02f, bGain = 1.02f, rWarmBias = 0f, gWarmBias = 0f, bWarmBias = 0f, isMonochrome = true)
    ),

    // =========================================================================
    // 20 NEW STANDARD / REGULAR CAMERA FILTERS
    // =========================================================================
    STD_FRESH_DAY(
        "Ngày Tươi", FilterCategory.FILM, false, false,
        listOf(Color(0xFF81ECEC), Color(0xFF00B894)),
        ColorGradeSpec(saturation = 1.14f, contrast = 1.04f, exposureBias = 4f, rGain = 1.02f, gGain = 1.04f, bGain = 1.03f, rWarmBias = 2f, gWarmBias = 3f, bWarmBias = 2f)
    ),
    STD_CLEAR_SKY(
        "Trời Trong", FilterCategory.FILM, false, false,
        listOf(Color(0xFF74B9FF), Color(0xFF0984E3)),
        ColorGradeSpec(saturation = 1.10f, contrast = 1.06f, exposureBias = 3f, rGain = 0.97f, gGain = 1.02f, bGain = 1.08f, rWarmBias = -2f, gWarmBias = 2f, bWarmBias = 6f)
    ),
    STD_SOFT_PASTEL(
        "Pastel Nhẹ", FilterCategory.FILM, false, false,
        listOf(Color(0xFFF8C8DC), Color(0xFFA8D8EA)),
        ColorGradeSpec(saturation = 0.92f, contrast = 0.94f, exposureBias = 8f, shadowFade = 12f, rGain = 1.04f, gGain = 1.02f, bGain = 1.05f, rWarmBias = 4f, gWarmBias = 2f, bWarmBias = 5f)
    ),
    STD_SAKURA(
        "Anh Đào", FilterCategory.FILM, false, false,
        listOf(Color(0xFFFFB7C5), Color(0xFF9B59B6)),
        ColorGradeSpec(saturation = 1.08f, contrast = 1.02f, exposureBias = 6f, rGain = 1.09f, gGain = 0.98f, bGain = 1.04f, rWarmBias = 9f, gWarmBias = -1f, bWarmBias = 5f)
    ),
    STD_CREAMY_MILK(
        "Kem Sữa", FilterCategory.FILM, false, true,
        listOf(Color(0xFFFFF1E6), Color(0xFFCDB4DB)),
        ColorGradeSpec(saturation = 0.90f, contrast = 0.97f, exposureBias = 7f, shadowFade = 10f, rGain = 1.05f, gGain = 1.02f, bGain = 0.96f, rWarmBias = 6f, gWarmBias = 3f, bWarmBias = -1f)
    ),
    STD_MOCHA_CAFE(
        "Mocha", FilterCategory.FILM, false, true,
        listOf(Color(0xFFB08968), Color(0xFF4A3525)),
        ColorGradeSpec(saturation = 0.88f, contrast = 1.08f, shadowFade = 7f, rGain = 1.07f, gGain = 1.00f, bGain = 0.89f, rWarmBias = 8f, gWarmBias = 2f, bWarmBias = -6f)
    ),
    STD_MORNING_DEW(
        "Sương Sớm", FilterCategory.FILM, false, false,
        listOf(Color(0xFFC7ECEE), Color(0xFF686DE0)),
        ColorGradeSpec(saturation = 0.95f, contrast = 0.98f, exposureBias = 5f, shadowFade = 8f, rGain = 0.96f, gGain = 1.03f, bGain = 1.07f, rWarmBias = -2f, gWarmBias = 3f, bWarmBias = 6f)
    ),
    STD_AUTUMN_BREEZE(
        "Thu Dịu", FilterCategory.FILM, false, true,
        listOf(Color(0xFFF3A683), Color(0xFF786FA6)),
        ColorGradeSpec(saturation = 1.04f, contrast = 1.05f, rGain = 1.08f, gGain = 1.01f, bGain = 0.90f, rWarmBias = 9f, gWarmBias = 3f, bWarmBias = -5f)
    ),
    STD_SUMMER_POP(
        "Hè Rực", FilterCategory.FILM, false, false,
        listOf(Color(0xFFFFBE76), Color(0xFFFF7979)),
        ColorGradeSpec(saturation = 1.24f, contrast = 1.08f, exposureBias = 3f, rGain = 1.06f, gGain = 1.03f, bGain = 0.98f, rWarmBias = 5f, gWarmBias = 3f, bWarmBias = 0f)
    ),
    STD_OCEAN_MIST(
        "Biển Xanh", FilterCategory.FILM, false, false,
        listOf(Color(0xFF48DBFB), Color(0xFF0ABDE3)),
        ColorGradeSpec(saturation = 1.09f, contrast = 1.05f, rGain = 0.93f, gGain = 1.04f, bGain = 1.10f, rWarmBias = -5f, gWarmBias = 3f, bWarmBias = 8f)
    ),
    STD_NORDIC_ICE(
        "Bắc Âu", FilterCategory.FILM, false, false,
        listOf(Color(0xFFDFF9FB), Color(0xFF535C68)),
        ColorGradeSpec(saturation = 0.80f, contrast = 1.09f, rGain = 0.95f, gGain = 1.01f, bGain = 1.09f, rWarmBias = -4f, gWarmBias = 1f, bWarmBias = 7f)
    ),
    STD_CITY_NEON(
        "Phố Đêm", FilterCategory.FILM, false, false,
        listOf(Color(0xFFFD79A8), Color(0xFF00CEC9)),
        ColorGradeSpec(saturation = 1.22f, contrast = 1.15f, rGain = 1.05f, gGain = 0.97f, bGain = 1.12f, rWarmBias = 4f, gWarmBias = -3f, bWarmBias = 9f)
    ),
    STD_RETRO_90S(
        "Retro 90s", FilterCategory.FILM, false, false,
        listOf(Color(0xFFE1B12C), Color(0xFF40739E)),
        ColorGradeSpec(saturation = 0.94f, contrast = 1.03f, shadowFade = 11f, rGain = 1.04f, gGain = 1.03f, bGain = 0.93f, rWarmBias = 5f, gWarmBias = 4f, bWarmBias = -2f)
    ),
    STD_POLAROID(
        "Polaroid", FilterCategory.FILM, false, false,
        listOf(Color(0xFFF7F1E3), Color(0xFF474787)),
        ColorGradeSpec(saturation = 0.91f, contrast = 1.04f, shadowFade = 12f, rGain = 1.03f, gGain = 1.04f, bGain = 0.95f, rWarmBias = 5f, gWarmBias = 5f, bWarmBias = 1f)
    ),
    STD_KODAK_GOLD(
        "Kodak Gold", FilterCategory.FILM, false, true,
        listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
        ColorGradeSpec(saturation = 1.12f, contrast = 1.07f, rGain = 1.10f, gGain = 1.04f, bGain = 0.87f, rWarmBias = 10f, gWarmBias = 5f, bWarmBias = -8f)
    ),
    STD_AGFA_VISTA(
        "Agfa Vista", FilterCategory.FILM, false, false,
        listOf(Color(0xFFFF6B6B), Color(0xFF4ECDC4)),
        ColorGradeSpec(saturation = 1.16f, contrast = 1.09f, rGain = 1.07f, gGain = 1.01f, bGain = 1.01f, rWarmBias = 5f, gWarmBias = 0f, bWarmBias = 1f)
    ),
    STD_FOREST_GREEN(
        "Rừng Xanh", FilterCategory.FILM, false, false,
        listOf(Color(0xFF55EFC4), Color(0xFF1E824C)),
        ColorGradeSpec(saturation = 1.06f, contrast = 1.07f, rGain = 0.95f, gGain = 1.08f, bGain = 0.94f, rWarmBias = -3f, gWarmBias = 6f, bWarmBias = -3f)
    ),
    STD_ROSE_TINT(
        "Hồng Phấn", FilterCategory.FILM, false, false,
        listOf(Color(0xFFFF9FF3), Color(0xFFF368E0)),
        ColorGradeSpec(saturation = 1.05f, contrast = 1.02f, exposureBias = 4f, rGain = 1.08f, gGain = 0.97f, bGain = 1.05f, rWarmBias = 8f, gWarmBias = -2f, bWarmBias = 5f)
    ),
    STD_NIGHT_MOOD(
        "Đêm Trầm", FilterCategory.FILM, false, false,
        listOf(Color(0xFF576574), Color(0xFF222F3E)),
        ColorGradeSpec(saturation = 0.84f, contrast = 1.14f, exposureBias = -5f, rGain = 0.94f, gGain = 0.99f, bGain = 1.09f, rWarmBias = -4f, gWarmBias = 0f, bWarmBias = 7f)
    ),
    STD_MATTE_CHARCOAL(
        "Than Mờ", FilterCategory.FILM, false, false,
        listOf(Color(0xFF8395A7), Color(0xFF22252A)),
        ColorGradeSpec(contrast = 1.12f, shadowFade = 12f, rGain = 1.00f, gGain = 1.00f, bGain = 1.02f, rWarmBias = 0f, gWarmBias = 0f, bWarmBias = 2f, isMonochrome = true)
    );

    val sampleSubject: FilterSampleSubject
        get() = when (this) {
            PORTRA,
            LEICA_PEACH_SKIN, LEICA_ROSE_GOLD, LEICA_APRICOT_50, LEICA_CORAL_DUSK,
            LEICA_BLUSH_LUX, LEICA_WARM_SKIN, LEICA_VELVET_PEACH, LEICA_DAWN_GLINT,
            LEICA_CHAMPAGNE, LEICA_SILK_CREAM, LEICA_PORTRA_WARM,
            STD_SOFT_PASTEL, STD_SAKURA, STD_CREAMY_MILK, STD_ROSE_TINT,
            WARM, STD_AUTUMN_BREEZE, LEICA_WHEAT_FIELD -> FilterSampleSubject.PORTRAIT_SKIN

            LEICA_AUTHENTIC, LEICA_BW_NAT, LEICA_BW_HC, LEICA_SEPIA,
            LEICA_ESPRESSO, LEICA_ROASTED_M, LEICA_WARM_LATTE, LEICA_TOASTED_OAK,
            LEICA_CANDLE_28, LEICA_TUNGSTEN_WARM, LEICA_CAFE_VIENNA, LEICA_COGNAC_M,
            LEICA_CHESTNUT, LEICA_TEAK_WOOD, LEICA_ANALOG_WARM, LEICA_FADED_WARM,
            LEICA_IVORY_LUX, LEICA_PARCHMENT, LEICA_WARM_SEPIA_M, LEICA_PLATINUM_WARM,
            LEICA_SELENIUM_GOLD, LEICA_NOIR_WARM, LEICA_SILVER_WARM, LEICA_BW_WARM_TONE,
            LEICA_MONOCHROM_S, FILM, VINTAGE, BW,
            STD_MOCHA_CAFE, STD_RETRO_90S, STD_POLAROID, STD_MATTE_CHARCOAL,
            LEICA_CLAY_WARM, LEICA_CINNAMON_M, LEICA_BURNT_UMBER, LEICA_NOCTILUX_WARM -> FilterSampleSubject.CAFE_ANALOG

            LEICA_BLUE, FUJI_PRO, COOL, CINEMATIC,
            LEICA_OLIVE_GOLD, LEICA_MOSS_WARM, LEICA_PINE_AMBER, LEICA_FOREST_LUX,
            LEICA_TEAL_AMBER, LEICA_DUSK_COPPER, LEICA_TWILIGHT_GOLD, LEICA_CINE_AMBER,
            LEICA_STREET_GRIT,
            STD_FRESH_DAY, STD_CLEAR_SKY, STD_MORNING_DEW, STD_OCEAN_MIST,
            STD_NORDIC_ICE, STD_CITY_NEON, STD_FOREST_GREEN, STD_NIGHT_MOOD -> FilterSampleSubject.SCENIC_BLUE

            NATURAL, LEICA_VIBRANT,
            LEICA_AMBER_GOLD, LEICA_HONEY_SUN, LEICA_GOLDEN_HOUR, LEICA_SUNSET_GLOW,
            LEICA_WARM_SUMMILUX, LEICA_APO_AMBER, LEICA_M11_WARM, LEICA_Q3_GOLDEN,
            LEICA_SL3_SOLAR, LEICA_BRASS_PATINA, LEICA_SAFFRON_M, LEICA_CARAMEL_35,
            LEICA_MARIGOLD, LEICA_OCHRE_STREET, LEICA_SOLSTICE, LEICA_SUNLIT_STONE,
            LEICA_COPPER_M, LEICA_TERRACOTTA, LEICA_BRONZE_LUX, LEICA_RUST_VINTAGE,
            LEICA_SIENNA_PRO, LEICA_SANDSTONE, LEICA_AUTUMN_LEAF, LEICA_MAPLE_WARM,
            LEICA_TANGERINE_LITE, LEICA_RED_EARTH, LEICA_KODACHROME_W, LEICA_DESERT_SAGE,
            LEICA_CHROME_LUX, LEICA_URBAN_BRASS, LEICA_CLASSIC_M9, LEICA_M10_PUNCH,
            LEICA_HIGH_ART_W, LEICA_SHADOW_WARM,
            VIVID, STD_SUMMER_POP, STD_KODAK_GOLD, STD_AGFA_VISTA -> FilterSampleSubject.STREET_GOLDEN
        }
}
