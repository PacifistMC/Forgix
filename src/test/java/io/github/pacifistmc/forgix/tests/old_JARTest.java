package io.github.pacifistmc.forgix.tests;

import org.junit.jupiter.api.Disabled;

/**
 * Tests for the JAR utility class.
 */
@Disabled
class old_JARTest {

//    private File exampleJarFile;
//
//    @TempDir
//    Path tempDir;
//
//    @BeforeEach
//    void setUp() throws URISyntaxException {
//        // Get the example.jar from the resources
//        URL resource = getClass().getClassLoader().getResource("jars/example.jar");
//        assertNotNull(resource, "Example JAR file not found in resources");
//        exampleJarFile = new File(resource.toURI());
//        assertTrue(exampleJarFile.exists(), "Example JAR file does not exist");
//    }
//
//    @AfterEach
//    void tearDown() {
//        // Clean up temporary files created during tests
//        tempDir.toFile().delete();
//    }
//
//    /**
//     * Test unpacking a JAR file.
//     */
//    @Test
//    void testUnpack() throws IOException {
//        // Create a temporary directory for unpacking
//        File outputDir = tempDir.resolve("unpack-output").toFile();
//        outputDir.mkdirs();
//
//        // Unpack the JAR
//        JarFile jarFile = new JarFile(exampleJarFile);
//        File result = JAR.unpack(jarFile, outputDir);
//
//        // Verify the result
//        assertEquals(outputDir, result, "The returned directory should be the output directory");
//
//        // Verify the contents were extracted
//        Set<String> expectedEntries = getJarEntries(jarFile);
//        for (String entryName : expectedEntries) {
//            if (!entryName.endsWith("/")) { // Skip directories
//                File extractedFile = new File(outputDir, entryName);
//                assertTrue(extractedFile.exists(), "File ${entryName} should have been extracted");
//            }
//        }
//
//        jarFile.close();
//    }
//
//    /**
//     * Test packing a directory into a JAR file.
//     */
//    @Test
//    void testPack() throws IOException {
//        // First unpack the example JAR to get content to repack
//        File unpackDir = tempDir.resolve("pack-source").toFile();
//        unpackDir.mkdirs();
//
//        JarFile originalJar = new JarFile(exampleJarFile);
//        JAR.unpack(originalJar, unpackDir);
//
//        // Now pack the directory back into a new JAR
//        File newJarFile = tempDir.resolve("repacked.jar").toFile();
//        JarFile packedJar = JAR.pack(unpackDir, newJarFile);
//
//        // Verify the new JAR was created
//        assertTrue(newJarFile.exists(), "New JAR file should have been created");
//        assertTrue(newJarFile.length() > 0, "New JAR file should not be empty");
//
//        // Verify the contents match the original
//        Set<String> originalEntries = getJarEntries(originalJar);
//        Set<String> newEntries = getJarEntries(packedJar);
//
//        // Compare entry names (ignoring directories and META-INF differences)
//        Set<String> filteredOriginal = filterEntries(originalEntries);
//        Set<String> filteredNew = filterEntries(newEntries);
//
//        // Check if all original entries are in the new JAR
//        for (String entry : filteredOriginal) {
//            assertTrue(filteredNew.contains(entry),
//                    "Entry ${entry} from original JAR should be in the new JAR");
//        }
//
//        // Check if all new entries are in the original JAR
//        for (String entry : filteredNew) {
//            assertTrue(filteredOriginal.contains(entry),
//                    "Entry ${entry} from new JAR should be in the original JAR");
//        }
//
//        assertEquals(filteredOriginal.size(), filteredNew.size(), "Number of files in original and new JAR should match");
//
//        originalJar.close();
//        packedJar.close();
//    }
//
//    /**
//     * Test the full cycle: unpack and then pack back.
//     */
//    @Test
//    void testUnpackAndPackCycle() throws IOException {
//        // Unpack the example JAR
//        File unpackDir = tempDir.resolve("cycle-unpack").toFile();
//        unpackDir.mkdirs();
//
//        JarFile originalJar = new JarFile(exampleJarFile);
//        JAR.unpack(originalJar, unpackDir);
//
//        // Pack it back
//        File repackedJar = tempDir.resolve("cycle-repacked.jar").toFile();
//        JarFile newJar = JAR.pack(unpackDir, repackedJar);
//
//        // Unpack the new JAR
//        File secondUnpackDir = tempDir.resolve("cycle-second-unpack").toFile();
//        secondUnpackDir.mkdirs();
//        JAR.unpack(newJar, secondUnpackDir);
//
//        // Compare the contents of the two unpacked directories
//        assertDirectoriesEqual(unpackDir, secondUnpackDir);
//
//        // Compare the contents of the two JAR files
//        Set<String> entries = getJarEntries(originalJar);
//        for (String entryName : entries) {
//            JarEntry firstEntry = originalJar.getJarEntry(entryName);
//            JarEntry secondEntry = newJar.getJarEntry(entryName);
//            assertFalse(firstEntry != null && secondEntry == null, "Entry ${entryName} should exist in both JARs");
//
//            if (firstEntry != null && !firstEntry.isDirectory()) {
//                byte[] firstHash = JAR.computeHash(originalJar, firstEntry);
//                byte[] secondHash = JAR.computeHash(newJar, secondEntry);
//
//                assertArrayEquals(firstHash, secondHash, "Content mismatch for entry: ${entryName}");
//            }
//        }
//
//        originalJar.close();
//        newJar.close();
//    }
//
//    /**
//     * Test performance of unpacking with multiple iterations for better measurement.
//     */
//    @Test
//    void testUnpackPerformance() throws IOException {
//        // Prepare multiple output directories for testing
//        File[] outputDirs = new File[5];
//        for (int i = 0; i < outputDirs.length; i++) {
//            outputDirs[i] = tempDir.resolve("perf-unpack-" + i).toFile();
//            outputDirs[i].mkdirs();
//        }
//
//        // Warm-up run
//        JarFile jarFile = new JarFile(exampleJarFile);
//        JAR.unpack(jarFile, outputDirs[0]);
//        jarFile.close();
//
//        // Actual performance test with multiple iterations
//        long[] durations = new long[outputDirs.length - 1];
//        for (int i = 0; i < durations.length; i++) {
//            jarFile = new JarFile(exampleJarFile);
//
//            long startTime = System.nanoTime();
//            JAR.unpack(jarFile, outputDirs[i + 1]);
//            long endTime = System.nanoTime();
//
//            durations[i] = (endTime - startTime) / 1_000_000;
//            jarFile.close();
//        }
//
//        // Calculate and print statistics
//        long totalDuration = 0;
//        long minDuration = Long.MAX_VALUE;
//        long maxDuration = 0;
//
//        for (long duration : durations) {
//            totalDuration += duration;
//            minDuration = Math.min(minDuration, duration);
//            maxDuration = Math.max(maxDuration, duration);
//        }
//
//        double avgDuration = (double) totalDuration / durations.length;
//
//        "Unpack Performance Statistics:".println();
//        "  Average duration: ${String.format(\"%.2f\", avgDuration)}ms".println();
//        "  Min duration: ${minDuration}ms".println();
//        "  Max duration: ${maxDuration}ms".println();
//
//        // Verify performance is reasonable
//        assertTrue(avgDuration > 0, "Average duration should be positive");
//    }
//
//    /**
//     * Test performance of packing with multiple iterations for better measurement.
//     */
//    @Test
//    void testPackPerformance() throws IOException {
//        // First unpack to get content
//        File unpackDir = tempDir.resolve("perf-pack-source").toFile();
//        unpackDir.mkdirs();
//
//        JarFile jarFile = new JarFile(exampleJarFile);
//        JAR.unpack(jarFile, unpackDir);
//        jarFile.close();
//
//        // Prepare multiple output files for testing
//        File[] outputFiles = new File[5];
//        for (int i = 0; i < outputFiles.length; i++) {
//            outputFiles[i] = tempDir.resolve("perf-repacked-" + i + ".jar").toFile();
//        }
//
//        // Warm-up run
//        JarFile packedJar = JAR.pack(unpackDir, outputFiles[0]);
//        packedJar.close();
//
//        // Actual performance test with multiple iterations
//        long[] durations = new long[outputFiles.length - 1];
//        for (int i = 0; i < durations.length; i++) {
//            long startTime = System.nanoTime();
//            packedJar = JAR.pack(unpackDir, outputFiles[i + 1]);
//            long endTime = System.nanoTime();
//
//            durations[i] = (endTime - startTime) / 1_000_000;
//            packedJar.close();
//        }
//
//        // Calculate and print statistics
//        long totalDuration = 0;
//        long minDuration = Long.MAX_VALUE;
//        long maxDuration = 0;
//
//        for (long duration : durations) {
//            totalDuration += duration;
//            minDuration = Math.min(minDuration, duration);
//            maxDuration = Math.max(maxDuration, duration);
//        }
//
//        double avgDuration = (double) totalDuration / durations.length;
//
//        "Pack Performance Statistics:".println();
//        "  Average duration: ${String.format(\"%.2f\", avgDuration)}ms".println();
//        "  Min duration: ${minDuration}ms".println();
//        "  Max duration: ${maxDuration}ms".println();
//
//        // Verify performance is reasonable
//        assertTrue(avgDuration > 0, "Average duration should be positive");
//    }
//
//    // Helper methods
//
//    /**
//     * Get all entry names from a JAR file.
//     */
//    private Set<String> getJarEntries(JarFile jarFile) {
//        Set<String> entries = new HashSet<>();
//        Enumeration<JarEntry> jarEntries = jarFile.entries();
//
//        while (jarEntries.hasMoreElements()) {
//            JarEntry entry = jarEntries.nextElement();
//            entries.add(entry.getName());
//        }
//
//        return entries;
//    }
//
//    /**
//     * Filter JAR entries to exclude directories and certain META-INF files.
//     * Also normalizes path separators for cross-platform compatibility.
//     */
//    private Set<String> filterEntries(Set<String> entries) {
//        Set<String> filtered = new HashSet<>();
//
//        for (String entry : entries) {
//            // Normalize path separators (replace backslashes with forward slashes)
//            String normalizedEntry = entry.replace('\\', '/');
//
//            // Skip directories and certain META-INF files that might differ
//            if (!normalizedEntry.endsWith("/") &&
//                !normalizedEntry.equals("META-INF/MANIFEST.MF") &&
//                !normalizedEntry.startsWith("META-INF/maven/") &&
//                !normalizedEntry.startsWith("META-INF/services/")) {
//                filtered.add(normalizedEntry);
//            }
//        }
//
//        return filtered;
//    }
//
//    /**
//     * Assert that two directories have the same content.
//     * This method is more robust to handle different path separators and file ordering.
//     */
//    private void assertDirectoriesEqual(File dir1, File dir2) throws IOException {
//        Path path1 = dir1.toPath();
//        Path path2 = dir2.toPath();
//
//        // Get all files from first directory
//        Set<String> files1 = new HashSet<>();
//        Files.walk(path1)
//            .filter(Files::isRegularFile)
//            .forEach(file -> {
//                String relativePath = path1.relativize(file).toString().replace('\\', '/');
//                files1.add(relativePath);
//            });
//
//        // Get all files from second directory
//        Set<String> files2 = new HashSet<>();
//        Files.walk(path2)
//            .filter(Files::isRegularFile)
//            .forEach(file -> {
//                String relativePath = path2.relativize(file).toString().replace('\\', '/');
//                files2.add(relativePath);
//            });
//
//        // Compare file sets
//        assertEquals(files1.size(), files2.size(), "Both directories should have the same number of files");
//
//        for (String relativePath : files1) {
//            assertTrue(files2.contains(relativePath),
//                    "File ${relativePath} should exist in both directories");
//
//            try {
//                // Convert relative path to actual paths in both directories
//                Path file1 = path1.resolve(relativePath.replace('/', File.separatorChar));
//                Path file2 = path2.resolve(relativePath.replace('/', File.separatorChar));
//
//                byte[] content1 = Files.readAllBytes(file1);
//                byte[] content2 = Files.readAllBytes(file2);
//
//                assertArrayEquals(content1, content2,
//                        "Content of ${relativePath} should be the same in both directories");
//            } catch (IOException e) {
//                fail("Failed to compare files: ${e.getMessage()}");
//            }
//        }
//    }
}
