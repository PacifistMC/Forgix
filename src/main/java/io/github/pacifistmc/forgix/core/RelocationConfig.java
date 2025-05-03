package io.github.pacifistmc.forgix.core;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Configuration for relocating conflicting files in JARs.
 */
public class RelocationConfig {
    private JarFile jarFile;
    private final String conflictPrefix;

    // INTERNAL USE ONLY. DO NOT TOUCH.
    private Map<String, String> mappings;
    private File tinyFile;

    /**
     * Creates a new RelocationConfig.
     * @param jarFile The JAR file to relocate
     * @param conflictPrefix The prefix to use for relocated files
     */
    public RelocationConfig(JarFile jarFile, String conflictPrefix) {
        this.jarFile = jarFile;
        this.conflictPrefix = conflictPrefix;
        this.mappings = new ConcurrentHashMap<>();
        this.tinyFile = null;
    }

    /**
     * Creates a new RelocationConfig with all fields specified.
     * This constructor is primarily for internal use.
     * @param jarFile The JAR file to relocate
     * @param conflictPrefix The prefix to use for relocated files
     * @param mappings The mappings of original paths to relocated paths
     * @param tinyFile The tiny file for mappings
     */
    public RelocationConfig(JarFile jarFile, String conflictPrefix, Map<String, String> mappings, File tinyFile) {
        this.jarFile = jarFile;
        this.conflictPrefix = conflictPrefix;
        this.mappings = mappings;
        this.tinyFile = tinyFile;
    }

    /**
     * Sets the JAR file to relocate.
     * @param jarFile The JAR file
     */
    public void setJarFile(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    /**
     * Gets the JAR file to relocate.
     * @return The JAR file
     */
    public JarFile getJarFile() {
        return jarFile;
    }

    /**
     * Gets the prefix to use for relocated files.
     * @return The conflict prefix
     */
    public String getConflictPrefix() {
        return conflictPrefix;
    }

    /**
     * Gets the mappings of original paths to relocated paths.
     * @return The mappings
     */
    public Map<String, String> getMappings() {
        return mappings;
    }

    /**
     * Sets the mappings of original paths to relocated paths.
     * @param mappings The mappings
     */
    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings;
    }

    /**
     * Gets the tiny file for mappings.
     * @return The tiny file
     */
    public File getTinyFile() {
        return tinyFile;
    }

    /**
     * Sets the tiny file for mappings.
     * @param tinyFile The tiny file
     */
    public void setTinyFile(File tinyFile) {
        this.tinyFile = tinyFile;
    }
}
