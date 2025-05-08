package io.github.pacifistmc.forgix.plugin.tasks;

import io.github.pacifistmc.forgix.Forgix;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.WorkResults;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.settings;

public class MergeVersionsTask extends Jar {
    private final File outputJar;
    private Collection<File> inputJars = Collections.emptyList();
    private static final AtomicBoolean running = new AtomicBoolean();


    public MergeVersionsTask() {
        getArchiveBaseName().convention(settings.getArchiveBaseName().get());
        getArchiveClassifier().convention(settings.getArchiveClassifier().get());
        getArchiveVersion().convention("${settings.getArchiveVersion().get()}-multi");
        getDestinationDirectory().convention(settings.destinationDirectory.get().dir("multiversion"));

        var destDir = getDestinationDirectory().get().asFile; destDir.mkdirs();
        outputJar = new File(destDir, getArchiveFileName().get());
        getOutputs().files(outputJar);

        if (settings.multiversionConfiguration == null) return;
        getInputs().files(settings.multiversionConfiguration.inputJars);
        inputJars = settings.multiversionConfiguration.inputJars.getFiles();
    }

    void run() {
        if (running.get()) return;
        if (outputJar.exists()) outputJar.deleteQuietly();
        if (inputJars.size() < 2) {
            throw new IllegalStateException("""
                            At least 2 jars must be selected in order for multiversion.
                            Please configure the forgix extension in your build.gradle file to specify the jars for multiversion.
                            See https://github.com/PacifistMC/Forgix for more information.""");
        }
        running.set(true);
        try {
            Forgix.mergeVersions(inputJars, outputJar);
        } finally {
            running.set(false);
        }
    }

    @Override
    protected CopyAction createCopyAction() {
        return copyActionProcessingStream -> {
            copyActionProcessingStream.process(_ -> {
                run();
            });
            return WorkResults.didWork(true);
        };
    }
}
