package dev.errnolink.tsuzuki.network

import exh.md.utils.MdCoverVariantUtil

object MangaDexCoverAgent {
    const val USER_AGENT = "Tsuzuki-Android"

    fun userAgentFor(url: String): String? = USER_AGENT.takeIf { MdCoverVariantUtil.isMangaDexCover(url) }
}
