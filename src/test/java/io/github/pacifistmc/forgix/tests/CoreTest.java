package io.github.pacifistmc.forgix.tests;

import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.core.Multiversion;
import io.github.pacifistmc.forgix.core.Relocator;
import io.github.pacifistmc.forgix.utils.JAR;
import io.github.pacifistmc.forgix.utils.TinyClassWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import io.github.pacifistmc.forgix.core.RelocationConfig;

import static org.junit.jupiter.api.Assertions.*;

public class CoreTest {
    private static final boolean debug = true;

    private File exampleJarFile;

    private File differentJarA;
    private File differentJarB;

    private File mergeJarA;
    private File mergeJarB;

    private File version_1_16_5;
    private File version_1_21_5;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws URISyntaxException {
        // Set up test JAR files
        URL resource = getClass().getClassLoader().getResource("jars/example.jar");
        assertNotNull(resource, "Example JAR file not found in resources");
        exampleJarFile = new File(resource.toURI());
        assertTrue(exampleJarFile.exists(), "Example JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/conflict-a.jar");
        assertNotNull(resource, "Different A JAR file not found in resources");
        differentJarA = new File(resource.toURI());
        assertTrue(differentJarA.exists(), "Different A JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/conflict-b.jar");
        assertNotNull(resource, "Different B JAR file not found in resources");
        differentJarB = new File(resource.toURI());
        assertTrue(differentJarB.exists(), "Different B JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/merge-a.jar");
        assertNotNull(resource, "Merge A JAR file not found in resources");
        mergeJarA = new File(resource.toURI());
        assertTrue(mergeJarA.exists(), "Merge A JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/merge-b.jar");
        assertNotNull(resource, "Merge B JAR file not found in resources");
        mergeJarB = new File(resource.toURI());
        assertTrue(mergeJarB.exists(), "Merge B JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/multiversion/1.16.5.jar");
        assertNotNull(resource, "1.16.5 JAR file not found in resources");
        version_1_16_5 = new File(resource.toURI());
        assertTrue(version_1_16_5.exists(), "1.16.5 JAR file does not exist");

        resource = getClass().getClassLoader().getResource("jars/multiversion/1.21.5.jar");
        assertNotNull(resource, "1.21.5 JAR file not found in resources");
        version_1_21_5 = new File(resource.toURI());
        assertTrue(version_1_21_5.exists(), "1.21.5 JAR file does not exist");
    }

    @AfterEach
    void tearDown() {
        // Clean up temporary files created during tests
        tempDir.toFile().delete();
    }

    /**
     * Test for when no mapping conflicts are present.
     */
    @Test
    void testNoMappingConflicts() throws IOException {
        // Copy example jar into the temp directory twice to simulate no conflicts
        File exampleJarCopy1 = tempDir.resolve("example1.jar").toFile();
        File exampleJarCopy2 = tempDir.resolve("example2.jar").toFile();
        FileUtils.copyFile(exampleJarFile, exampleJarCopy1);
        FileUtils.copyFile(exampleJarFile, exampleJarCopy2);

        try(JarFile exampleJar1 = new JarFile(exampleJarCopy1);
            JarFile exampleJar2 = new JarFile(exampleJarCopy2)) {
            // Run the relocation process
            List<RelocationConfig> files = new ArrayList<>(List.of(
                    new RelocationConfig(exampleJar1, "diffA"),
                    new RelocationConfig(exampleJar2, "diffB")
            ));
            Relocator.generateMappings(files);

            // Verify no conflicts were found
            for (var file : files) {
                assertTrue(file.mappings.isEmpty(), "No conflicts should be present");
            }
        }
    }

