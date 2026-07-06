package pl.skidam.automodpack_core.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static pl.skidam.automodpack_core.Constants.LOGGER;

/**
 * Resolves an admin-published certificate pin from DNS:
 * {@code _automodpack.<host>. TXT "v=amp1;fp=<sha256-hex>"}
 *
 * The record is fetched over DNS-over-HTTPS from two independent resolvers and is only
 * used when BOTH report the answer as DNSSEC-validated (AD flag) and agree on the pin.
 * Trust is therefore anchored out-of-band: in the WebPKI certificates of the resolvers
 * and in the DNSSEC chain of the server's zone - never in anything the modpack host
 * itself sends. Anything less than full agreement yields empty, falling back to the
 * regular manual fingerprint verification.
 */
public class DnsPinResolver {

    public static final String RECORD_PREFIX = "_automodpack.";
    public static final String RECORD_VERSION = "v=amp1";

    private static final List<String> DOH_RESOLVERS = List.of(
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/resolve"
    );
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    // Per-host memoization so parallel download connections don't re-query
    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    /**
     * @return the DNSSEC-validated pin (lowercase sha256 hex) published for the host,
     * or empty when there is no record, validation fails or the resolvers disagree.
     */
    public static Optional<String> resolvePin(String host) {
        if (host == null || host.isBlank() || isIpLiteral(host)) {
            return Optional.empty();
        }

        return CACHE.computeIfAbsent(host.toLowerCase(Locale.ROOT), DnsPinResolver::queryAllResolvers);
    }

    private static Optional<String> queryAllResolvers(String host) {
        String name = RECORD_PREFIX + host;

        String agreedPin = null;
        for (String resolver : DOH_RESOLVERS) {
            Optional<String> pin = queryValidatedPin(resolver, name);
            if (pin.isEmpty()) {
                return Optional.empty();
            }

            if (agreedPin == null) {
                agreedPin = pin.get();
            } else if (!agreedPin.equals(pin.get())) {
                LOGGER.warn("DNS resolvers disagree on the certificate pin for {} - ignoring DNS pin", host);
                return Optional.empty();
            }
        }

        LOGGER.info("Found DNSSEC-validated certificate pin for {} in DNS", host);
        return Optional.of(agreedPin);
    }

    private static Optional<String> queryValidatedPin(String resolver, String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolver + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&type=TXT"))
                    .header("Accept", "application/dns-json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.get("Status").getAsInt() != 0) {
                return Optional.empty();
            }

            // AD = the resolver performed DNSSEC validation on the whole chain
            if (!json.has("AD") || !json.get("AD").getAsBoolean()) {
                return Optional.empty();
            }

            if (!json.has("Answer")) {
                return Optional.empty();
            }

            for (JsonElement element : json.getAsJsonArray("Answer")) {
                JsonObject answer = element.getAsJsonObject();
                if (answer.get("type").getAsInt() != 16) { // TXT
                    continue;
                }

                // Long TXT records come as multiple quoted strings - drop all quotes
                Optional<String> pin = parsePin(answer.get("data").getAsString().replace("\"", "").trim());
                if (pin.isPresent()) {
                    return pin;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("DNS pin lookup for {} via {} failed", name, resolver, e);
        }

        return Optional.empty();
    }

    static Optional<String> parsePin(String txt) {
        if (txt == null || !txt.startsWith(RECORD_VERSION)) {
            return Optional.empty();
        }

        for (String part : txt.split(";")) {
            part = part.trim();
            if (!part.startsWith("fp=")) {
                continue;
            }

            String fingerprint = part.substring(3).replace(":", "").toLowerCase(Locale.ROOT);
            if (fingerprint.matches("[0-9a-f]{64}")) {
                return Optional.of(fingerprint);
            }
        }

        return Optional.empty();
    }

    static boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
