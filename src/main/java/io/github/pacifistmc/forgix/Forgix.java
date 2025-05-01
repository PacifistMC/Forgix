package io.github.pacifistmc.forgix;

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
    private static final String VERSION = "1.3.0";
    private static final String MANIFEST_VERSION_KEY = "Forgix-Version";
    private static final String MANIFEST_MAPPINGS_KEY = "Forgix-Mappings";

    public static void run(Map<File, String> jarFileProjectMap, File outputFile, boolean silence = false) {
        if (!silence) {
            """
            Thank you for using Forgix!
            Forgix: ${VERSION}
            Please report any issues to https://github.com/PacifistMC/Forgix/issues""".println();
        }

        List<RelocationConfig> configs = new ArrayList<>();
        jarFileProjectMap.forEach((jarFile, conflictPrefix) -> configs.add((RelocationConfig) (jarFile:new JarFile(jarFile), conflictPrefix:conflictPrefix)));
        Relocator.relocate(configs);

        Map<File, String> tinyFiles = configs.stream()
                .map(config -> config.tinyFile)
                .collect(Collectors.toMap(
                        Function.identity(),
                        file -> "META-INF/forgix/${file.getName()}"
                ));

        try(ByteArrayOutputStream baos = JAR.combineJars(jarFileProjectMap.keySet(), extraManifestAttributes:new HashMap<>() {
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

    public static void run(List<File> jarFiles, File outputFile) {
        run(jarFiles.stream().collect(Collectors.toMap(file -> file, _ -> UUID.randomUUID().toString().first(8))), outputFile);
    }
}
