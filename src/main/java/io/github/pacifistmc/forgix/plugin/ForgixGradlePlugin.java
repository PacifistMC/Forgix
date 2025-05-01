package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.plugin.configurations.ForgixMergeConfiguration;
import io.github.pacifistmc.forgix.plugin.tasks.MergeJarsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ForgixGradlePlugin implements Plugin<Project> {
    public static ForgixMergeConfiguration settings;
    public static Project rootProject;

    @Override
    public void apply(Project project) {
        ForgixGradlePlugin.rootProject = project;

        settings = rootProject.getExtensions().create("forgix", ForgixMergeConfiguration.class);
        rootProject.getTasks().register("mergeJars", MergeJarsTask.class).configure(forgix -> {
            forgix.setGroup("forgix");
            forgix.setDescription("This plugin merges all your modloader & plugin jars into a single jar.");
        });
    }
}
