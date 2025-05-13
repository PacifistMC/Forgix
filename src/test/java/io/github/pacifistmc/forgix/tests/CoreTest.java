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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

            for (var file : files) {
                if (debug) {
                    // Print the mappings
                    file.mappings.forEach((originalPath, relocatedPath) -> {
                        "Original: ${originalPath}, Relocated: ${relocatedPath}".println();
                    });
                }

                // Verify conflicts were found
                assertFalse(file.mappings.isEmpty(), "Conflicts should be present");
            }

            TinyClassWriter.write(files, tempDir.toFile());

            for (var file : files) {
                if (debug) {
                    "Tiny mappings file content:".println();
                    FileUtils.readFileToString(file.tinyFile, "UTF-8").println();
                }

                // Verify the tiny mappings file was created
                assertTrue(file.tinyFile.exists(), "Tiny mappings file should be created");

                // Verify the content of the tiny mappings file
                String content = FileUtils.readFileToString(file.tinyFile, "UTF-8");
                assertTrue(content.contains("tiny\t2\t0\toriginal\trelocated"), "Tiny mappings file should contain header");
                assertTrue(content.contains("c\t"), "Tiny mappings file should contain class mappings");
                assertTrue(content.contains(file.conflictPrefix), "Tiny mappings file should contain conflict prefix");
            }
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

            // There must at least be one entry with the conflict prefix and the entries must be different

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


            assertTrue(foundPrefixA.get(), "JAR A should contain at least one entry with prefix 'diffA'");
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
}
