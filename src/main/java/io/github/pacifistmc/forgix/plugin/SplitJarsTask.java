package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.Forgix;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;

public class SplitJarsTask extends DefaultTask {
    private RegularFileProperty jar;

    @TaskAction
    void remapJar() {
        if (!ForgixSplitExtension.instances.containsKey(getProject())) return;
        if (ForgixSplitExtension.getRelocated(getProject()).isEmpty()) return;

        try {
            new Forgix.Split.MergeBack(jar.getAsFile().get(), ForgixSplitExtension.getRelocated(getProject()), new File(ForgixPlugin.rootProject.getRootDir(), ".gradle" + File.separator + "forgix")).mergeBack();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setJar(AbstractArchiveTask jarTask) {
        jar = getProject().getObjects().fileProperty();
        jar.convention(jarTask.getDestinationDirectory().file(jarTask.getArchiveFileName()));
    }
}
