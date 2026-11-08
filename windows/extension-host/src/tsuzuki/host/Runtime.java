package tsuzuki.host;

public final class Runtime {
    private Runtime() {
    }

    public static void boot(String root) {
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", root);
        String[][] settings = {
            {"kcefEnabled", "false"},
            {"systemTrayEnabled", "false"},
            {"webUIEnabled", "false"},
            {"initialOpenInBrowserEnabled", "false"},
            {"globalUpdateInterval", "0"},
            {"backupInterval", "0"},
            {"webUIUpdateCheckInterval", "0"},
            {"autoDownloadNewChapters", "false"},
            {"syncYomiEnabled", "false"},
            {"maxSourcesInParallel", "2"},
            {"ip", "127.0.0.1"},
            {"port", "0"},
        };
        for (String[] setting : settings) {
            System.setProperty("suwayomi.tachidesk.config.server." + setting[0], setting[1]);
        }
        suwayomi.tachidesk.server.ServerSetupKt.applicationSetup();
    }
}
