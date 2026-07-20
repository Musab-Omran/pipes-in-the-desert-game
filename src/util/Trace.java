package util;

/**
 * Central trace logger used by the project.
 * <p>
 * The skeleton and prototype phases rely on human-readable console output
 * with the {@code S:} prefix. The final GUI can disable the logger so that
 * the model layer no longer spams the console during interactive play.
 */
public final class Trace {

    /**
     * Controls whether trace output is written to the console.
     */
    private static boolean enabled = true;

    private Trace() {
        // utility class
    }

    /**
     * Returns whether tracing is currently enabled.
     *
     * @return true when tracing is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables tracing output.
     *
     * @param value new enabled state
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Prints a trace line if tracing is enabled.
     *
     * @param message message to print
     */
    public static void log(String message) {
        if (enabled) {
            System.out.println(message);
        }
    }

    /**
     * Prints a blank trace line if tracing is enabled.
     */
    public static void log() {
        if (enabled) {
            System.out.println();
        }
    }

    /**
     * Prints text without a line break if tracing is enabled.
     *
     * @param message text to print
     */
    public static void print(String message) {
        if (enabled) {
            System.out.print(message);
        }
    }
}