    /**
     * Test for when mapping conflicts are present.
     * This test also verifies that mappings are generated correctly.
     */
    @Test
    void testMappingConflictsAndGeneration() throws IOException {
        // Copy different jars into the temp directory
        File differentJarACopy = tempDir.resolve("conflict-a.jar").toFile();
        File differentJarBCopy = tempDir.resolve("conflict-b.jar").toFile();
        FileUtils.copyFile(differentJarA, differentJarACopy);
        FileUtils.copyFile(differentJarB, differentJarBCopy);

        try(JarFile differentJarA = new JarFile(differentJarACopy);
            JarFile differentJarB = new JarFile(differentJarBCopy)) {
            // Run the relocation process
            List<RelocationConfig> files = new ArrayList<>(List.of(
                    new RelocationConfig(differentJarA, "diffA"),
                    new RelocationConfig(differentJarB, "diffB")
            ));
            Relocator.generateMappings(files);

            if (debug) {
                for (var file : files) {
                    // Print the mappings
                    file.mappings.forEach((originalPath, relocatedPath) -> {
                        "Original: ${originalPath}, Relocated: ${relocatedPath}".println();
                    });
                }
            }

            // Verify conflicts were found, the first JAR keeps its original names so only the others get mappings
            assertTrue(files.getFirst().mappings.isEmpty(), "The first JAR should keep its original names");
            assertFalse(files.getLast().mappings.isEmpty(), "Conflicts should be present");

            TinyClassWriter.write(files, tempDir.toFile());

            for (var file : files) {
                if (debug) {
                    "Tiny mappings file content:".println();
                    FileUtils.readFileToString(file.tinyFile, "UTF-8").println();
                }

                // Verify the tiny mappings file was created
                assertTrue(file.tinyFile.exists(), "Tiny mappings file should be created");
                assertTrue(FileUtils.readFileToString(file.tinyFile, "UTF-8").contains("tiny\t2\t0\toriginal\trelocated"), "Tiny mappings file should contain header");
            }

            // Verify the content of the tiny mappings file of the relocated JAR
            String content = FileUtils.readFileToString(files.getLast().tinyFile, "UTF-8");
            assertTrue(content.contains("c\t"), "Tiny mappings file should contain class mappings");
            assertTrue(content.contains(files.getLast().conflictPrefix), "Tiny mappings file should contain conflict prefix");
        }
    }

    @Test
    void testRelocation() throws IOException {
        // Copy different jars into the temp directory
        File differentJarACopy = tempDir.resolve("conflict-a.jar").toFile();
        File differentJarBCopy = tempDir.resolve("conflict-b.jar").toFile();
        FileUtils.copyFile(differentJarA, differentJarACopy);
        FileUtils.copyFile(differentJarB, differentJarBCopy);

        try(JarFile differentJarA = new JarFile(differentJarACopy);
            JarFile differentJarB = new JarFile(differentJarBCopy)) {
            // Run the relocation process
            List<RelocationConfig> files = new ArrayList<>(List.of(
                    new RelocationConfig(differentJarA, "diffA"),
                    new RelocationConfig(differentJarB, "diffB")
            ));
            Relocator.relocate(files);
            var differentJarA1 = new JarFile(differentJarACopy);
            var differentJarB1 = new JarFile(differentJarBCopy);

            // List all entries in the JAR files
            if (debug) {
                "\nEntries in differentJarA:".println();
                differentJarA1.entries().asIterator().forEachRemaining(System.out::println);
                "\nEntries in differentJarB:".println();
                differentJarB1.entries().asIterator().forEachRemaining(System.out::println);
            }

            // The first JAR keeps its original names, the second must have relocated entries and they must be different

            AtomicBoolean foundPrefixA = new AtomicBoolean(false);
            AtomicBoolean foundPrefixB = new AtomicBoolean(false);
            Set<String> entriesA = getJarEntries(differentJarA1);
            Set<String> entriesB = getJarEntries(differentJarB1);

            entriesA.forEach(entry -> {
                if (entry.contains("diffA")) {
                    foundPrefixA.set(true);
                }
            });
            entriesB.forEach(entry -> {
                if (entry.contains("diffB")) {
                    foundPrefixB.set(true);
                }
            });


            assertFalse(foundPrefixA.get(), "JAR A keeps its original names so nothing should have the prefix 'diffA'");
            assertTrue(foundPrefixB.get(), "JAR B should contain at least one entry with prefix 'diffB'");

            assertNotEquals(entriesA, entriesB, "JAR entries should be different after relocation");

            differentJarA1.close();
            differentJarB1.close();
        }
    }

