package io.github.pacifistmc.forgix.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * My JAR file utility for processing JAR files. There are many like it but this one is mine.
 */
public class JAR {
    // Constants
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer
    public static final Set<PosixFilePermission> perms = new HashSet<>() {
        {
            add(PosixFilePermission.OTHERS_EXECUTE);
            add(PosixFilePermission.OTHERS_WRITE);
            add(PosixFilePermission.OTHERS_READ);
            add(PosixFilePermission.OWNER_EXECUTE);
            add(PosixFilePermission.OWNER_WRITE);
            add(PosixFilePermission.OWNER_READ);
            add(PosixFilePermission.GROUP_EXECUTE);
            add(PosixFilePermission.GROUP_WRITE);
            add(PosixFilePermission.GROUP_READ);
        }
    };

    /**
     * Merges the manifests of multiple JAR files.
     * @param jars The collection of JAR files to read the manifests from
     * @return The merged manifest as a string, it will contain all the attributes from all the JAR files without duplicates
     */
    public static String mergeManifests(Collection<File> jars, Map<Object, Object> extraManifestAttributes = null) {
        Map<Object, Object> mergedAttributes = jars.stream()
                .flatMap(jar -> {
                    try (JarFile jarFile = new JarFile(jar)) {
                        Manifest manifest = jarFile.getManifest();
                        // If manifest doesn't exist or has no main attributes, skip it
                        if (manifest == null || manifest.getMainAttributes() == null) return Stream.empty();
                        return manifest.getMainAttributes().entrySet().stream();
                    } catch (IOException _) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,        // Key mapper: Attribute Name
                        Map.Entry::getValue,      // Value mapper: Attribute Value
                        (_, newValue) -> newValue, // Merge function for duplicate keys: keep the last value seen
                        LinkedHashMap::new        // Supplier for the Map: Use LinkedHashMap to maintain insertion order
                ));
        // Add extra attributes if provided
        if (extraManifestAttributes != null) mergedAttributes.putAll(extraManifestAttributes);
        // Return the merged attributes as a manifest string
        return mergedAttributes.entrySet().stream()
                .map(entry -> "${entry.getKey()}: ${entry.getValue()}")
                .collect(Collectors.joining("\n"));
    }

