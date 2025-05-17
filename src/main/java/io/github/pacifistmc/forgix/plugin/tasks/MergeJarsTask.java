package io.github.pacifistmc.forgix.plugin.tasks;

import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.utils.GradleProjectUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.rootProject;
import static io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin.settings;

@CacheableTask
public abstract class MergeJarsTask extends Jar {
    @Internal
    public abstract MapProperty<File, String> getJarFileProjectMap();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInputJarFiles();

    @Input
    public abstract Property<Boolean> getSilence();

    @Inject
    public MergeJarsTask() {
        // Configure archive properties
        archiveBaseName.set(settings.archiveBaseName);
        archiveClassifier.set(settings.archiveClassifier);
        archiveVersion.set(settings.archiveVersion);
        destinationDirectory.set(settings.destinationDirectory);

        // Initialize properties
        jarFileProjectMap.set(project.provider(this::createJarFileProjectMap));
        silence.set(settings.silence);

        // Setup the input files collection to track the keys from the map
        jarFileProjectMap.finalizeValueOnRead();
        inputJarFiles.setFrom(project.provider(() -> jarFileProjectMap.get().keySet()));
    }

    private Map<File, String> createJarFileProjectMap() {
        // Setup default configuration if needed
        if (settings.mergeConfigurations.isEmpty()) settings.setupDefaultConfiguration();

        // Create the jar file to project map
        Map<File, String> jarMap = new HashMap<>();
        settings.mergeConfigurations.forEach((name, config) -> {
            File outputFile = GradleProjectUtils.getBestOutputFile(config,
                    rootProject.allprojects.stream().filter(p ->
                            p.name.equalsIgnoreCase(name)).findFirst().orElse(null));
            if (outputFile != null) jarMap.put(outputFile, name);
        });
        return jarMap;
    }

    @TaskAction
    void mergeJars() {
        Map<File, String> jarMap = jarFileProjectMap.get();
        File outputFile = archiveFile.get().asFile;
        outputFile.ensureCreatable();

        // Validate input
        if (jarMap.size() < 2) {
            throw new IllegalStateException("""
                    At least 2 projects must be detected in order to merge them.
                    Please configure the forgix extension in your build.gradle file to specify the projects you want to merge.
                    See https://github.com/PacifistMC/Forgix for more information.""");
        }

        // Perform the merge operation
        Forgix.mergeLoaders(jarMap, outputFile, silence.get());
    }

    @Override
    protected CopyAction createCopyAction() {
        return _ -> WorkResults.didWork(true);
    }
}
