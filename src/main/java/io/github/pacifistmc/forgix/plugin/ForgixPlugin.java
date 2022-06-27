package io.github.pacifistmc.forgix.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("NullableProblems")
public class ForgixPlugin implements Plugin<Project> {
    static ForgixExtension settings;
    static Project rootProject;

    @Override
    public void apply(Project project) {
        ForgixPlugin.rootProject = project;

        settings = rootProject.getExtensions().create("forgix", ForgixExtension.class);
        rootProject.getTasks().register("mergeJars", MergeJarsTask.class).configure(forgix -> {
            forgix.setGroup("forgix");
            forgix.setDescription("Merges Fabric (also Quilt) and Forge jars into a single jar!");
        });
    }
}
