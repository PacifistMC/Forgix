package io.github.pacifistmc.forgix;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ForgixPlugin implements Plugin<Project> {
    static ForgixExtension settings;
    static Project rootProject;

    @Override
    public void apply(Project project) {
        ForgixPlugin.rootProject = project;

        settings = rootProject.getExtensions().create("forgix", ForgixExtension.class);
        rootProject.getTasks().create("mergeJars", MergeJarsTask.class);
    }
}
