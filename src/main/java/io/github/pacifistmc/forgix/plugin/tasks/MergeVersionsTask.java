package io.github.pacifistmc.forgix.plugin.tasks;

import io.github.pacifistmc.forgix.Forgix;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.*;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import javax.inject.Inject;

import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.settings;

@CacheableTask
public abstract class MergeVersionsTask extends Jar {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInputJarFiles();

    @Inject
    public MergeVersionsTask() {
        // Configure archive properties
        archiveBaseName.set(settings.archiveBaseName);
        archiveClassifier.set(settings.archiveClassifier);
        archiveVersion.set(project.provider(() -> "${settings.archiveVersion.get()}-multi"));
        destinationDirectory.set(settings.destinationDirectory.get().dir("multiversion"));

        // Set up input files
        if (settings.multiversionConfiguration != null) inputJarFiles.setFrom(settings.multiversionConfiguration.inputJars);
    }

    @TaskAction
    void mergeVersions() {
        // Get input and output files
        File outputFile = archiveFile.get().getAsFile();
        outputFile.ensureCreatable();

        // Validate input
        if (inputJarFiles.getFiles().size() < 2) {
            throw new IllegalStateException("""
                    At least 2 jars must be selected in order for multiversion.
                    Please configure the forgix extension in your build.gradle file to specify the jars for multiversion.
                    See https://github.com/PacifistMC/Forgix for more information.""");
        }

        // Perform the merge operation
        Forgix.mergeVersions(inputJarFiles.getFiles(), outputFile);
    }

    @Override
    protected CopyAction createCopyAction() {
        return _ -> WorkResults.didWork(true);
    }
}
