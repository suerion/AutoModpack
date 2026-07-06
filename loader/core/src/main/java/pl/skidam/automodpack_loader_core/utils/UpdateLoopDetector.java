package pl.skidam.automodpack_loader_core.utils;

import pl.skidam.automodpack_core.config.ConfigTools;

import java.nio.file.Files;
import java.nio.file.Path;

import static pl.skidam.automodpack_core.Constants.*;

/**
 * Breaks "restart your game" loops. Every restart the updater requests is recorded together
 * with a fingerprint of the state that caused it. If the same state keeps requesting restarts
 * back-to-back, the modpack is clearly not converging (corrupted file that never passes the
 * hash check, something external re-modifying files, ...) and endlessly rebooting the game
 * won't fix it - so after a few attempts we stop asking and surface a diagnostic instead.
 */
public class UpdateLoopDetector {
    // Third restart request for the same state within the window means we're looping.
    private static final int MAX_CONSECUTIVE_RESTARTS = 3;
    // Restarts further apart than this are treated as unrelated (e.g. the user plays daily).
    private static final long WINDOW_SECONDS = 30 * 60;

    private static final Path STATE_FILE = privateDir.resolve("automodpack-restart-state.json");

    public static class State {
        public String fingerprint = "";
        public int consecutiveRestarts = 0;
        public long lastRestartTimestamp = 0;
    }

    private UpdateLoopDetector() {
    }

    /**
     * Returns true when a restart for this state would just continue the loop.
     * Call before triggering the restart.
     */
    public static boolean isLooping(String fingerprint) {
        State state = load();
        return matchesWindow(state, fingerprint) && state.consecutiveRestarts >= MAX_CONSECUTIVE_RESTARTS;
    }

    /** Records that a restart is about to be triggered for this state. */
    public static void recordRestart(String fingerprint) {
        State state = load();
        if (matchesWindow(state, fingerprint)) {
            state.consecutiveRestarts++;
        } else {
            state.fingerprint = fingerprint;
            state.consecutiveRestarts = 1;
        }
        state.lastRestartTimestamp = now();
        ConfigTools.save(STATE_FILE, state);
    }

    /** Call when the modpack loaded cleanly - convergence resets the loop counter. */
    public static void clear() {
        try {
            Files.deleteIfExists(STATE_FILE);
        } catch (Exception e) {
            LOGGER.warn("Failed to clear restart-loop state", e);
        }
    }

    private static boolean matchesWindow(State state, String fingerprint) {
        return state.fingerprint.equals(fingerprint) && now() - state.lastRestartTimestamp <= WINDOW_SECONDS;
    }

    private static State load() {
        State state = ConfigTools.softLoad(STATE_FILE, State.class);
        return state == null ? new State() : state;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }
}
