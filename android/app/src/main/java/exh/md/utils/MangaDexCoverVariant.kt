package exh.md.utils

enum class MangaDexCoverQuality(val value: Int) {
    AUTOMATIC(0),
    HD_EVERYWHERE(1),
    DATA_SAVER(2);

    companion object {
        fun fromValue(value: Int): MangaDexCoverQuality =
            entries.firstOrNull { it.value == value } ?: AUTOMATIC
    }
}

enum class MangaDexCoverVariant(val suffix: String) {
    ORIGINAL(""),
    MEDIUM(".512.jpg"),
    LOW(".256.jpg");
}

object MdCoverVariantUtil {
    private const val SUFFIX_512 = ".512.jpg"
    private const val SUFFIX_256 = ".256.jpg"

    fun normalizeCoverUrl(url: String): String {
        return url.removeSuffix(SUFFIX_512).removeSuffix(SUFFIX_256)
    }

    fun cleanFileName(fileName: String): String {
        return fileName.removeSuffix(SUFFIX_512).removeSuffix(SUFFIX_256)
    }

    fun selectVariant(
        quality: MangaDexCoverQuality,
        displayWidthPx: Int,
        displayHeightPx: Int = 0,
    ): MangaDexCoverVariant {
        val neededWidth = if (displayWidthPx > 0) displayWidthPx else displayHeightPx * 2 / 3
        return when (quality) {
            MangaDexCoverQuality.HD_EVERYWHERE -> MangaDexCoverVariant.ORIGINAL
            MangaDexCoverQuality.DATA_SAVER -> {
                if (neededWidth <= 512) MangaDexCoverVariant.LOW else MangaDexCoverVariant.MEDIUM
            }
            MangaDexCoverQuality.AUTOMATIC -> {
                when {
                    neededWidth <= 256 -> MangaDexCoverVariant.LOW
                    neededWidth <= 512 -> MangaDexCoverVariant.MEDIUM
                    else -> MangaDexCoverVariant.ORIGINAL
                }
            }
        }
    }

    fun getVariantUrl(baseUrl: String, variant: MangaDexCoverVariant): String {
        if (!isMangaDexCover(baseUrl)) return baseUrl
        val base = normalizeCoverUrl(baseUrl)
        return "$base${variant.suffix}"
    }

    fun isMangaDexCover(url: String): Boolean {
        return url.contains("/covers/") && (url.contains("mangadex.org") || url.startsWith("https://uploads."))
    }
}
