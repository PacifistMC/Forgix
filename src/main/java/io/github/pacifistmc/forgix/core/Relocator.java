package io.github.pacifistmc.forgix.core;

import io.github.pacifistmc.forgix.utils.JAR;
import io.github.pacifistmc.forgix.utils.TinyClassWriter;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.api.TrLogger;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Relocates conflicting files in JARs.
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
    private static final Map<JarFile, Map<String, String>> mappingsSnapshot = new ConcurrentHashMap<>(); // Store a snapshot of the mappings so we can restore it at the end of all passes
    public static void relocateClasses(List<RelocationConfig> relocationConfigs, boolean doAnotherPass = false) {
        if (doAnotherPass || relocationConfigs.getFirst().tinyFile == null) generateMappings(relocationConfigs, !doAnotherPass); // Generate mappings if they don't exist

        // Return if there are no new conflicts (no new class conflicts)
        // This will return if there are no conflicts at all (empty mappings) or there are only resource conflicts which we're ignoring
        if (doAnotherPass && relocationConfigs.parallelStream()
                .flatMap(config -> config.getMappings().keySet().parallelStream())
                .noneMatch(mapping -> mapping.endsWith(".class"))
        ) {
            relocationConfigs.parallelStream().forEach(config -> config.setMappings(mappingsSnapshot.get(config.getJarFile())));
            return;
        }

        // Process each JAR file in parallel
        relocationConfigs.parallelStream().forEach(relocationConfig -> {
            // Update mapping snapshot, merge with existing ones
            // TODO: Make mappingsSnapshot faster, relocateResources needs access to class mappings and
            //  doing multiple passes of class relocation resets the mappings so we need to store a snapshot of the mappings
            //  in order to restore them at the end of all passes
            mappingsSnapshot.computeIfAbsent(relocationConfig.getJarFile(), _ -> new ConcurrentHashMap<>());
            mappingsSnapshot.computeIfPresent(relocationConfig.getJarFile(), (_, existingMap) -> {
                existingMap.putAll(relocationConfig.mappings);
                return existingMap;
            });

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
            Files.move(tempJarFilePath, jarFilePath, StandardCopyOption.REPLACE_EXISTING); // TODO: Probably don't overwrite, update the jar location in relocation config instead. FIXME: Trying to update the jar location gives so many lambda bootstrap errors due to how cursed manifold is 😭
        });

        // Do multiple passes to handle possible conflicts created by the previous pass
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
        // A will get remapped to `fabric.A` and `forge.A`, which will B call?
        relocateClasses(relocationConfigs, true);
    }

    /**
     * Relocates conflicting resources in JARs.
     * @param relocationConfigs The relocationConfigs to process
     */
    public static void relocateResources(List<RelocationConfig> relocationConfigs, boolean anotherPass = false) {
        // Generate mappings if they don't exist or this is another pass
        if (anotherPass || relocationConfigs.getFirst().tinyFile == null) generateMappings(relocationConfigs, !anotherPass);

        // Return if there are no new conflicts
//        if (anotherPass && relocationConfigs.stream().allMatch(config -> config.mappings.isEmpty())) return;
        // This will return if there are no conflicts at all (empty mappings) or there are only class and/or META-INF conflicts which we're ignoring
        if (anotherPass && relocationConfigs.stream()
                .flatMap(config -> config.mappings.keySet().stream())
                .noneMatch(mapping -> !mapping.endsWith(".class") && !mapping.startsWith("META-INF/")) // Checking to see if there's any resource that isn't a class or META-INF, if there isn't then return
        ) return;

        AtomicBoolean doAnotherPass = new AtomicBoolean(false);

        // Process each JAR file in parallel
        relocationConfigs.parallelStream().forEach(relocationConfig -> {
            JarFile jarFile = JAR.isClosed(relocationConfig.jarFile) ? new JarFile(relocationConfig.jarFile.getName()) : relocationConfig.jarFile; // Reopen the JAR file if it's closed (it can be closed by the `relocateClasses` method)

            Set<JarEntry> resources = JAR.getResources(jarFile);
            Map<JarEntry, String> contentMapping = new HashMap<>();

            // Create a map of conflicts with path alterations.
            Map<String, String> conflicts = new HashMap<>();
            Map<String, String> fileConflicts = new HashMap<>(); // Keep track of mixins to handle them specially
            relocationConfig.mappings.forEach((originalPath, relocatedPath) -> {
                if (originalPath.endsWith("META-INF/MANIFEST.MF")) return; // Skip manifest
                if (!originalPath.endsWith(".class") && !originalPath.startsWith("META-INF/services/")) { // Is a regular file conflict
                    fileConflicts.put(originalPath, relocatedPath);
                }
                // replacing with `removeExtension()` would make the ones with extensions be replaced which is what we want
                conflicts.put(originalPath.removeExtension(), relocatedPath.removeExtension()); // Add the original path without the .class extension
                conflicts.put(originalPath.removeExtension().replace('/', '.'), relocatedPath.removeExtension().replace('/', '.')); // Add the original path without the .class extension and with dots instead of slashes
                conflicts.put(originalPath.removeExtension().replace('/', '\\'), relocatedPath.removeExtension().replace('/', '\\')); // Add the original path without the .class extension and with backslashes instead of slashes
                if (originalPath.endsWith(".class")) { // Is a class conflict
                    conflicts.put("\"${originalPath.getBaseName().removeExtension()}\"", "\"${relocatedPath.getBaseName().removeExtension()}\""); // Just the filename without the path & extension and in quotes (for mixins)
                }
            });

            resources.parallelStream().forEach(entry -> {
                String content = JAR.getResource(jarFile, entry);
                for (var conflict : conflicts.entrySet()) {
                    // TODO: Smart replace
                    //  If a file has "com.example.Meow" and "com.example.Meow2" and we're only replacing "com.example.Meow" then only replace all instances of "com.example.Meow" but not "com.example.Meow2"
                    content = content.replace(conflict.getKey(), conflict.getValue());
                }
                contentMapping.put(entry, content);
            });

            doAnotherPass.set(JAR.writeResources(jarFile, contentMapping));
            jarFile.close();
            JAR.renameResources(jarFile.getName(), fileConflicts);
        });

        // Do a multiple passes to handle conflicts that were created by the previous pass
        if (doAnotherPass.get()) relocateResources(relocationConfigs, true);
    }

    /**
     * Sets up the mappings for conflicting files in JARs.
     * @param relocationConfigs The relocationConfigs to process
     * @param append Isn't a good name, but we set it to false to check if we have any new conflicts,
     *               setting it to false will ignore previous mappings and overwrite them,
     *               but it will still append the mappings to the tiny file
     */
    public static void generateMappings(List<RelocationConfig> relocationConfigs, boolean append = true) {
        mapConflicts(relocationConfigs, append);
        TinyClassWriter.write(relocationConfigs, tempDir);
    }

    /**
     * Maps conflicting entries to their relocated paths.
     * @param relocationConfigs The list of relocationConfigs to process
     * @param append Whether to append to the existing mappings
     */
    private static void mapConflicts(List<RelocationConfig> relocationConfigs, boolean append = true) {
        record FileInfo(String path, byte[] hash, RelocationConfig source) { }

        // Map to store all relocationConfigs and their hashes grouped by path
        Map<String, List<FileInfo>> filesByPath = new ConcurrentHashMap<>();

        // Process each JAR file in parallel
        relocationConfigs.parallelStream().forEach(file -> {
            var jarFile = append ? file.jarFile : new JarFile(file.jarFile.getName()); // If we're not appending, we need to reopen the JAR file
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    // Normalize the path to use forward slashes (JAR standard)
                    String path = FilenameUtils.normalize(entry.getName(), true);
                    byte[] hash = JAR.computeHash(jarFile, entry);

                    filesByPath.compute(path, (_, existing) -> {
                        var list = existing == null ? new ArrayList<FileInfo>() : existing;
                        // Only add if the hash is different
                        if (list.stream().noneMatch(info -> Arrays.equals(info.hash, hash)))
                            list.add(new FileInfo(path, hash, file));
                        return list;
                    });
                }
            }
            if (!append) jarFile.close(); // Close the jar if we opened it
        });

        if (!append) { // remove all mappings from the relocation configs as we're not appending
            relocationConfigs.forEach(config -> config.setMappings(new HashMap<>()));
        }

        // Create mappings for conflicts
        filesByPath.forEach((_, fileInfos) -> {
            // Return if there are no conflicts
            if (fileInfos.size() <= 1) return;

            // Create mappings for all relocationConfigs
            fileInfos.forEach(fileInfo ->
                fileInfo.source.mappings.putIfAbsent(fileInfo.path, fileInfo.path.addPrefixExtension(fileInfo.source.conflictPrefix))
            );
        });
    }
}
