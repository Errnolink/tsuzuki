package mihon.core.migration.migrations

import mihon.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupLibraryUpdateMigration(),
        SetupSyncDataMigration(),
        // MergedMangaRewriteMigration(),
        // LogoutFromMALMigration(),
        // MoveDOHSettingMigration(),
        // ResetRotationSettingMigration(),
        // ResetReaderSettingsMigration(),
        // DeleteOldMangaDexTracksMigration(),
        // RemoveOldReaderThemeMigration(),
        // RemoveShorterLibraryUpdatesMigration(),
        // MoveLibrarySortingSettingsMigration(),
        // RemoveShortLibraryUpdatesMigration(),
        // MoveLibraryNonCompleteSettingMigration(),
        // DeleteOldEhFavoritesDatabaseMigration(),
        // MoveSecureScreenSettingMigration(),
        // ChangeMiuiExtensionInstallerMigration(),
        // MoveCoverOnlyGridSettingMigration(),
        // MoveCatalogueCoverOnlyGridSettingMigration(),
        // MoveLatestToFeedMigration(),
        // MoveReaderTapSettingMigration(),
        // MoveSortingModeSettingsMigration(),
        // MoveSortingModeSettingMigration(),
        // AlwaysBackupMigration(),
        // ResetFilterAndSortSettingsMigration(),
        // ChangeThemeModeToUppercaseMigration(),
        // MoveReadingButtonSettingMigration(),
        // ChangeTrackingQueueTypeMigration(),
        // LogoutFromMangaDexMigration(),
        // RemoveUpdateCheckerJobsMigration(),
        // RemoveBatteryNotLowRestrictionMigration(),
        // MoveRelativeTimeSettingMigration(),
        // MoveCacheToDiskSettingMigration(),
        // MoveSettingsToPrivateOrAppStateMigration(),
        // MoveExtensionRepoSettingsMigration(),
        // MoveEncryptionSettingsToAppStateMigration(),
        // KMK -->
        MergedMangaDedupeModeMigration(),
        // KMK <--
        TrustExtensionRepositoryMigration(),
        CategoryPreferencesCleanupMigration(),
        RemoveDuplicateReaderPreferenceMigration(),
        // KMK -->
        DisabledRepoMigration(),
        SyncPrefKeyMigration(),
        ChapterUrlHashMigration(),
        // KMK <--
    )
