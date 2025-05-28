package io.github.pacifistmc.forgix;

import io.github.pacifistmc.forgix.core.Multiversion;
import io.github.pacifistmc.forgix.core.RelocationConfig;
import io.github.pacifistmc.forgix.core.Relocator;
import io.github.pacifistmc.forgix.utils.JAR;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Forgix {
    public static final String VERSION = "2.0.0-SNAPSHOT.5.1";
    private static final String MANIFEST_VERSION_KEY = "Forgix-Version";
    private static final String MANIFEST_MAPPINGS_KEY = "Forgix-Mappings";

    public static void mergeLoaders(Map<File, String> jarsAndLoadersMap, File outputFile, boolean silence = false) {
        if (!silence) {
            """
            Thank you for using Forgix!
            Forgix: ${VERSION}
            Please report any issues to https://github.com/PacifistMC/Forgix/issues""".println();
        }

        List<RelocationConfig> configs = new ArrayList<>();
        jarsAndLoadersMap.forEach((jar, loader) -> configs.add(new RelocationConfig(new JarFile(jar), loader)));
        Relocator.relocate(configs);

        Map<File, String> tinyFiles = configs.stream()
                .map(RelocationConfig::getTinyFile)
                .collect(Collectors.toMap(
                        Function.identity(),
                        file -> "META-INF/forgix/${file.getName()}"
                ));

        try (var baos = JAR.combineJars(jarsAndLoadersMap.keySet(),
                extraManifestAttributes:Map.of(
                    MANIFEST_VERSION_KEY, VERSION,
                    MANIFEST_MAPPINGS_KEY, String.join(";", tinyFiles.values())
                ));
             var fos = new FileOutputStream(outputFile)
        ) {
            baos.writeTo(fos);
        }
        JAR.addFiles(outputFile, tinyFiles);
        JAR.setPerms(outputFile);
    }

    public static void mergeVersions(Collection<File> jarFiles, File outputFile) {
        try (var baos = Multiversion.mergeVersions(jarFiles); var fos = new FileOutputStream(outputFile)) {
            baos.writeTo(fos);
        }
        JAR.setPerms(outputFile);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            """
            Usage:
            java -jar forgix.jar mergeJars --output <outputJar> --<loader1> <jar1> --<loader2> <jar2> [--<loader3> <jar3> ...]
            java -jar forgix.jar mergeVersions --output <outputJar> <jar1> <jar2> [<jar3> ...]

            Example:
            java -jar forgix.jar mergeJars --output path/to/merged.jar --forge myforgemod.jar --fabric myfabricmod.jar
            java -jar forgix.jar mergeVersions --output path/to/merged.jar my1.16mod.jar my1.17mod.jar my1.18mod.jar""".errExit();
        }

        String command = args[0];

        // Handle mergeJars command
        if ("mergeJars".equals(command)) {
            if (args.length < 5 || args.length % 2 != 1) {
                "Usage: java -jar forgix.jar mergeJars --output <outputJar> --<loader1> <jar1> --<loader2> <jar2> [--<loader3> <jar3> ...]".errExit();
            }

            Map<File, String> jarsAndLoadersMap = new HashMap<>();
            File outputFile = null;

            for (int i = 1; i < args.length; i += 2) {
                if (!args[i].startsWith("--")) "Parameter must start with --".errExit();

                var param = args[i].substring(2);
                var value = args[i + 1];

                if ("output".equals(param)) {
                    outputFile = new File(value);
                    continue;
                }

                var jarFile = new File(value);
                if (!jarFile.exists() || !jarFile.isFile()) "Jar file not found: ${value}".errExit();
                jarsAndLoadersMap.put(jarFile, param);
            }

            if (outputFile == null) "Output jar file must be specified with --output".errExit();
            if (jarsAndLoadersMap.size() < 2) "At least two jars must be provided".errExit();

            try {
                mergeLoaders(jarsAndLoadersMap, outputFile);
                "Successfully merged jars into ${outputFile.getAbsolutePath()}".println();
            } catch (Exception e) {
                "Error merging jars: ${e.getMessage()}".errExit();
            }
            return;
        }

        // Handle mergeVersions command
        if ("mergeVersions".equals(command)) {
            if (args.length < 4) {
                "Usage: java -jar forgix.jar mergeVersions --output <outputJar> <jar1> <jar2> [<jar3> ...]".errExit();
            }

            if (!"--output".equals(args[1])) "Output jar file must be specified with --output".errExit();

            File outputFile = new File(args[2]);
            List<File> jarFiles = new ArrayList<>();

            for (int i = 3; i < args.length; i++) {
                var jarFile = new File(args[i]);
                if (!jarFile.exists() || !jarFile.isFile()) "Jar file not found: ${args[i]}".errExit();
                jarFiles.add(jarFile);
            }

            if (jarFiles.size() < 2) "At least two jars must be provided".errExit();

            try {
                mergeVersions(jarFiles, outputFile);
                "Successfully merged version jars into ${outputFile.getAbsolutePath()}".println();
            } catch (Exception e) {
                "Error merging version jars: ${e.getMessage()}".errExit();
            }
            return;
        }

        // Unknown command
        "Unknown command: ${command}".errExit();
    }
}
