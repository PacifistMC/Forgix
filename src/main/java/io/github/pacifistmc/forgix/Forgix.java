package io.github.pacifistmc.forgix;

import io.github.pacifistmc.forgix.core.Multiversion;
import io.github.pacifistmc.forgix.core.RelocationConfig;
import io.github.pacifistmc.forgix.core.Relocator;
import io.github.pacifistmc.forgix.utils.JAR;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Forgix {
    public static final String VERSION = "2.0.0-SNAPSHOT.1";
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

        try(ByteArrayOutputStream baos = JAR.combineJars(jarsAndLoadersMap.keySet(), extraManifestAttributes:new HashMap<>() {
            {
                put(MANIFEST_VERSION_KEY, VERSION);
                put(MANIFEST_MAPPINGS_KEY, String.join(";", tinyFiles.values()));
            }
        })) {
            try (var fos = new FileOutputStream(outputFile)) {
                baos.writeTo(fos);
            }
        }
        JAR.addFiles(outputFile, tinyFiles);
        JAR.setPerms(outputFile);
    }

    public static void mergeVersions(Collection<File> jarFiles, File outputFile) {
        try (var baos = Multiversion.mergeVersions(jarFiles)) {
            try (var fos = new FileOutputStream(outputFile)) {
                baos.writeTo(fos);
            }
        }
        JAR.setPerms(outputFile);
    }
}
