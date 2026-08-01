package io.github.pacifistmc.forgix.core;

import io.github.pacifistmc.forgix.utils.JAR;
import io.github.pacifistmc.forgix.utils.Text;
import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

/**
 * Deduplicates nested zips (jar-in-jar dependencies) across the JARs being merged.
 * Every loader nests the same dependency at its own path with tiny metadata differences
 * (Fabric's tooling injects a fabric.mod.json into them), so plain equality never matches,
 * instead we treat a nested zip that's fully contained in another one as the same dependency.
 */
public class Deduplicator {
    private record NestedZip(File jar, String path, Map<String, String> entryHashes) { }

    private Deduplicator() { }

    /**
     * Deduplicates nested zips across the given JARs, keeping the bigger copy of each and
     * rewriting everything that pointed at a dropped copy. The JARs are modified in place.
     * @param jars The JARs to deduplicate across, in merge order
     */
    public static void deduplicateNestedZips(List<File> jars) {
        // Collect every nested zip with the hashes of its entries
        List<NestedZip> nestedZips = new ArrayList<>();
        for (var jar : jars) {
            try (var jarFile = new JarFile(jar)) {
                for (var entry : Collections.list(jarFile.entries())) {
                    if (entry.isDirectory()) continue;
                    var bytes = JAR.getResourceBytes(jarFile, entry);
                    if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) continue; // Not a zip
                    var entryHashes = innerEntryHashes(bytes);
                    if (entryHashes == null || entryHashes.isEmpty()) continue; // An empty zip would count as a subset of everything
                    nestedZips.add(new NestedZip(jar, FilenameUtils.normalize(entry.getName(), true), entryHashes));
                }
            }
        }

        // A nested zip is redundant when a different JAR nests a zip that contains everything it has
        Map<File, List<String>> removals = new HashMap<>();
        Map<String, String> pathRewrites = new LinkedHashMap<>();
        Set<NestedZip> dropped = new HashSet<>();
        for (var candidate : nestedZips) {
            for (var keeper : nestedZips) {
                if (candidate == keeper || dropped.contains(keeper) || candidate.jar.equals(keeper.jar)) continue; // Two copies within one JAR are intentional
                if (candidate.path.equals(keeper.path)) continue; // Same path already deduplicates when combining
                if (!isSubsetOf(candidate, keeper)) continue;
                if (isSubsetOf(keeper, candidate) && jars.indexOf(candidate.jar) < jars.indexOf(keeper.jar)) continue; // They're equal, keep the one from the earlier JAR
                dropped.add(candidate);
                removals.computeIfAbsent(candidate.jar, _ -> new ArrayList<>()).add(candidate.path);
                pathRewrites.put(candidate.path, keeper.path);
                break;
            }
        }
        if (dropped.isEmpty()) return;

        // Drop the redundant copies and point everything that referenced them at the kept ones
        for (var jar : jars) {
            try (var jarFile = new JarFile(jar)) {
                Map<JarEntry, String> contentMapping = new HashMap<>();
                JAR.getResources(jarFile).forEach(entry -> {
                    var content = Text.decode(JAR.getResourceBytes(jarFile, entry));
                    if (content == null) return;
                    var rewritten = content;
                    for (var rewrite : pathRewrites.entrySet()) {
                        rewritten = Text.replaceToken(rewritten, rewrite.getKey(), rewrite.getValue());
                        rewritten = Text.replaceToken(rewritten, rewrite.getKey().replace('/', '\\'), rewrite.getValue().replace('/', '\\'), "\\");
                    }
                    if (!rewritten.equals(content)) contentMapping.put(entry, rewritten);
                });
                JAR.writeResources(jarFile, contentMapping, onlyIfDifferent:false);
            }
            var files = removals.get(jar);
            if (files != null) JAR.removeFiles(jar, files);
        }
    }

    /**
     * @return Map of entry name to content hash for the zip, or null if the bytes can't be read as a zip
     */
    private static Map<String, String> innerEntryHashes(byte[] zipBytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            Map<String, String> hashes = new HashMap<>();
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                hashes.put(entry.getName(), HexFormat.of().formatHex(JAR.computeSemanticHash(zip.readAllBytes())));
            }
            return hashes;
        } catch (Exception _) {
            return null;
        }
    }

    private static boolean isSubsetOf(NestedZip candidate, NestedZip keeper) {
        return candidate.entryHashes.entrySet().stream().allMatch(entry -> entry.getValue().equals(keeper.entryHashes.get(entry.getKey())));
    }
}