    @Test
    void testMerge() throws IOException {
        // Copy merge jars into the temp directory
        File mergeJarACopy = tempDir.resolve("merge-a.jar").toFile();
        File mergeJarBCopy = tempDir.resolve("merge-b.jar").toFile();
        FileUtils.copyFile(mergeJarA, mergeJarACopy);
        FileUtils.copyFile(mergeJarB, mergeJarBCopy);

        File mergedJar = tempDir.resolve("merged.jar").toFile();

        // Relocate & merge
        try(JarFile mergeJarA = new JarFile(mergeJarACopy);
            JarFile mergeJarB = new JarFile(mergeJarBCopy)) {
            List<RelocationConfig> files = new ArrayList<>(List.of(
                    new RelocationConfig(mergeJarA, "diffA"),
                    new RelocationConfig(mergeJarB, "diffB")
            ));
            Relocator.relocate(files);
            try (var baos = JAR.combineJars(List.of(mergeJarACopy, mergeJarBCopy))) {
                try (var fos = new FileOutputStream(mergedJar)) {
                    baos.writeTo(fos);
                }
            }
        }

        // Verify the merged JAR contains all entries from both JARs
        try (JarFile mergedJarFile = new JarFile(mergedJar);
             JarFile mergeJarA = new JarFile(mergeJarACopy);
             JarFile mergeJarB = new JarFile(mergeJarBCopy)) {
            Set<String> mergedEntries = new HashSet<>();
            mergedJarFile.entries().asIterator().forEachRemaining(entry -> {
                String name = entry.getName();
                mergedEntries.add(name);
            });

            if (debug) {
                "Entries in merged JAR:".println();
                mergedEntries.forEach(System.out::println);
            }

            assertTrue(mergedEntries.containsAll(getJarEntries(mergeJarA)), "Merged JAR should contain all entries from JAR A");
            assertTrue(mergedEntries.containsAll(getJarEntries(mergeJarB)), "Merged JAR should contain all entries from JAR B");
        }
    }

    @Test
    void testMergeCLI() throws IOException {
        // Copy merge jars into the temp directory
        File mergeJarACopy = tempDir.resolve("merge-a.jar").toFile();
        File mergeJarBCopy = tempDir.resolve("merge-b.jar").toFile();
        FileUtils.copyFile(mergeJarA, mergeJarACopy);
        FileUtils.copyFile(mergeJarB, mergeJarBCopy);

        // Merge using CLI
        File mergedJar = tempDir.resolve("merged.jar").toFile();
        Forgix.main(new String[] {
                "mergeJars",
                "--output", mergedJar.getAbsolutePath(),
                "--loaderA", mergeJarACopy.getAbsolutePath(),
                "--loaderB", mergeJarBCopy.getAbsolutePath()
        });

        // Verify the merged JAR contains all entries from both JARs
        try (JarFile mergedJarFile = new JarFile(mergedJar);
             JarFile mergeJarA = new JarFile(mergeJarACopy);
             JarFile mergeJarB = new JarFile(mergeJarBCopy)) {
            Set<String> mergedEntries = new HashSet<>();
            mergedJarFile.entries().asIterator().forEachRemaining(entry -> {
                String name = entry.getName();
                mergedEntries.add(name);
            });

            if (debug) {
                "Entries in merged JAR:".println();
                mergedEntries.forEach(System.out::println);
            }

            assertTrue(mergedEntries.containsAll(getJarEntries(mergeJarA)), "Merged JAR should contain all entries from JAR A");
            assertTrue(mergedEntries.containsAll(getJarEntries(mergeJarB)), "Merged JAR should contain all entries from JAR B");
            assertTrue(mergedEntries.stream().noneMatch(entry -> (entry.startsWith("assets/") || entry.startsWith("data/")) && (entry.contains("_loaderA") || entry.contains("_loaderB"))), "Files the game looks up by path must never be renamed");
            assertTrue(mergedEntries.contains("fabric.mod.json"), "Loader descriptors must keep their place");
        }
    }

    @Test
    void testMultiversion() throws IOException {
        // Copy version jars into the temp directory
        File version_1_16_5_copy = tempDir.resolve("1.16.5.jar").toFile();
        File version_1_21_5_copy = tempDir.resolve("1.21.5.jar").toFile();
        FileUtils.copyFile(version_1_16_5, version_1_16_5_copy);
        FileUtils.copyFile(version_1_21_5, version_1_21_5_copy);

        try (var baos = Multiversion.mergeVersions(List.of(version_1_16_5_copy, version_1_21_5_copy))) {
            try (var fos = new FileOutputStream(tempDir.resolve("multiversion.jar").toFile())) {
                baos.writeTo(fos);
            }
        }
    }

