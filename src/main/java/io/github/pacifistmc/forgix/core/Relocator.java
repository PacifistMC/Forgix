package io.github.pacifistmc.forgix.core;

import io.github.pacifistmc.forgix.utils.JAR;
import io.github.pacifistmc.forgix.utils.Json;
import io.github.pacifistmc.forgix.utils.Text;
import io.github.pacifistmc.forgix.utils.TinyClassWriter;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.api.TrLogger;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Relocates conflicting files in JARs. The JARs are modified in place.
 */
public class Relocator {
    private static final File tempDir = Files.createTempDirectory("forgix-tiny").toFile();
    static {
        tempDir.mustDeleteOnExit();
    }

    /**
     * Relocates conflicting files in JARs.
     * @param relocationConfigs The relocationConfigs to process
     */
    public static void relocate(List<RelocationConfig> relocationConfigs) {
        relocateClasses(relocationConfigs);
        relocateResources(relocationConfigs);
    }

    /**
     * Relocates conflicting classes in JARs.
     * @param relocationConfigs The relocationConfigs to process
     */
    public static void relocateClasses(List<RelocationConfig> relocationConfigs) {
        if (relocationConfigs.getFirst().tinyFile == null) generateMappings(relocationConfigs); // Generate mappings if they don't exist

        // Process each JAR file in parallel
        relocationConfigs.parallelStream().forEach(relocationConfig -> {
            // The first JAR keeps its original names, so there might be nothing to remap in this JAR
            if (relocationConfig.mappings.keySet().stream().noneMatch(mapping -> mapping.endsWith(".class"))) return;

            // Create a tiny remapper with the mappings
            IMappingProvider tinyMappings = TinyUtils.createTinyMappingProvider(relocationConfig.tinyFile.toPath(), "original", "relocated");
            var logger = new ConsoleLogger(); logger.setLevel(TrLogger.Level.ERROR);
            TinyRemapper tinyRemapper = TinyRemapper.newRemapper(logger).withMappings(tinyMappings).ignoreConflicts(true).fixPackageAccess(true).renameInvalidLocals(true).rebuildSourceFilenames(true).resolveMissing(true).build();

            // Get the paths
            Path jarFilePath = Paths.get(relocationConfig.jarFile.getName());
            Path tempJarFilePath = Paths.get(relocationConfig.jarFile.getName().setExtension("tmp"));

            try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(tempJarFilePath).assumeArchive(true).build()) {
                // Add the jar file as a target
                outputConsumer.addNonClassFiles(jarFilePath);
                tinyRemapper.readInputs(jarFilePath);

                // Apply the mappings
                tinyRemapper.apply(outputConsumer);
            } finally {
                tinyRemapper.finish(); // Close the remapper
            }

            // Close the original JAR file
            relocationConfig.jarFile.close();

            // Replace the original JAR file with the relocated one
            Files.move(tempJarFilePath, jarFilePath, StandardCopyOption.REPLACE_EXISTING); // FIXME: Trying to update the jar location instead gives so many lambda bootstrap errors due to how cursed manifold is 😭
        });
    }

    /**
     * Relocates conflicting resources in JARs and rewrites everything that references them by name.
     * Resources that nothing references are resolved at the end instead, see {@link #resolveConventionConflicts}.
     * @param relocationConfigs The relocationConfigs to process
     */
    public static void relocateResources(List<RelocationConfig> relocationConfigs, boolean anotherPass = false) {
        // Generate mappings if they don't exist or this is another pass (the passes only look at resources since class conflicts are already settled)
        if (anotherPass || relocationConfigs.getFirst().tinyFile == null) generateMappings(relocationConfigs, append:!anotherPass, includeClasses:!anotherPass);

        // Return if there are no new conflicts
        // This will return if there are no conflicts at all (empty mappings) or there are only class and/or META-INF conflicts which we're ignoring
        if (anotherPass && relocationConfigs.stream()
                .flatMap(config -> config.mappings.keySet().stream())
                .noneMatch(mapping -> !mapping.endsWith(".class") && !mapping.startsWith("META-INF/")) // Checking to see if there's any resource that isn't a class or META-INF, if there isn't then return
        ) {
            resolveConventionConflicts(relocationConfigs);
            return;
        }

        dropUnreferencedResourceMappings(relocationConfigs);

        AtomicBoolean doAnotherPass = new AtomicBoolean(false);

        // Process each JAR file in parallel
        relocationConfigs.parallelStream().forEach(relocationConfig -> {
            JarFile jarFile = JAR.isClosed(relocationConfig.jarFile) ? new JarFile(relocationConfig.jarFile.getName()) : relocationConfig.jarFile; // Reopen the JAR file if it's closed (it can be closed by the `relocateClasses` method)

            Map<JarEntry, String> contentMapping = new ConcurrentHashMap<>();
            Map<String, String> fileRenames = new ConcurrentHashMap<>(); // Map of files to rename, these are all resources since TinyRemapper already renamed the classes
            relocationConfig.mappings.forEach((originalPath, relocatedPath) -> {
                if (!originalPath.endsWith(".class")) fileRenames.put(originalPath, relocatedPath);
            });

            JAR.getResources(jarFile).parallelStream().forEach(entry -> {
                var path = FilenameUtils.normalize(entry.getName(), true);

                // A resource named after a renamed name moves along with it (ServiceLoader files are named after their class)
                var relocatedByName = Text.rewrite(path, relocationConfig.mappings);
                if (!relocatedByName.equals(path)) fileRenames.put(path, relocatedByName);

                var bytes = JAR.getResourceBytes(jarFile, entry);
                if (path.equals(JarFile.MANIFEST_NAME)) { // The manifest needs special handling for its line wrapping
                    var rewritten = rewriteManifest(bytes, relocationConfig.mappings);
                    if (rewritten != null) contentMapping.put(entry, rewritten);
                    return;
                }
                var content = Text.decode(bytes);
                if (content == null) return; // Skip binary files
                var rewritten = Text.rewrite(content, relocationConfig.mappings, path);
                if (!rewritten.equals(content)) contentMapping.put(entry, rewritten);
            });

            if (JAR.writeResources(jarFile, contentMapping, onlyIfDifferent:false)) doAnotherPass.set(true);
            jarFile.close();
            JAR.renameResources(jarFile.getName(), fileRenames);
        });

        // Do multiple passes to handle conflicts that were created by the previous pass (a file we rewrote now differs from its copies in the other JARs, so it conflicts too)
        if (doAnotherPass.get()) {
            relocateResources(relocationConfigs, true);
            return;
        }
        resolveConventionConflicts(relocationConfigs);
    }

    /**
     * Drops the rename mappings of resources that nothing references by name.
     * These are looked up by convention (assets, data, loader descriptors and the like),
     * so renaming them would just orphan them, we resolve them at the end instead.
     */
    private static void dropUnreferencedResourceMappings(List<RelocationConfig> relocationConfigs) {
        var pendingPaths = relocationConfigs.stream().flatMap(config -> config.mappings.keySet().stream()).filter(path -> !path.endsWith(".class")).collect(Collectors.toSet());
        if (pendingPaths.isEmpty()) return;

        Set<String> referencedPaths = ConcurrentHashMap.newKeySet();
        relocationConfigs.parallelStream().forEach(config -> {
            try (var jarFile = new JarFile(config.jarFile.getName())) {
                JAR.getResources(jarFile).parallelStream().forEach(entry -> {
                    var entryPath = FilenameUtils.normalize(entry.getName(), true);
                    var content = entryPath.equals(JarFile.MANIFEST_NAME) ? manifestText(JAR.getResourceBytes(jarFile, entry)) : Text.decode(JAR.getResourceBytes(jarFile, entry));
                    if (content == null) return;
                    pendingPaths.stream()
                            .filter(path -> !referencedPaths.contains(path) && !path.equals(entryPath)) // A file mentioning its own path doesn't count as a reference
                            .filter(path -> Text.containsToken(content, path) || Text.containsToken(content, path.replace('/', '\\'), "\\"))
                            .forEach(referencedPaths::add);
                });
            }
        });

        relocationConfigs.forEach(config -> config.mappings.keySet().removeIf(path -> !path.endsWith(".class") && !referencedPaths.contains(path)));
    }

    /**
     * Resolves the same-path conflicts that are still left once all the renaming settled.
     * Nothing references these by name so they must keep their path,
     * we merge the copies if they don't contradict each other, otherwise the first JAR's copy wins.
     */
    private static void resolveConventionConflicts(List<RelocationConfig> relocationConfigs) {
        record Copy(RelocationConfig source, String text) { }

        // Collect the distinct copies of each path, in JAR order
        Map<String, List<Copy>> copiesByPath = new LinkedHashMap<>();
        Map<String, Set<String>> seenHashesByPath = new HashMap<>();
        for (var config : relocationConfigs) {
            try (var jarFile = new JarFile(config.jarFile.getName())) {
                for (var entry : Collections.list(jarFile.entries())) {
                    if (entry.isDirectory()) continue;
                    var path = FilenameUtils.normalize(entry.getName(), true);
                    if (path.endsWith(".class") || cannotConflict(path)) continue; // Classes were already relocated and these can't conflict
                    var bytes = JAR.getResourceBytes(jarFile, entry);
                    if (seenHashesByPath.computeIfAbsent(path, _ -> new LinkedHashSet<>()).add(HexFormat.of().formatHex(JAR.computeSemanticHash(bytes)))) {
                        copiesByPath.computeIfAbsent(path, _ -> new ArrayList<>()).add(new Copy(config, Text.decode(bytes)));
                    }
                }
            }
        }
        copiesByPath.values().removeIf(copies -> copies.size() <= 1);
        if (copiesByPath.isEmpty()) return;

        Map<RelocationConfig, Map<String, String>> mergedWrites = new HashMap<>();
        Map<RelocationConfig, List<String>> removals = new HashMap<>();

        copiesByPath.forEach((path, copies) -> {
            // Try to merge the copies, if they genuinely contradict each other the first JAR's copy wins
            var elements = copies.stream().map(copy -> copy.text == null ? null : Json.parse(copy.text)).toList();
            var merged = elements.contains(null) ? null : elements.getFirst();
            for (var element : elements.subList(1, elements.size())) {
                if (merged == null) break;
                merged = Json.merge(merged, element);
            }
            if (merged != null) {
                mergedWrites.computeIfAbsent(copies.getFirst().source, _ -> new HashMap<>()).put(path, Json.write(merged));
            } else {
                var kept = copies.getFirst().source.conflictPrefix;
                var lost = copies.stream().skip(1).map(copy -> copy.source.conflictPrefix).toList();
                "${path} differs between the jars but nothing references it so it can't be relocated! Keeping the copy from ${kept} and dropping ${lost}".err();
            }
            copies.stream().skip(1).forEach(copy -> removals.computeIfAbsent(copy.source, _ -> new ArrayList<>()).add(path));
        });

        // Apply the merges and drop the losing copies
        Stream.concat(mergedWrites.keySet().stream(), removals.keySet().stream()).distinct().toList().parallelStream().forEach(config -> {
            try (var zipFile = new ZipFile(config.jarFile.getName())) {
                var files = removals.get(config);
                if (files != null) zipFile.removeFiles(files);
                var writes = mergedWrites.get(config);
                if (writes != null) writes.forEach((path, content) -> {
                    var params = new ZipParameters();
                    params.setFileNameInZip(path);
                    params.setOverrideExistingFilesInZip(true);
                    zipFile.addStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), params);
                });
            }
        });
    }

    /**
     * Rewrites references inside the manifest attribute values.
     * Manifests wrap lines at 72 bytes which would split names in half, so we rewrite the parsed values instead of the plain text.
     * @return The rewritten manifest, or null if nothing changed
     */
    private static String rewriteManifest(byte[] bytes, Map<String, String> mappings) {
        var manifest = new Manifest(new ByteArrayInputStream(bytes));
        var changed = false;
        for (var attributes : Stream.concat(Stream.of(manifest.getMainAttributes()), manifest.getEntries().values().stream()).toList()) {
            for (var entry : attributes.entrySet()) {
                var value = entry.getValue().toString();
                var rewritten = Text.rewrite(value, mappings, JarFile.MANIFEST_NAME);
                if (!rewritten.equals(value)) {
                    entry.setValue(rewritten);
                    changed = true;
                }
            }
        }
        if (!changed) return null;
        var output = new ByteArrayOutputStream();
        manifest.write(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    /**
     * @return The manifest's attribute values as scannable text (raw manifests wrap long lines which would split names in half)
     */
    private static String manifestText(byte[] bytes) {
        var manifest = new Manifest(new ByteArrayInputStream(bytes));
        return Stream.concat(Stream.of(manifest.getMainAttributes()), manifest.getEntries().values().stream()).flatMap(attributes -> attributes.values().stream()).map(Object::toString).collect(Collectors.joining("\n"));
    }

    /**
     * Sets up the mappings for conflicting files in JARs.
     * @param relocationConfigs The relocationConfigs to process
     * @param append Isn't a good name, but we set it to false to check if we have any new conflicts,
     *               setting it to false will ignore previous mappings and overwrite them,
     *               but it will still append the mappings to the tiny file
     * @param includeClasses Whether to also look at classes, the resource passes set this to false
     *                       since class conflicts are already settled by the time they run
     */
    public static void generateMappings(List<RelocationConfig> relocationConfigs, boolean append = true, boolean includeClasses = true) {
        mapConflicts(relocationConfigs, append, includeClasses);
        TinyClassWriter.write(relocationConfigs, tempDir);
    }

    /**
     * Maps conflicting entries to their relocated paths.
     * Entries conflict when the same path has different content in different JARs, but we compare by meaning,
     * so JSON that only differs in formatting or key order never conflicts.
     * The first JAR keeps its original names, only the copies in the other JARs get relocated.
     * @param relocationConfigs The list of relocationConfigs to process
     * @param append Whether to append to the existing mappings
     * @param includeClasses Whether to also look at classes
     */
    private static void mapConflicts(List<RelocationConfig> relocationConfigs, boolean append = true, boolean includeClasses = true) {
        record FileInfo(String path, String hash, int order, RelocationConfig source) { }

        // Map to store all relocationConfigs and their hashes grouped by path
        Map<String, List<FileInfo>> filesByPath = new ConcurrentHashMap<>();
        // Which classes of a JAR reference which of its class names, so we can spread renames to the classes that use them
        Map<RelocationConfig, Map<String, Set<String>>> referencesByName = new ConcurrentHashMap<>();

        // Process each JAR file in parallel
        IntStream.range(0, relocationConfigs.size()).parallel().forEach(order -> {
            var file = relocationConfigs.get(order);
            var jarFile = append ? file.jarFile : new JarFile(file.jarFile.getName()); // If we're not appending, we need to reopen the JAR file

            // The names of all classes in this JAR, a referenced name only matters when it's one of them
            Set<String> classNames = !includeClasses ? Set.of() : Collections.list(jarFile.entries()).stream().map(entry -> FilenameUtils.normalize(entry.getName(), true)).filter(name -> name.endsWith(".class")).map(name -> name.removeExtension()).collect(Collectors.toSet());

            var references = referencesByName.computeIfAbsent(file, _ -> new HashMap<>());
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                // Normalize the path to use forward slashes (JAR standard)
                String path = FilenameUtils.normalize(entry.getName(), true);
                if (cannotConflict(path)) continue;
                if (path.endsWith(".class") && !includeClasses) continue;
                var bytes = JAR.getResourceBytes(jarFile, entry);
                filesByPath.computeIfAbsent(path, _ -> Collections.synchronizedList(new ArrayList<>())).add(new FileInfo(path, HexFormat.of().formatHex(JAR.computeSemanticHash(bytes)), order, file));
                if (path.endsWith(".class")) {
                    for (var name : referencedNames(bytes, classNames)) references.computeIfAbsent(name, _ -> new LinkedHashSet<>()).add(path);
                }
            }
            if (!append) jarFile.close(); // Close the jar if we opened it
        });

        if (!append) { // remove all mappings from the relocation configs as we're not appending
            relocationConfigs.forEach(config -> config.setMappings(new HashMap<>()));
        }

        // Every name that already exists, so relocated paths can never collide with a real entry or each other
        Set<String> takenPaths = new HashSet<>(filesByPath.keySet());

        // Create mappings for conflicts
        filesByPath.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(filesEntry -> {
            var fileInfos = filesEntry.getValue();
            if (fileInfos.size() <= 1) return;
            fileInfos.sort(Comparator.comparingInt(FileInfo::order));

            // Group the copies by content, the earliest JAR of each distinct content leads the group
            List<List<FileInfo>> groups = new ArrayList<>();
            for (var fileInfo : fileInfos) {
                groups.stream().filter(group -> group.getFirst().hash.equals(fileInfo.hash)).findFirst().orElseGet(() -> { var group = new ArrayList<FileInfo>(); groups.add(group); return group; }).add(fileInfo);
            }
            if (groups.size() <= 1) return; // All copies have the same content, nothing conflicts

            // The first group keeps the original name, every copy in the other groups shares one relocated path
            groups.stream().skip(1).forEach(group -> {
                var relocated = claimRelocatedPath(filesEntry.getKey(), group.getFirst().source.conflictPrefix, takenPaths);
                group.forEach(fileInfo -> fileInfo.source.mappings.putIfAbsent(fileInfo.path, relocated));
            });
        });

        if (!includeClasses) return;

        // Spread the renames through the reference graph, remapping changes the bytecode of every class
        // that references a relocated class, so copies that used to be identical between the JARs have to split too.
        // Example:
        // ```
        // class A {
        //     public static Object get() {
        //         return Minecraft.getInstance();
        //     }
        // }
        // class B {
        //     public static void use() {
        //         System.out.println(A.get());
        //     }
        // }
        // ```
        // A differs between the JARs and becomes `A_fabric` and `A_forge`, which one would a single shared B call?
        // So B splits along with A, and everything referencing B follows, until this settles.
        relocationConfigs.forEach(config -> {
            var references = referencesByName.get(config);
            Deque<String> pending = config.mappings.keySet().stream().filter(path -> path.endsWith(".class")).map(path -> path.removeExtension()).sorted().collect(Collectors.toCollection(ArrayDeque::new));
            while (!pending.isEmpty()) {
                var name = pending.poll();
                for (var referencingPath : references.getOrDefault(name, Set.of())) {
                    if (config.mappings.containsKey(referencingPath)) continue;
                    if (filesByPath.get(referencingPath).size() <= 1) continue; // Only present in this JAR, there's nothing to split from
                    config.mappings.put(referencingPath, claimRelocatedPath(referencingPath, config.conflictPrefix, takenPaths));
                    pending.add(referencingPath.removeExtension());
                }
            }
        });
    }

    /**
     * The internal names of the JAR's own classes that this class references.
     * The constant pool stores them as plain text (class entries, descriptors, signatures),
     * so we can find them all by tokenizing the raw bytes without parsing the class format.
     * A token can have one junk character in front (the low byte of the length prefix) and a leading 'L' from descriptors, so we check those variants too.
     */
    private static Set<String> referencedNames(byte[] classBytes, Set<String> classNames) {
        var content = new String(classBytes, StandardCharsets.ISO_8859_1);
        Set<String> names = new HashSet<>();
        var start = -1;
        for (var i = 0; i <= content.length(); i++) {
            var c = i < content.length() ? content.charAt(i) : ' ';
            if (c == '/' || c == '_' || c == '$' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                if (start == -1) start = i;
                continue;
            }
            if (start == -1) continue;
            var token = content.substring(start, i);
            start = -1;
            for (var variant : new String[] { token, token.substring(1) }) {
                if (classNames.contains(variant)) names.add(variant);
                if (variant.startsWith("L") && classNames.contains(variant.substring(1))) names.add(variant.substring(1));
            }
        }
        return names;
    }

    /**
     * Files that must never be treated as conflicts: the manifest and services get merged
     * across JARs when combining, and module-info/package-info must keep their JVM-mandated names.
     */
    private static boolean cannotConflict(String path) {
        return path.equals(JarFile.MANIFEST_NAME) || path.startsWith("META-INF/services/") || path.endsWith("-info.class");
    }

    private static String claimRelocatedPath(String path, String prefix, Set<String> takenPaths) {
        var relocated = path.addPrefixExtension(prefix);
        for (var attempt = 2; !takenPaths.add(relocated); attempt++) relocated = path.addPrefixExtension("${prefix}${attempt}");
        return relocated;
    }
}
