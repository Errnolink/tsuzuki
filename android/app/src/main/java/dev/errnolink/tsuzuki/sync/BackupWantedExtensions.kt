package dev.errnolink.tsuzuki.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupWantedExtensions(
    @ProtoNumber(1) var wanted: List<BackupWantedExtension> = emptyList(),
    @ProtoNumber(2) var tombstones: List<BackupExtensionTombstone> = emptyList(),
    @ProtoNumber(3) var syncUninstalls: Boolean = true,
)

@Serializable
data class BackupWantedExtension(
    @ProtoNumber(1) var pkgName: String = "",
    @ProtoNumber(2) var repoIndexUrl: String = "",
    @ProtoNumber(3) var signingFingerprint: String = "",
    @ProtoNumber(4) var versionCode: Long = 0,
    @ProtoNumber(5) var updatedAt: Long = 0,
    @ProtoNumber(6) var versionName: String = "",
)

@Serializable
data class BackupExtensionTombstone(
    @ProtoNumber(1) var pkgName: String = "",
    @ProtoNumber(2) var deletedAt: Long = 0,
)