    @Test
    void testMultiversionCLI() throws IOException {
        // Copy version jars into the temp directory
        File version_1_16_5_copy = tempDir.resolve("1.16.5.jar").toFile();
        File version_1_21_5_copy = tempDir.resolve("1.21.5.jar").toFile();
        FileUtils.copyFile(version_1_16_5, version_1_16_5_copy);
        FileUtils.copyFile(version_1_21_5, version_1_21_5_copy);

        // Merge using CLI
        File mergedJar = tempDir.resolve("multiversion.jar").toFile();
        Forgix.main(new String[] {
                "mergeVersions",
                "--output", mergedJar.getAbsolutePath(),
                version_1_16_5_copy.getAbsolutePath(),
                version_1_21_5_copy.getAbsolutePath()
        });

        // List entries in the merged jar for debugging
        if (debug) {
            try (JarFile mergedJarFile = new JarFile(mergedJar)) {
                "Entries in merged JAR:".println();
                mergedJarFile.entries().asIterator().forEachRemaining(System.out::println);
            }
        }
    }

    /**
     * Test for files that are looked up by convention (assets, data, and the like).
     * JSON that only differs in formatting must not conflict, unreferenced conflicts must keep their
     * path (merged if possible, first JAR's copy otherwise) and the input jars must never be modified.
     */
    @Test
    void testConventionFilesKeepTheirPath() throws IOException {
        File jarA = buildJar(tempDir.resolve("a.jar"), new LinkedHashMap<>() {{
            put("data/example/recipe.json", "{\n  \"type\": \"crafting\",\n  \"count\": 1\n}\n".getBytes());
            put("assets/example/lang/en_us.json", "{\"key\": \"from a\"}".getBytes());
            put("assets/example/sounds.json", "{\"purr\": {}}".getBytes());
        }});
        File jarB = buildJar(tempDir.resolve("b.jar"), new LinkedHashMap<>() {{
            put("data/example/recipe.json", "{\"count\":1,\"type\":\"crafting\"}".getBytes());
            put("assets/example/lang/en_us.json", "{\"key\": \"from b\"}".getBytes());
            put("assets/example/sounds.json", "{\"meow\": {}}".getBytes());
        }});
        byte[] originalA = Files.readAllBytes(jarA.toPath());
        byte[] originalB = Files.readAllBytes(jarB.toPath());

        File mergedJar = tempDir.resolve("merged.jar").toFile();
        Forgix.mergeLoaders(Map.of(jarA, "loaderA", jarB, "loaderB"), mergedJar, silence:true);

        try (JarFile merged = new JarFile(mergedJar)) {
            Set<String> entries = getJarEntries(merged);
            assertTrue(entries.stream().noneMatch(entry -> entry.contains("_loaderA") || entry.contains("_loaderB")), "Files the game looks up by path must never be renamed");
            assertEquals("{\n  \"type\": \"crafting\",\n  \"count\": 1\n}\n", entryContent(merged, "data/example/recipe.json"), "Formatting-only differences are not conflicts and the first copy stays as-is");
            assertEquals("{\"key\": \"from a\"}", entryContent(merged, "assets/example/lang/en_us.json"), "Contradicting unreferenced files keep the first JAR's copy");
            assertEquals("{\"purr\":{},\"meow\":{}}", entryContent(merged, "assets/example/sounds.json"), "Unreferenced files that don't contradict each other get merged");
        }

        assertArrayEquals(originalA, Files.readAllBytes(jarA.toPath()), "Merging must never modify the input jars");
        assertArrayEquals(originalB, Files.readAllBytes(jarB.toPath()), "Merging must never modify the input jars");
    }

