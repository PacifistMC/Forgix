package io.github.pacifistmc.forgix.plugin.tasks;

import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.utils.GradleProjectUtils;
import io.github.pacifistmc.forgix.utils.JAR;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.WorkResults;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.rootProject;
import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.settings;

public class MergeJarsTask extends Jar {
    private final File mergedJar;
    private static final AtomicReference<byte[]> hash = new AtomicReference<>();
    private static final AtomicBoolean running = new AtomicBoolean();

    private final Map<File, String> jarFileProjectMap = new HashMap<>() {
        {
            // Setup default configuration
            if (settings.getMergeConfigurations().isEmpty()) settings.setupDefaultConfiguration();

            // Get the best output file for each project (the jar file we want to merge)
            settings.getMergeConfigurations().forEach((name, config) -> put(GradleProjectUtils.getBestOutputFile(config,rootProject.getAllprojects().stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElse(null)), name));
        }
    };

    public MergeJarsTask() {
        getArchiveBaseName().convention(settings.getArchiveBaseName());
        getArchiveClassifier().convention(settings.getArchiveClassifier());
        getArchiveVersion().convention(settings.getArchiveVersion());
        getDestinationDirectory().convention(settings.getDestinationDirectory());
        var destDir = getDestinationDirectory().get().asFile;

        // Create the destination directory if it doesn't exist
        destDir.mkdirs();

//        // Don't allow custom input files
//        getInputs().files();

        // Set output file
        mergedJar = new File(destDir, getArchiveFileName().get());
        getOutputs().files(mergedJar);

        // Up-to-date check
        getOutputs().upToDateWhen(_ -> isUpToDate());

        // Set input files
        jarFileProjectMap.keySet().forEach(file -> getInputs().file(file));
    }

    void run() {
        if (running.get()) return;
        if (mergedJar.exists()) mergedJar.deleteQuietly();
        if (settings.getMergeConfigurations().size() < 2) {
            throw new IllegalStateException("""
                    At least 2 projects must be detected in order to merge them.
                    Please configure the forgix extension in your build.gradle file to specify the projects you want to merge.
                    See https://github.com/PacifistMC/Forgix for more information.""");
        }

        running.set(true);
        try {
            Forgix.mergeLoaders(jarFileProjectMap, mergedJar, settings.getSilence().get());
            hash.set(JAR.computeHash(jarFileProjectMap.keySet()));
        } finally {
            running.set(false);
        }
    }

    @Internal
    boolean isUpToDate() {
        if (!mergedJar.exists()) return false;
        if (hash.get() == null) return false;
        return Arrays.equals(hash.get(), JAR.computeHash(jarFileProjectMap.keySet()));
    }

    @Override
    protected CopyAction createCopyAction() {
        return copyActionProcessingStream -> {
            copyActionProcessingStream.process(_ -> {
                if (isUpToDate()) return;
                run();
            });
            return WorkResults.didWork(true);
        };
    }
}
