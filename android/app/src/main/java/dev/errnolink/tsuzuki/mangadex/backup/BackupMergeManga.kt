// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMergeManga(
    @ProtoNumber(1) var url: String = "",
    @ProtoNumber(2) var title: String = "",
    @ProtoNumber(3) var coverUrl: String = "",
    @ProtoNumber(4) var mergeType: Int = -1,
)
