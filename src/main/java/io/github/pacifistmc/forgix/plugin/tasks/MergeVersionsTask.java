package io.github.pacifistmc.forgix.plugin.tasks;

import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.plugin.configurations.ForgixConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.*;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import javax.inject.Inject;

@CacheableTask
public abstract class MergeVersionsTask extends Jar {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInputJarFiles();

    private final ForgixConfiguration settings = this.project.rootProject.extensions.getByType(ForgixConfiguration.class);

    @Inject
    public MergeVersionsTask() {
        var multiversion = settings.multiversionConfiguration;

        if (multiversion != null) {
            // Configure archive properties
            archiveBaseName.set(multiversion.archiveBaseName);
            archiveClassifier.set(multiversion.archiveClassifier);
            archiveVersion.set(multiversion.archiveVersion);
            destinationDirectory.set(multiversion.destinationDirectory.get());

            // Set up input files
            inputJarFiles.setFrom(multiversion.inputJars.get());
        }
    }

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
        return _ -> {
            mergeVersions();
            return WorkResults.didWork(true);
        };
    }
}
