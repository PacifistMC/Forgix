package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.plugin.configurations.ForgixMergeConfiguration;
import io.github.pacifistmc.forgix.plugin.tasks.MergeJarsTask;
import io.github.pacifistmc.forgix.plugin.tasks.MergeVersionsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ForgixGradlePlugin implements Plugin<Project> {
    public static ForgixMergeConfiguration settings;
    public static Project rootProject;

    @Override
    public void apply(Project project) {
        ForgixGradlePlugin.rootProject = project;

        settings = rootProject.getExtensions().create("forgix", ForgixMergeConfiguration.class);
        rootProject.getTasks().register("mergeJars", MergeJarsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your modloader & plugin jars into a single jar.");
        });

        rootProject.getTasks().register("mergeVersions", MergeVersionsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your minecraft version specific jars into a single jar.");
        });
    }
}
