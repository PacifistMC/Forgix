package io.github.pacifistmc.forgix.utils;

import io.github.pacifistmc.forgix.core.RelocationConfig;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

/**
 * Writes relocation mappings as tiny files. <br>
 * Namespace: original -> relocated
 * This only writes class mappings.
 */
public class TinyClassWriter {
    private TinyClassWriter() { }

    public static void write(List<RelocationConfig> relocationConfigs, File outputDirectory) {
        // Iterate over each relocation and it's mappings
        relocationConfigs.parallelStream().forEach(relocationConfig -> {
            // Set the tiny file for the relocation
            if (relocationConfig.tinyFile == null)
                relocationConfig.setTinyFile(
                        new File(outputDirectory, relocationConfig.jarFile.getName()
                                .setBaseNameExtension("${relocationConfig.conflictPrefix}.tiny"))
                                .createFileWithParents("tiny\t2\t0\toriginal\trelocated\n")
                );
            // Write the mappings
            write(relocationConfig.tinyFile, relocationConfig.mappings);
        });
    }

    public static void write(File tinyFile, Map<String, String> mappings) {
        try (var fileWriter = new FileWriter(tinyFile, true)) {
            for (var mappingsEntry : mappings.entrySet()) {
                if (!mappingsEntry.getKey().endsWith(".class")) continue; // Return if not a class file
                // Write class mappings
                fileWriter.write("c\t${mappingsEntry.getKey().removeExtension()}\t${mappingsEntry.getValue().removeExtension()}\n");
            }
        }
    }
}
