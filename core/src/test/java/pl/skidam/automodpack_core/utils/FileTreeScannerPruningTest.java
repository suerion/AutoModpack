package pl.skidam.automodpack_core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileTreeScannerPruningTest {

    @TempDir
    Path testFilesDir;

    private FileTreeScanner scannerFor(Set<String> rules) {
        return new FileTreeScanner(rules, Set.of(testFilesDir));
    }

    @Test
    void subtreeBlacklistPrunesMatchingDirectories() {
        var scanner = scannerFor(Set.of("/**", "!/bluemap/**", "!/config/big/**", "!/cache*/**"));

        assertTrue(scanner.isSubtreeBlacklisted(Path.of("bluemap")));
        assertTrue(scanner.isSubtreeBlacklisted(Path.of("config/big")));
        assertTrue(scanner.isSubtreeBlacklisted(Path.of("cache1")));
        assertTrue(scanner.isSubtreeBlacklisted(Path.of("cache-tmp")));

        // similar names must not be pruned
        assertFalse(scanner.isSubtreeBlacklisted(Path.of("bluemap-extra")));
        // parent of a nested exclusion must keep being walked
        assertFalse(scanner.isSubtreeBlacklisted(Path.of("config")));
        assertFalse(scanner.isSubtreeBlacklisted(Path.of("mods")));
    }

    @Test
    void blacklistWithSuffixAfterDoubleWildcardDoesNotPrune() {
        // "!/maps/**/*.png" excludes only pngs - other files below /maps must still be found
        var scanner = scannerFor(Set.of("/**", "!/maps/**/*.png"));

        assertFalse(scanner.isSubtreeBlacklisted(Path.of("maps")));
        assertFalse(scanner.isSubtreeBlacklisted(Path.of("maps/region")));
    }

    @Test
    void wildcardPrefixedSubtreeBlacklistPrunesNestedDirectories() {
        // matches any "cache" directory nested under journeymap
        var scanner = scannerFor(Set.of("/**", "!/journeymap/**/cache/**"));

        assertTrue(scanner.isSubtreeBlacklisted(Path.of("journeymap/data/cache")));
        assertFalse(scanner.isSubtreeBlacklisted(Path.of("journeymap/data")));
    }

    @Test
    void scanResultsExcludeBlacklistedSubtreeButKeepSiblings() throws IOException {
        Files.createDirectories(testFilesDir.resolve("bluemap/web/tiles"));
        Files.writeString(testFilesDir.resolve("bluemap/web/tiles/tile.png"), "a");
        Files.writeString(testFilesDir.resolve("bluemap/settings.json"), "a");
        Files.createDirectories(testFilesDir.resolve("config"));
        Files.writeString(testFilesDir.resolve("config/mod.toml"), "a");
        Files.writeString(testFilesDir.resolve("root.txt"), "a");

        var scanner = scannerFor(Set.of("/**", "!/bluemap/**"));
        scanner.scan();
        var matches = scanner.getMatchedPaths();

        assertTrue(matches.containsKey("/config/mod.toml"));
        assertTrue(matches.containsKey("/root.txt"));
        assertFalse(matches.containsKey("/bluemap/settings.json"));
        assertFalse(matches.containsKey("/bluemap/web/tiles/tile.png"));
        assertEquals(2, matches.size());
    }

    @Test
    void scanResultsIdenticalWhetherPrunedOrNot() throws IOException {
        // The same exclusion expressed in a non-prunable way must yield the same result set,
        // proving pruning is purely a traversal optimization.
        Files.createDirectories(testFilesDir.resolve("data/cache/sub"));
        Files.writeString(testFilesDir.resolve("data/cache/sub/blob.bin"), "a");
        Files.writeString(testFilesDir.resolve("data/keep.txt"), "a");

        var pruned = scannerFor(Set.of("/data/**", "!/data/cache/**"));
        pruned.scan();
        var nonPruned = scannerFor(Set.of("/data/**", "!/data/cache/**/*", "!/data/cache/*"));
        nonPruned.scan();

        assertEquals(nonPruned.getMatchedPaths().keySet(), pruned.getMatchedPaths().keySet());
        assertTrue(pruned.getMatchedPaths().containsKey("/data/keep.txt"));
        assertFalse(pruned.getMatchedPaths().containsKey("/data/cache/sub/blob.bin"));
    }
}
