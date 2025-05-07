package io.github.pacifistmc.forgix.core;

import com.google.gson.Gson;
import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.multiversion.versioning.ForgixVersionJson;
import io.github.pacifistmc.forgix.utils.JAR;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Multiversion {
    private static final File tempDir = Files.createTempDirectory("forgix-multiversion").toFile();
    private static final File multiversionJar = tempDir.toPath().resolve("forgix-multiversion.jar").toFile();
    private static final String uuid = "forgix_multiversion_${UUID.randomUUID().toString().replace(\"-\", \"\")}";
    private static final Gson gson = new Gson();

    static {
        tempDir.mustDeleteOnExit();


        try (var multiversionJarResource = Multiversion.class.getResourceAsStream("/multiversion/forgix-multiversion.jar")) {
            if (multiversionJarResource == null) throw new RuntimeException("Could not find internal multiversion jar. This should never happen!");
            IOUtils.copy(multiversionJarResource, Files.newOutputStream(multiversionJar.toPath()));
        }

        try (var jarFile = new JarFile(multiversionJar)) {
            Map<String, String> renameMap = new HashMap<>();
            JAR.getClasses(jarFile).forEach(entry -> renameMap.put(entry.name, "${uuid}/${entry.name.replace(\"-\", \"_\")}")); // TODO: jvmdg can create invalid package names, it made something like `hello_world-neoforge` which neoforge doesn't like

            var relocationConfig = new RelocationConfig(jarFile, uuid);
            relocationConfig.setMappings(renameMap);
            Relocator.relocate(List.of(relocationConfig));
        }

        JAR.removeFiles(multiversionJar, List.of(
                "META-INF/forgix/"
        ));
    }

    /**
     * Merges multiple versions of a mod into a single JAR file.
     * @param versionsAndFilePathMap The map of versions and their corresponding file paths <br>
     *                               Note: The version key is not semver! It is using Forge's version range format.
     * @return The merged JAR file as a ByteArrayOutputStream
     */
    public static ByteArrayOutputStream mergeVersions(Map<String, Path> versionsAndFilePathMap, String fabricModId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            var versionsJson = new ForgixVersionJson();
            zos.setLevel(9); // Maximum compression

            // Add contents from the multiversion jar
            try (var zipFile = new ZipFile(multiversionJar)) {
                zipFile.getFileHeaders().forEach(header -> {
                    var name = header.getFileName();
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(name.equals("pack.mcmeta") ?
                            IOUtils.toString(zipFile.getInputStream(header), StandardCharsets.UTF_8)
                                    .replace("Forgix-Multiversion-Mod", uuid) // Replace the mod name in pack.mcmeta with a UUID to avoid conflicts
                                    .getBytes(StandardCharsets.UTF_8)
                            : zipFile.getInputStream(header).readAllBytes());
                    zos.closeEntry();
                });
            }

            // First, identify common files across all jars
            Map<String, byte[]> commonFiles = findCommonFiles(versionsAndFilePathMap.values());

            // Add common files to the main jar (the jar we're creating)
            commonFiles.forEach((fileName, content) -> {
                if (isMetadataFile(fileName)) return; // Skip metadata files since they should be included in each jar separately
                zos.putNextEntry(new ZipEntry(fileName));
                zos.write(content);
                zos.closeEntry();
            });

//            // Relocate conflicting files to avoid module export issues on forge
//            var relocationConfigs = new ArrayList<RelocationConfig>();
//            versionsAndFilePathMap.forEach((_, path) -> relocationConfigs.add(new RelocationConfig(new JarFile(path.toFile()), "forgix_multiversion_${UUID.randomUUID().toString().replace(\"-\", \"\")}")));
//            Relocator.relocateClasses(relocationConfigs);

            // Add the version-specific jars with only unique files
            versionsAndFilePathMap.forEach((version, path) -> {
                // Create a temporary STORED jar with only unique files
                ByteArrayOutputStream storedJarBytes = new ByteArrayOutputStream();
                try (var sourceJar = new ZipFile(path.toFile());
                     var storedZos = new ZipOutputStream(storedJarBytes)) {

                    // Set default method to STORED for all entries
                    storedZos.setMethod(ZipOutputStream.STORED);

                    // Copy only unique entries or metadata files as STORED
                    sourceJar.getFileHeaders().forEach(header -> {
                        String fileName = header.getFileName();

                        // Skip common files unless they're metadata files which should be included in each jar even if they're common
                        if (commonFiles.containsKey(fileName) && !isMetadataFile(fileName)) return;

                        ZipEntry newEntry = new ZipEntry(fileName);
                        byte[] content = sourceJar.getInputStream(header).readAllBytes();

                        // Set required fields for STORED entries
                        newEntry.setMethod(ZipEntry.STORED);
                        newEntry.setSize(content.length);
                        newEntry.setCrc(calculateCrc(content));

                        storedZos.putNextEntry(newEntry);
                        storedZos.write(content);
                        storedZos.closeEntry();
                    });
                }

                // Add the STORED jar to the output
                var pathInJar = "META-INF/forgix/multiversion/${path.fileName}";
                zos.putNextEntry(new ZipEntry(pathInJar));
                zos.write(storedJarBytes.toByteArray());
                zos.closeEntry();
                versionsJson.getVersions().put(version, pathInJar);
            });

            // Create the multiversion json file
            zos.putNextEntry(new ZipEntry("META-INF/forgix/multiversion.json"));
            zos.write(gson.toJson(versionsJson).getBytes());
            zos.closeEntry();

            // Create the fabric mod json file
            if (fabricModId != null) {
                zos.putNextEntry(new ZipEntry("fabric.mod.json"));
                zos.write(gson.toJson(new FabricModJson(versionsJson.versions.values(), fabricModId)).getBytes());
                zos.closeEntry();
            }

            zos.finish();
        }
        return baos;
    }

    /**
     * Finds files that are common across all jars (identical content)
     * TODO: Make this better after I'm done with testing this
     */
    private static Map<String, byte[]> findCommonFiles(Collection<Path> jarPaths) {
        // First, collect all files from all jars
        Map<String, Map<String, byte[]>> allFiles = new HashMap<>();
        Map<String, Integer> fileOccurrences = new HashMap<>();

        for (Path jarPath : jarPaths) {
            String jarId = jarPath.getFileName().toString();
            Map<String, byte[]> jarContents = new HashMap<>();
            allFiles.put(jarId, jarContents);

            try (var zipFile = new ZipFile(jarPath.toFile())) {
                zipFile.getFileHeaders().forEach(header -> {
                    if (!header.isDirectory()) {
                        String fileName = header.getFileName();
                        byte[] content = zipFile.getInputStream(header).readAllBytes();
                        jarContents.put(fileName, content);
                        fileOccurrences.merge(fileName, 1, Integer::sum);
                    }
                });
            }
        }

        // Find files that appear in all jars with identical content
        Map<String, byte[]> commonFiles = new HashMap<>();
        int totalJars = jarPaths.size();

        for (String fileName : fileOccurrences.keySet()) {
            if (fileOccurrences.get(fileName) == totalJars) {
                // This file appears in all jars, check if content is identical
                byte[] firstContent = null;
                boolean allMatch = true;

                for (Map<String, byte[]> jarContents : allFiles.values()) {
                    byte[] content = jarContents.get(fileName);
                    if (firstContent == null) {
                        firstContent = content;
                    } else if (!Arrays.equals(firstContent, content)) {
                        allMatch = false;
                        break;
                    }
                }

                if (allMatch && firstContent != null) {
                    commonFiles.put(fileName, firstContent);
                }
            }
        }

        return commonFiles;
    }

    /**
     * Merges multiple versions of a mod into a single JAR file.
     * Automatically determines the version ranges from the mods.toml file.
     * @param jars The collection of JAR files to merge
     * @return The merged JAR file as a ByteArrayOutputStream
     */
    public static ByteArrayOutputStream mergeVersions(Collection<File> jars) {
        Map<String, Path> versionsAndFilePathMap = new ConcurrentHashMap<>();
        AtomicReference<String> fabricModId = new AtomicReference<>();
        // Process each jar to extract version information
        jars.parallelStream().forEach(jar -> {
            try (var zipFile = new ZipFile(jar)) {
                String mcVersionRange = "[0,)"; // Default to match all versions

                // Try to read from mods.toml or neoforge.mods.toml
                String tomlContent;
                var modToml = zipFile.getFileHeader("META-INF/mods.toml");
                var neoForgeModToml = zipFile.getFileHeader("META-INF/neoforge.mods.toml");

                if (modToml != null) {
                    tomlContent = IOUtils.toString(zipFile.getInputStream(modToml), StandardCharsets.UTF_8);
                } else if (neoForgeModToml != null) {
                    tomlContent = IOUtils.toString(zipFile.getInputStream(neoForgeModToml), StandardCharsets.UTF_8);
                } else {
                    throw new RuntimeException("No mods.toml or neoforge.mods.toml found in jar: " + jar.getName());
                }

                var fabricModsJson = zipFile.getFileHeader("fabric.mod.json");
                if (fabricModsJson != null && fabricModId.get() == null) {
                    fabricModId.set(gson.fromJson(IOUtils.toString(zipFile.getInputStream(fabricModsJson), StandardCharsets.UTF_8), FabricModJson.class).id);
                }

                // Use Regex for now to extract the version range
                // TODO: Use a proper TOML parser
                var pattern = Pattern.compile(
                        "\\[\\[dependencies\\.[^]]+]]\\s*" +  // Match dependency section
                                "(?:.|\\s)*?" +                           // Any content in between
                                "modId\\s*=\\s*\"minecraft\"\\s*" +       // Match modId = "minecraft"
                                "(?:.|\\s)*?" +                           // Any content in between
                                "versionRange\\s*=\\s*\"([^\"]*)\"",      // Capture the version range
                        Pattern.DOTALL
                );

                var matcher = pattern.matcher(tomlContent);
                if (matcher.find() && matcher.groupCount() >= 1) {
                    mcVersionRange = matcher.group(1);
                }

                // Add to our map with the extracted version range
                versionsAndFilePathMap.put(mcVersionRange, jar.toPath());
            }
        });
        return mergeVersions(versionsAndFilePathMap, fabricModId.get());
    }

    /**
     * Fabric has built in support for multiversion that works on all mc versions, so we use that
     * It's mainly because the Fabric loader and Minecraft are independent, so the latest Fabric loader will work on all mc versions
     */
    public static class FabricModJson {
        public int schemaVersion = 1;
        public String id = "${uuid.first(60)}";
        public String version = Forgix.VERSION;
        public List<Map<String, String>> jars = new ArrayList<>();
        public Map<String, Object> custom = Map.of(
                "modmenu", Map.of(
                        "badges", List.of("library")
                )
        );
        public Map<String, Object> depends = new HashMap<>();

        public FabricModJson(Collection<String> jars, String modId) {
            jars.forEach(path -> {
                Map<String, String> jarEntry = new HashMap<>();
                jarEntry.put("file", path);
                this.jars.add(jarEntry);
            });
            this.depends.put(modId, "*");
        }
    }

    // Helper method to calculate CRC
    private static long calculateCrc(byte[] data) {
        var crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    // These are the set of files that are kept in each version-specific jar if they exist and aren't added to the main jar
    // Even if they are common since adding them to the main jar would cause issues
    private static final Set<String> COMMON_METADATA_FILES = Set.of(
            "META-INF/mods.toml",
            "META-INF/neoforge.mods.toml",
            "pack.mcmeta",
            "fabric.mod.json"
    );
    private static final Set<String> META_INF_PREFIXES = Set.of(
            "META-INF/MANIFEST.MF",
            "META-INF/maven/",
            "META-INF/services/"
    );
    private static final Set<String> META_INF_SUFFIXES = Set.of(
            ".png"
    );

    /**
     * Determines if a file is a metadata file that should be kept in version-specific jars
     */
    private static boolean isMetadataFile(String fileName) {
        if (COMMON_METADATA_FILES.contains(fileName)) return true;
        for (String prefix : META_INF_PREFIXES) {
            if (fileName.startsWith(prefix)) return true;
        }
        for (String suffix : META_INF_SUFFIXES) {
            if (fileName.endsWith(suffix)) return true;
        }
        return false;
    }
}
