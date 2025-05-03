package io.github.pacifistmc.forgix.core;

import io.github.pacifistmc.forgix.utils.JAR;
import net.fabricmc.tinyremapper.*;
import net.fabricmc.tinyremapper.api.TrLogger;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
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
@SuppressWarnings({"ConstantValue", "DataFlowIssue"}) // Don't worry these aren't real issues, IntelliJ just doesn't understand the weird manifold we have
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
    public static void relocateClasses(List<RelocationConfig> relocationConfigs, boolean doAnotherPass = false) {
        if (doAnotherPass || relocationConfigs.getFirst().tinyFile == null) generateMappings(relocationConfigs, !doAnotherPass); // Generate mappings if they don't exist

        // Return if there are no new conflicts (no new class conflicts)
        // This will return if there are no conflicts at all (empty mappings) or there are only resource conflicts which we're ignoring
        if (doAnotherPass && relocationConfigs.stream()
                .flatMap(config -> config.mappings.keySet().stream())
                .noneMatch(mapping -> mapping.endsWith(".class"))
        ) return;

        // Process each JAR file in parallel
        relocationConfigs.stream().parallel().forEach(relocationConfig -> {
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
        relocationConfigs.stream().parallel().forEach(relocationConfig -> {
            JarFile jarFile = relocationConfig.jarFile;
            if (JAR.isClosed(jarFile)) jarFile = new JarFile(jarFile.getName()); // Reopen the JAR file if it's closed (it can be closed by the `relocateClasses` method)

            Set<JarEntry> resources = JAR.getResources(jarFile);
            Map<JarEntry, String> contentMapping = new HashMap<>();

            // Create a map of conflicts with path alterations.
            Map<String, String> conflicts = new HashMap<>();
            Map<String, String> fileConflicts = new HashMap<>(); // Keep track of mixins to handle them specially
            relocationConfig.mappings.forEach((originalPath, relocatedPath) -> {
//                if (!originalPath.contains("/") && originalPath.getExtension().equals("json")) { // Is in root and is a conflicting json file, assuming mixin
//                    // Do not put mixins in a subfolder/package instead rename them with prefixed `<conflict_prefix>.` to the name
//                    fileConflicts.put(originalPath, relocatedPath.replaceFirst("/", "."));
//                    conflicts.put(originalPath, relocatedPath.replaceFirst("/", "."));
//                    return;
//                }
                if (originalPath.endsWith("META-INF/MANIFEST.MF")) return; // Skip manifest
                if (!originalPath.endsWith(".class") && !originalPath.startsWith("META-INF/services/")) { // Is a regular file conflict
                    relocatedPath = relocatedPath.everythingAfterFirst("/").addPrefixExtension(relocationConfig.conflictPrefix);
                    fileConflicts.put(originalPath, relocatedPath);
                    conflicts.put(originalPath, relocatedPath);
                    conflicts.put(originalPath.replace('/', '\\'), relocatedPath.replace('/', '\\')); // Add the original path with backslashes instead of slashes
                    return;
                }
                conflicts.put(originalPath, relocatedPath); // Add the original path which is something like com/example/Example.class
                conflicts.put(originalPath.replace('/', '\\'), relocatedPath.replace('/', '\\')); // Add the original path with backslashes instead of slashes
                conflicts.put(originalPath.removeExtension(), relocatedPath.removeExtension()); // Add the original path without the .class extension
                conflicts.put(originalPath.removeExtension().replace('/', '.'), relocatedPath.removeExtension().replace('/', '.')); // Add the original path without the .class extension and with dots instead of slashes
                conflicts.put(originalPath.removeExtension().replace('/', '\\'), relocatedPath.removeExtension().replace('/', '\\')); // Add the original path without the .class extension and with backslashes instead of slashes
            });

            for (var entry : resources) {
                String content = JAR.getResource(jarFile, entry);
                for (var conflict : conflicts.entrySet()) {
                    // TODO: Smart replace
                    //  If a file has "com.example.Meow" and "com.example.Meow2" and we're only replacing "com.example.Meow" then only replace all instances of "com.example.Meow" but not "com.example.Meow2"
                    content = content.replace(conflict.getKey(), conflict.getValue());
                }
                contentMapping.put(entry, content);
            }

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
        relocationConfigs.stream().parallel().forEach(file -> {
            var jarFile = append ? file.jarFile : new JarFile(file.jarFile.getName());
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
            if (!append) jarFile.close();
        });

        // Create mappings for conflicts
        Map<JarFile, Map<String, String>> mappings = new ConcurrentHashMap<>();
        filesByPath.forEach((_, fileInfos) -> {
            if (fileInfos.size() <= 1) return; // No conflict
            // Create mappings for all relocationConfigs
            for (FileInfo fileInfo : fileInfos) {
                String relocatedPath = FilenameUtils.normalize("${fileInfo.source.conflictPrefix}/${fileInfo.path}", true);
//                fileInfo.source.mappings.put(fileInfo.path, relocatedPath); // This doesn't work and so doesn't the alternatives, maybe fileInfo.source doesn't actually point to the original object?
                if (append) mappings.computeIfAbsent(fileInfo.source.jarFile, _ -> fileInfo.source.mappings).put(fileInfo.path, relocatedPath);
                else mappings.put(fileInfo.source.jarFile, Map.of(fileInfo.path, relocatedPath));
            }
        });

        // Update mappings in the original list by repopulating the list
        relocationConfigs.replaceAll(relocationConfig -> (RelocationConfig) (
                jarFile: relocationConfig.jarFile,
                conflictPrefix: relocationConfig.conflictPrefix,
                tinyFile: relocationConfig.tinyFile,
                mappings: mappings.getOrDefault(relocationConfig.jarFile, Map.of())
                ));
    }

    /**
     * Writes relocation mappings as tiny files. <br>
     * Namespace: original -> relocated
     * This only writes class mappings.
     */
    public static class TinyClassWriter {
        public static void write(List<RelocationConfig> relocationConfigs, File outputDirectory) {
            // Store FileWriters for each tiny file
            Map<File, FileWriter> fileWriterMap = new HashMap<>();
            // Initialize the tiny mappings file for each relocation by repopulating the list because there's no other way due to how cursed manifold is
            relocationConfigs.replaceAll(relocationConfig -> (RelocationConfig) (
                    jarFile: relocationConfig.jarFile,
                    conflictPrefix: relocationConfig.conflictPrefix,
                    mappings: relocationConfig.mappings,
                    tinyFile: new File(outputDirectory, relocationConfig.jarFile.getName().setBaseNameExtension("${relocationConfig.conflictPrefix}.tiny")).createFileWithParents("tiny\t2\t0\toriginal\trelocated\n")
                    ));

            // Iterate over each relocation and it's mappings
            relocationConfigs.forEach(relocationConfig -> {
                // We can't use `.forEach` otherwise we get a bootstrap method initialization error. The error will go away if we make TinyClassWriter be its own class AND make RelocationConfig be an inner class of Relocator
                for (var mappingsEntry : relocationConfig.mappings.entrySet()) {
                    if (!mappingsEntry.getKey().endsWith(".class")) continue; // Return if not a class file

                    // Get or create the FileWriter for the current tiny file
                    // We can't use `computeIfAbsent` otherwise we get a bootstrap method initialization error. The error will go away if we make TinyClassWriter be its own class AND make RelocationConfig be an inner class of Relocator
                    FileWriter fileWriter = fileWriterMap.get(relocationConfig.tinyFile);
                    if (fileWriter == null) {
                        fileWriter = new FileWriter(relocationConfig.tinyFile, true); // Append to the file because this method may be called in multiple passes
                        fileWriterMap.put(relocationConfig.tinyFile, fileWriter);
                    }

                    // Write class mappings
                    fileWriter.write("c\t${mappingsEntry.getKey().removeExtension()}\t${mappingsEntry.getValue().removeExtension()}\n");
                }
            });

            // Close all writers
            fileWriterMap.values().forEach(OutputStreamWriter::close);
        }
    }
}