    /**
     * Test for the full mixin cascade: the conflicting mixin class gets relocated, the config that
     * lists it and the refmap follow, everything referencing them points at the new names
     * and the first JAR stays completely untouched.
     */
    @Test
    void testMixinRelocationCascade() throws IOException {
        String mixinConfig = "{\"package\": \"com.example.mixin\", \"mixins\": [\"ExampleMixin\"], \"refmap\": \"example.refmap.json\"}";
        File fabricJar = buildJar(tempDir.resolve("fabric.jar"), new LinkedHashMap<>() {{
            put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n".getBytes());
            put("com/example/mixin/ExampleMixin.class", classBytes("com/example/mixin/ExampleMixin", "fabric"));
            put("example.mixins.json", mixinConfig.getBytes());
            put("example.refmap.json", "{\"mappings\": {\"com/example/mixin/ExampleMixin\": {\"target\": \"intermediary\"}}}".getBytes());
            put("fabric.mod.json", "{\"id\": \"example\", \"mixins\": [\"example.mixins.json\"]}".getBytes());
        }});
        File neoforgeJar = buildJar(tempDir.resolve("neoforge.jar"), new LinkedHashMap<>() {{
            put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMixinConfigs: example.mixins.json\n\n".getBytes());
            put("com/example/mixin/ExampleMixin.class", classBytes("com/example/mixin/ExampleMixin", "neoforge"));
            put("example.mixins.json", mixinConfig.getBytes());
            put("example.refmap.json", "{\"mappings\": {\"com/example/mixin/ExampleMixin\": {\"target\": \"mojmap\"}}}".getBytes());
        }});

        File mergedJar = tempDir.resolve("merged.jar").toFile();
        Forgix.mergeLoaders(Map.of(fabricJar, "fabric", neoforgeJar, "neoforge"), mergedJar, silence:true);

        try (JarFile merged = new JarFile(mergedJar)) {
            Set<String> entries = getJarEntries(merged);
            if (debug) {
                "Entries in merged JAR:".println();
                entries.forEach(System.out::println);
            }
            assertTrue(entries.contains("com/example/mixin/ExampleMixin.class"), "The first JAR keeps its class name");
            assertTrue(entries.contains("com/example/mixin/ExampleMixin_neoforge.class"), "The conflicting class gets relocated");

            assertEquals(mixinConfig, entryContent(merged, "example.mixins.json"), "The first JAR's mixin config stays byte-identical");
            String relocatedConfig = entryContent(merged, "example.mixins_neoforge.json");
            assertTrue(relocatedConfig.contains("\"ExampleMixin_neoforge\""), "The relocated config lists the relocated mixin class");
            assertTrue(relocatedConfig.contains("\"example.refmap_neoforge.json\""), "The relocated config points at the relocated refmap");

            assertTrue(entryContent(merged, "example.refmap.json").contains("\"com/example/mixin/ExampleMixin\""), "The first JAR's refmap stays as-is");
            assertTrue(entryContent(merged, "example.refmap_neoforge.json").contains("\"com/example/mixin/ExampleMixin_neoforge\""), "The relocated refmap keys point at the relocated class");

            assertEquals("example.mixins_neoforge.json", merged.getManifest().getMainAttributes().getValue("MixinConfigs"), "The manifest points at the relocated config");
        }
    }

    /**
     * Test for jar-in-jar deduplication: the same dependency nested by two loaders at different paths
     * (one with Fabric's injected fabric.mod.json) collapses into the bigger copy and the jarjar metadata follows it.
     */
    @Test
    void testNestedJarDeduplication() throws IOException {
        byte[] plainLibrary = zipBytes(new LinkedHashMap<>() {{
            put("com/library/Library.class", classBytes("com/library/Library", "shared"));
            put("library.txt", "hello".getBytes());
        }});
        byte[] injectedLibrary = zipBytes(new LinkedHashMap<>() {{
            put("com/library/Library.class", classBytes("com/library/Library", "shared"));
            put("library.txt", "hello".getBytes());
            put("fabric.mod.json", "{\"id\": \"library\"}".getBytes());
        }});

        File fabricJar = buildJar(tempDir.resolve("fabric.jar"), new LinkedHashMap<>() {{
            put("fabric.mod.json", "{\"id\": \"example\", \"jars\": [{\"file\": \"META-INF/jars/library.jar\"}]}".getBytes());
            put("META-INF/jars/library.jar", injectedLibrary);
        }});
        File neoforgeJar = buildJar(tempDir.resolve("neoforge.jar"), new LinkedHashMap<>() {{
            put("META-INF/jarjar/metadata.json", jarJarMetadata("library").getBytes());
            put("META-INF/jarjar/library.jar", plainLibrary);
        }});

        File mergedJar = tempDir.resolve("merged.jar").toFile();
        Forgix.mergeLoaders(Map.of(fabricJar, "fabric", neoforgeJar, "neoforge"), mergedJar, silence:true);

        try (JarFile merged = new JarFile(mergedJar)) {
            Set<String> entries = getJarEntries(merged);
            assertTrue(entries.contains("META-INF/jars/library.jar"), "The bigger copy of the dependency is kept");
            assertFalse(entries.contains("META-INF/jarjar/library.jar"), "The duplicated copy of the dependency is dropped");
            assertTrue(entryContent(merged, "META-INF/jarjar/metadata.json").contains("META-INF/jars/library.jar"), "The jarjar metadata points at the kept copy");
        }
    }