    /**
     * Merges all services
     * @param jars The collection of JAR files to read the services from
     * @return Map containing the name of the service file and the content
     */
    public static Map<String, String> mergeServices(Collection<File> jars) {
        return jars.stream()
                .flatMap(jar -> {
                    try (JarFile jarFile = new JarFile(jar)) {
                        return Collections.list(jarFile.entries()).stream()
                                .filter(entry -> entry.getName().startsWith("META-INF/services/"))
                                .map(entry -> Map.entry(entry.getName(), getResource(jarFile, entry)))
                                .toList().stream(); // This is needed to avoid closing the jarFile too early
                    } catch (IOException _) {
                        return Stream.empty();
                    }
                }).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            // Only add unique lines
                            return String.join("\n", new LinkedHashSet<>() {
                                {
                                    addAll(Arrays.asList(a.split("\n")));
                                    addAll(Arrays.asList(b.split("\n")));
                                }
                            });
                        }
                ));
    }

    /**
     * Combines the entries of multiple JAR files into a ByteArrayOutputStream.
     * @param jars The collection of JAR files to process
     * @return A ByteArrayOutputStream containing the combined JAR data
     */
    public static ByteArrayOutputStream combineJars(Collection<File> jars, boolean mergeMetaInf = true, Map<Object, Object> extraManifestAttributes = null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); // This holds the combined JAR data
        var fileSeen = new HashSet<>(); // Create a set to keep track of seen files
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (File jar : jars) {
                try (ZipFile zipFile = new ZipFile(jar)) { // Add all files from the JAR
                    // Only add unseen files and skip manifest & services if we're merging them
                    for (FileHeader header : zipFile.getFileHeaders()) {
                        var name = header.getFileName();
                        if (fileSeen.contains(name) || (mergeMetaInf && (name.equals("META-INF/MANIFEST.MF") || name.startsWith("META-INF/services/")))) continue;
                        zos.putNextEntry(new ZipEntry(name));
                        zos.write(zipFile.getInputStream(header).readAllBytes());
                        zos.closeEntry();
                        fileSeen.add(name);
                    }
                }
            }
            if (mergeMetaInf) {
                zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                zos.write(mergeManifests(jars, extraManifestAttributes).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                mergeServices(jars).forEach((name, content) -> {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                });
            }
            zos.finish();
        }
        return baos;
    }

    public static void setPerms(File file) {
        try {
            Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (UnsupportedOperationException _) { }
    }

    /**
     * Computes the hash of multiple JAR files.
     * @param jarFiles List of JAR file to compute the hash for
     * @return The hash of the JAR file
     */
    public static byte[] computeHash(Collection<File> jarFiles) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];

        for (File file : jarFiles) {
            try (InputStream is = Files.newInputStream(file.toPath())) {
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
        }

        return digest.digest();
    }

    /**
     * Computes the hash of a JAR entry.
     * @param jarFile The JAR file containing the entry
     * @param entry The entry to compute the hash for
     * @return The hash of the entry
     */
    public static byte[] computeHash(JarFile jarFile, JarEntry entry) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];

        try (var is = jarFile.getInputStream(entry)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return digest.digest();
    }

    /**
     * Get all resources in a JAR file.
     * These are not classes, these will be things like json, xml, txt, and other text files.
     */
    public static Set<JarEntry> getResources(JarFile jarFile) {
        return Collections.list(jarFile.entries()).stream()
                .filter(entry -> !entry.isDirectory() && !entry.getName().endsWith(".class"))
                .collect(Collectors.toSet());
    }

    /**
     * Get all classes in a JAR file.
     */
    public static Set<JarEntry> getClasses(JarFile jarFile) {
        return Collections.list(jarFile.entries()).stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                .collect(Collectors.toSet());
    }

    /**
     * Read a JAR resource as a string.
     */
    public static String getResource(JarFile jarFile, JarEntry resource) {
        try (InputStream is = jarFile.getInputStream(resource)) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * Write a JAR resource
     * @param jarFile The JAR file to write to.
     * @param content Map of resource and content.
     * @param onlyIfDifferent Only write the resource if the content is different. (Optional)
     * @return Whether any resources were written.
     */
    public static boolean writeResources(JarFile jarFile, Map<JarEntry, String> content, boolean onlyIfDifferent = true) {
        if (content.isEmpty()) return false; // Return if no content to write
        Map<InputStream, JarEntry> writers = new HashMap<>(); // Create a map of writers and entries
        content.forEach((entry, value) -> {
            if (onlyIfDifferent && getResource(jarFile, entry).equals(value)) return; // Skip if we're supposed to only write differentable content
            writers.put(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), entry);
        });
        jarFile.close(); // Close the JAR file so we can write to it
        try (ZipFile zipFile = new ZipFile(jarFile.getName())) {
            // Add all writers to the ZIP
            writers.forEach((is, entry) ->
                zipFile.addStream(is,
                        new ZipParameters() {{
                            setFileNameInZip(entry.getName());
                            setOverrideExistingFilesInZip(true);
                        }}
                ));
        }
        return !writers.isEmpty();
    }

    /**
     * Add files to a JAR file.
     * @param jarFile The JAR file to add to.
     * @param filesAndNamesMap The map of files to add.
     */
    public static void addFiles(File jarFile, Map<File, String> filesAndNamesMap) {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            filesAndNamesMap.forEach((file, path) -> zipFile.addFile(file, new ZipParameters() {{
                setFileNameInZip(path);
                setOverrideExistingFilesInZip(true);
            }}));
        }
    }

    /**
     * Remove files from a JAR file.
     * @param jarFile The JAR file to remove from.
     * @param fileNames The list of file names to remove.
     */
    public static void removeFiles(File jarFile, List<String> fileNames) {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            zipFile.removeFiles(fileNames);
        }
    }

    /**
     * Rename resources in a JAR file.
     * @param jarFilePath The path to the JAR file.
     * @param fileRenameMap The map of file names to rename.
     */
    public static void renameResources(String jarFilePath, Map<String, String> fileRenameMap) {
        if (fileRenameMap.isEmpty()) return;
        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            zipFile.renameFiles(fileRenameMap);
        }
    }

    /**
     * @return Is the JarFile closed?
     * @param jarFile The jar file to check
     */
    public static boolean isClosed(JarFile jarFile) {
        try {
            jarFile.entries();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // Ignore the stuff below
//
//    // Constants for performance tuning
//    private static final int LARGE_FILE_THRESHOLD = 50 * 1024 * 1024; // 50MB
//    private static final int SMALL_FILE_THRESHOLD = 4 * 1024; // 4KB
//    // Thread pool configuration
//    private static final ExecutorService EXECUTOR = Executors.newWorkStealingPool();
//
//    /**
//     * Ultra-fast JAR unpacking using parallel processing and memory-mapped I/O.
//     *
//     * @param jarFile The jar file to unpack
//     * @param outputDirectory The directory to unpack to
//     * @return The output directory as a File object
//     */
//    public static File unpack(JarFile jarFile, File outputDirectory) {
//        // Ensure output directory exists
//        if (!outputDirectory.exists()) outputDirectory.mkdirs();
//
//        // Get the physical file
//        File jarFileObj = new File(jarFile.getName());
//        long jarSize = jarFileObj.length();
//
//        // Choose the best extraction method based on JAR size
//        if (jarSize > LARGE_FILE_THRESHOLD) {
//            // For large JARs, use memory-mapped extraction with parallel processing
//            return unpackLargeJar(jarFileObj, outputDirectory);
//        }
//        // For smaller JARs, use direct extraction with parallel processing
//        return unpackSmallJar(jarFile, outputDirectory);
//    }
//
//    /**
//     * Ultra-fast JAR packing using parallel processing and optimized compression.
//     *
//     * @param jarDirectory The directory to pack
//     * @param outputFile The file to pack as
//     * @return A JarFile object representing the newly created JAR
//     */
//    public static JarFile pack(File jarDirectory, File outputFile) {
//        // Delete output file if it already exists
//        if (outputFile.exists()) outputFile.deleteQuietly();
//
//        // Create parent directories if they don't exist
//        File parentDir = outputFile.getParentFile();
//        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
//
//        // Get all files to add to the JAR
//        Path basePath = jarDirectory.toPath();
//        var files = collectFilesParallel(basePath);
//
//        if (files.totalSize > LARGE_FILE_THRESHOLD && files.list.size() > 100) {
//            // For many files or large total size, use parallel packing
//            packLargeDirectoryParallel(basePath, files.list, outputFile);
//        } else {
//            // For fewer files or smaller total size, use optimized sequential packing
//            packDirectorySequential(basePath, files.list, outputFile);
//        }
//
//        // Return a JarFile object for the newly created JAR
//        return new JarFile(outputFile);
//    }
//
//    /**
//     * Unpacks a large JAR file using memory-mapped I/O and parallel processing.
//     */
//    private static File unpackLargeJar(File jarFile, File outputDirectory) throws IOException {
//        try (ZipFile zipFile = new ZipFile(jarFile)) {
//            // Get all entries
//            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
//
//            // Process entries in parallel
//            CountDownLatch latch = new CountDownLatch(fileHeaders.size());
//
//            for (FileHeader header : fileHeaders) {
//                EXECUTOR.submit(() -> {
//                    try {
//                        // Skip directories - they'll be created as needed
//                        if (!header.isDirectory()) {
//                            // Extract with optimized settings
//                            String entryName = header.getFileName().replace('\\', '/');
//                            File outFile = new File(outputDirectory, entryName);
//
//                            // Ensure parent directories exist
//                            File parent = outFile.getParentFile();
//                            if (parent != null && !parent.exists()) {
//                                parent.mkdirs();
//                            }
//
//                            // Extract the file with optimized buffer size
//                            try (InputStream is = zipFile.getInputStream(header);
//                                 FileOutputStream fos = new FileOutputStream(outFile)) {
//
//                                // Use direct byte buffer for better performance
//                                ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
//                                byte[] byteArray = new byte[BUFFER_SIZE];
//
//                                int read;
//                                while ((read = is.read(byteArray)) != -1) {
//                                    buffer.clear();
//                                    buffer.put(byteArray, 0, read);
//                                    buffer.flip();
//
//                                    // Write to file
//                                    fos.getChannel().write(buffer);
//                                }
//                            }
//                        } else {
//                            // Create directory
//                            new File(outputDirectory, header.getFileName()).mkdirs();
//                        }
//                    } finally {
//                        latch.countDown();
//                    }
//                });
//            }
//
//            // Wait for all extractions to complete
//            try {
//                latch.await();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw new IOException("Extraction interrupted", e);
//            }
//        }
//
//        return outputDirectory;
//    }
//
//    /**
//     * Unpacks a small JAR file using parallel processing.
//     */
//    private static File unpackSmallJar(JarFile jarFile, File outputDirectory) throws IOException {
//        // Get all entries
//        List<JarEntry> entries = Collections.list(jarFile.entries());
//
//        // Process entries in parallel
//        CountDownLatch latch = new CountDownLatch(entries.size());
//
//        for (JarEntry entry : entries) {
//            EXECUTOR.submit(() -> {
//                try {
//                    String entryName = entry.getName();
//                    File outFile = new File(outputDirectory, entryName);
//
//                    if (entry.isDirectory()) {
//                        // Create directory
//                        outFile.mkdirs();
//                    } else {
//                        // Ensure parent directories exist
//                        File parent = outFile.getParentFile();
//                        if (parent != null && !parent.exists()) {
//                            parent.mkdirs();
//                        }
//
//                        // Extract file with optimized buffer
//                        try (InputStream is = jarFile.getInputStream(entry);
//                             FileOutputStream fos = new FileOutputStream(outFile)) {
//
//                            // Use direct copy for small files
//                            if (entry.getSize() < SMALL_FILE_THRESHOLD) {
//                                byte[] buffer = new byte[(int) entry.getSize()];
//                                IOUtils.readFully(is, buffer);
//                                fos.write(buffer);
//                            } else {
//                                // Use buffered copy for larger files
//                                byte[] buffer = new byte[BUFFER_SIZE];
//                                int len;
//                                while ((len = is.read(buffer)) > 0) {
//                                    fos.write(buffer, 0, len);
//                                }
//                            }
//                        }
//                    }
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // Wait for all extractions to complete
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new IOException("Extraction interrupted", e);
//        }
//
//        return outputDirectory;
//    }
//
//    /**
//     * Collects all files in a directory using parallel processing.
//     */
//    private static auto collectFilesParallel(Path basePath) throws IOException {
//        // Use a concurrent collection to safely collect files from multiple threads
//        ConcurrentLinkedQueue<Path> fileQueue = new ConcurrentLinkedQueue<>();
//        AtomicLong totalSize = new AtomicLong(0);
//
//        // Walk the file tree in parallel
//        try (Stream<Path> pathStream = Files.walk(basePath)) {
//            pathStream.parallel()
//                    .filter(Files::isRegularFile)
//                    .forEach(file -> {
//                        fileQueue.add(file);
//                        totalSize.addAndGet(file.toFile().length());
//                    });
//        }
//
//
//        // Return as a list and the total size
//        return (list:new ArrayList<>(fileQueue), totalSize:totalSize.get());
//    }
//
//    /**
//     * Packs a large directory using parallel processing and memory-mapped I/O.
//     */
//    private static void packLargeDirectoryParallel(Path basePath, List<Path> filesToAdd, File outputFile) throws IOException {
//        // Group files by size for optimal processing
//        Map<Boolean, List<Path>> partitionedFiles = filesToAdd.stream()
//                .collect(Collectors.partitioningBy(path -> {
//                    try {
//                        return Files.size(path) < SMALL_FILE_THRESHOLD;
//                    } catch (IOException e) {
//                        return true; // Assume small if can't determine size
//                    }
//                }));
//
//        List<Path> smallFiles = partitionedFiles.get(true);
//        List<Path> largeFiles = partitionedFiles.get(false);
//
//        // Pre-compute all entry data for small files to avoid contention
//        Map<Path, byte[]> smallFileContents = new ConcurrentHashMap<>();
//        Map<Path, String> relativePathMap = new ConcurrentHashMap<>();
//
//        // Load small files in parallel
//        CountDownLatch smallFilesLatch = new CountDownLatch(smallFiles.size());
//        for (Path path : smallFiles) {
//            EXECUTOR.submit(() -> {
//                try {
//                    // Normalize path
//                    String relativePath = basePath.relativize(path).toString().replace('\\', '/');
//                    relativePathMap.put(path, relativePath);
//
//                    // Load file content
//                    byte[] content = Files.readAllBytes(path);
//                    smallFileContents.put(path, content);
//                } finally {
//                    smallFilesLatch.countDown();
//                }
//            });
//        }
//
//        // Compute relative paths for large files
//        for (Path path : largeFiles) {
//            String relativePath = basePath.relativize(path).toString().replace('\\', '/');
//            relativePathMap.put(path, relativePath);
//        }
//
//        try {
//            // Wait for small files to be loaded
//            smallFilesLatch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new IOException("File loading interrupted", e);
//        }
//
//        // Create the JAR file with maximum compression speed
//        try (FileOutputStream fos = new FileOutputStream(outputFile);
//             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos, BUFFER_SIZE * 2))) {
//            // We can change the default strategy of ZipOutputStream but no need as the default is good enough
//
//            // First add all small files (already in memory)
//            for (Path path : smallFiles) {
//                String relativePath = relativePathMap.get(path);
//                byte[] content = smallFileContents.get(path);
//
//                ZipEntry entry = new ZipEntry(relativePath);
//                zos.putNextEntry(entry);
//                zos.write(content);
//                zos.closeEntry();
//            }
//
//            // Clear the map to free memory
//            smallFileContents.clear();
//
//            // Then add large files using memory-mapped I/O when possible
//            for (Path path : largeFiles) {
//                String relativePath = relativePathMap.get(path);
//                ZipEntry entry = new ZipEntry(relativePath);
//                zos.putNextEntry(entry);
//
//                // Use memory-mapped I/O for large files
//                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
//                    long size = channel.size();
//                    long position = 0;
//
//                    // Map file in chunks if necessary
//                    while (position < size) {
//                        long remainingSize = size - position;
//                        long chunkSize = Math.min(remainingSize, Integer.MAX_VALUE);
//
//                        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, chunkSize);
//
//                        // Write the buffer to the ZIP
//                        byte[] chunk = new byte[BUFFER_SIZE];
//                        while (buffer.hasRemaining()) {
//                            int bytesToRead = Math.min(buffer.remaining(), BUFFER_SIZE);
//                            buffer.get(chunk, 0, bytesToRead);
//                            zos.write(chunk, 0, bytesToRead);
//                        }
//
//                        position += chunkSize;
//                    }
//                }
//
//                zos.closeEntry();
//            }
//        }
//    }
//
//    /**
//     * Packs a directory using optimized sequential processing.
//     */
//    private static void packDirectorySequential(Path basePath, List<Path> filesToAdd, File outputFile) throws IOException {
//        try (FileOutputStream fos = new FileOutputStream(outputFile);
//             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos, BUFFER_SIZE * 2))) {
//            // We can change the default strategy of ZipOutputStream but no need as the default is good enough
//
//            // Add all files to the ZIP
//            byte[] buffer = new byte[BUFFER_SIZE];
//            for (Path path : filesToAdd) {
//                String relativePath = basePath.relativize(path).toString().replace('\\', '/');
//                ZipEntry entry = new ZipEntry(relativePath);
//                zos.putNextEntry(entry);
//
//                // Use optimized copy based on file size
//                long fileSize = Files.size(path);
//                if (fileSize < SMALL_FILE_THRESHOLD) {
//                    // For very small files, read all at once
//                    byte[] content = Files.readAllBytes(path);
//                    zos.write(content);
//                } else {
//                    // For larger files, use buffered I/O
//                    try (InputStream is = Files.newInputStream(path)) {
//                        int len;
//                        while ((len = is.read(buffer)) > 0) {
//                            zos.write(buffer, 0, len);
//                        }
//                    }
//                }
//
//                zos.closeEntry();
//            }
//        }
//    }
}
