package pl.skidam.automodpack_core.auth;

import pl.skidam.automodpack_core.utils.AddressHelpers;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static pl.skidam.automodpack_core.Constants.LOGGER;

/**
 * Certificate pins embedded in the server address the player typed:
 * {@code play.example.com:25565;<sha256-fingerprint>}. Admins can hand out one
 * copy-pasteable string and the first join needs no fingerprint screen at all.
 *
 * Pins live only for the session - once the presented certificate matches, the
 * fingerprint is persisted into the regular known-hosts store.
 */
public class AddressPins {
    // Hex SHA-256, optionally with ':' or spaces between bytes (as some tools print it)
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final Map<String, String> pins = new ConcurrentHashMap<>();

    private AddressPins() {
    }

    /**
     * Splits an optional {@code ;fingerprint} suffix off a typed server address.
     * Stores the pin (keyed by host) and returns the address without the suffix.
     * Returns the input unchanged when there is no valid pin in it.
     */
    public static String extractAndStore(String rawAddress) {
        if (rawAddress == null)
            return null;

        int idx = rawAddress.lastIndexOf(';');
        if (idx < 0)
            return rawAddress;

        String fingerprint = rawAddress.substring(idx + 1).trim()
                .replace(":", "").replace(" ", "")
                .toLowerCase(Locale.ROOT);
        String cleanAddress = rawAddress.substring(0, idx).trim();

        if (cleanAddress.isEmpty() || !FINGERPRINT.matcher(fingerprint).matches())
            return rawAddress;

        String host = hostOf(cleanAddress);
        if (host == null || host.isBlank())
            return rawAddress;

        // isValidAddress runs on every UI keystroke - only log genuinely new pins
        String previous = pins.put(host, fingerprint);
        if (!fingerprint.equals(previous)) {
            LOGGER.info("Using certificate fingerprint embedded in the server address of {}", host);
        }
        return cleanAddress;
    }

    /** The pin the player supplied for this minecraft server host, if any. */
    public static Optional<String> getPin(String serverHost) {
        if (serverHost == null)
            return Optional.empty();
        return Optional.ofNullable(pins.get(serverHost.toLowerCase(Locale.ROOT)));
    }

    private static String hostOf(String address) {
        var parsed = AddressHelpers.parse(address);
        return parsed == null ? null : parsed.getHostString();
    }
}