    /**
     * Test for when two loaders both use jarjar metadata, the metadata files must merge into the union of their entries.
     */
    @Test
    void testJarJarMetadataMerge() throws IOException {
        File forgeJar = buildJar(tempDir.resolve("forge.jar"), new LinkedHashMap<>() {{
            put("META-INF/jarjar/metadata.json", jarJarMetadata("libraryA").getBytes());
            put("META-INF/jarjar/libraryA.jar", zipBytes(Map.of("a.txt", "a".getBytes())));
        }});
        File neoforgeJar = buildJar(tempDir.resolve("neoforge.jar"), new LinkedHashMap<>() {{
            put("META-INF/jarjar/metadata.json", jarJarMetadata("libraryB").getBytes());
            put("META-INF/jarjar/libraryB.jar", zipBytes(Map.of("b.txt", "b".getBytes())));
        }});

        File mergedJar = tempDir.resolve("merged.jar").toFile();
        Forgix.mergeLoaders(Map.of(forgeJar, "forge", neoforgeJar, "neoforge"), mergedJar, silence:true);

        try (JarFile merged = new JarFile(mergedJar)) {
            String metadata = entryContent(merged, "META-INF/jarjar/metadata.json");
            assertTrue(metadata.contains("META-INF/jarjar/libraryA.jar") && metadata.contains("META-INF/jarjar/libraryB.jar"), "The metadata files merge into the union of their entries");
            assertTrue(getJarEntries(merged).containsAll(Set.of("META-INF/jarjar/libraryA.jar", "META-INF/jarjar/libraryB.jar")), "Both dependencies are kept");
        }
    }

    // Helper methods

    /**
     * Get all entry names from a JAR file.
     */
    private Set<String> getJarEntries(JarFile jarFile) {
        Set<String> entries = new HashSet<>();
        Enumeration<JarEntry> jarEntries = jarFile.entries();

        while (jarEntries.hasMoreElements()) {
            JarEntry entry = jarEntries.nextElement();
            entries.add(entry.getName());
        }

        return entries;
    }

    /**
     * Build a JAR file at the given path with the given entries.
     */
    private File buildJar(Path path, Map<String, byte[]> entries) throws IOException {
        Files.write(path, zipBytes(entries));
        return path.toFile();
    }

    /**
     * Build a zip with the given entries.
     */
    private byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Build a minimal real class so TinyRemapper can parse it, the field name makes the content differ.
     */
    private byte[] classBytes(String internalName, String fieldName) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, "I", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * Read an entry of the JAR as a string.
     */
    private String entryContent(JarFile jarFile, String name) throws IOException {
        var entry = jarFile.getJarEntry(name);
        assertNotNull(entry, "${name} should exist in the jar");
        return JAR.getResource(jarFile, entry);
    }

    private String jarJarMetadata(String artifact) {
        return "{\"jars\": [{\"identifier\": {\"group\": \"com.example\", \"artifact\": \"${artifact}\"}, \"version\": {\"range\": \"[1,)\", \"artifactVersion\": \"1.0\"}, \"path\": \"META-INF/jarjar/${artifact}.jar\"}]}";
    }
}
