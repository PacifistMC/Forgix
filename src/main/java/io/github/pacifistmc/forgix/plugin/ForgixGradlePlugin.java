package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.plugin.configurations.ForgixConfiguration;
import io.github.pacifistmc.forgix.plugin.tasks.MergeJarsTask;
import io.github.pacifistmc.forgix.plugin.tasks.MergeVersionsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ForgixGradlePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var rootProject = project.rootProject;

        rootProject.extensions.create("forgix", ForgixConfiguration.class);
        rootProject.tasks.register("mergeJars", MergeJarsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your modloader & plugin jars into a single jar.");
        });

        rootProject.tasks.register("mergeVersions", MergeVersionsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your minecraft version specific jars into a single jar.");
        });

        if (!rootProject.plugins.hasPlugin("java")) rootProject.plugins.apply("java"); // Apply Java plugin if not already applied, we need this for auto-run
        rootProject.gradle.projectsEvaluated(gradle -> {
            // Make our tasks be able to use inputs from any task that outputs a jar and setup ordering so it runs after them
            gradle.allprojects(proj ->
                    proj.tasks.matching(task ->
                            task.outputs.files.files.stream().anyMatch(file -> file.name.endsWith(".jar"))
                    ).forEach(task -> {
                        if (task instanceof MergeJarsTask || task instanceof MergeVersionsTask) return;
                        gradle.rootProject.tasks.getByName("mergeJars").mustRunAfter(task);
                        gradle.rootProject.tasks.getByName("mergeVersions").mustRunAfter(task);
                    })
            );

            // If autoRun is enabled, then only run mergeJars if the root project is being built (the user ran `gradle build` or `gradle assemble`)
            if (!gradle.rootProject.extensions.getByType(ForgixConfiguration.class).autoRun.get()) return;
            gradle.rootProject.tasks.getByName("jar").finalizedBy(gradle.rootProject.tasks.getByName("mergeJars"));
        });
    }
}
