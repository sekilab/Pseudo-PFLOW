package util;

/**
 * Resolves ${PFLOW_HOME} tokens in configuration values.
 * Enables cross-platform config files shared via Dropbox between Windows and macOS.
 */
public class PathResolver {

    private static final String ENV_VAR = "PFLOW_HOME";
    private static final String PLACEHOLDER = "${PFLOW_HOME}";
    private static final String WINDOWS_DEFAULT = "H:/Dropbox/PFLOW";

    /**
     * Replace ${PFLOW_HOME} tokens in a property value with the resolved path.
     * Returns the original value unchanged if no token is present.
     */
    public static String resolve(String value) {
        if (value == null || !value.contains(PLACEHOLDER)) {
            return value;
        }
        return value.replace(PLACEHOLDER, getPflowHome());
    }

    /**
     * Get the resolved PFLOW_HOME base path.
     * Priority: (1) PFLOW_HOME env var, (2) OS-based auto-detect.
     */
    public static String getPflowHome() {
        String home = System.getenv(ENV_VAR);
        if (home != null && !home.isEmpty()) {
            return stripTrailingSlash(home);
        }
        return detectDefault();
    }

    private static String detectDefault() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("nix") || os.contains("nux")) {
            return System.getProperty("user.home") + "/Dropbox/PFLOW";
        }
        return WINDOWS_DEFAULT;
    }

    private static String stripTrailingSlash(String path) {
        if (path.endsWith("/") || path.endsWith("\\")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
