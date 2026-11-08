package tsuzuki.host;

final class Errors {
    private Errors() {
    }

    static String kind(Throwable error) {
        String name = error.getClass().getName();
        String message = String.valueOf(error.getMessage()).toLowerCase();
        if (message.contains("cloudflare") || message.contains("webview") || message.contains("captcha")) {
            return "blocked";
        }
        if (error instanceof java.io.IOException || name.startsWith("okhttp3") || name.contains("Http")) {
            return "network";
        }
        if (error instanceof LinkageError || error instanceof ClassCastException || error instanceof UnsupportedOperationException) {
            return "unsupported";
        }
        return "source";
    }
}
