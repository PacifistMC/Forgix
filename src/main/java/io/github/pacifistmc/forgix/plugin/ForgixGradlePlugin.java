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

        settings = rootProject.extensions.create("forgix", ForgixMergeConfiguration.class);
        rootProject.tasks.register("mergeJars", MergeJarsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your modloader & plugin jars into a single jar.");
        });

        rootProject.tasks.register("mergeVersions", MergeVersionsTask.class).configure(task -> {
            task.setGroup("forgix");
            task.setDescription("This task merges all your minecraft version specific jars into a single jar.");
        });

        if (!settings.autoRun.get()) return;
        if (!rootProject.plugins.hasPlugin("java")) rootProject.plugins.apply("java"); // Apply Java plugin if not already applied, we need this for auto-run
//        gradle.projectsEvaluated {
//            rootProject.tasks.jar.finalizedBy(rootProject.tasks.mergeJars) // Only run mergeJars if the root project is being built
//            rootProject.subprojects.each { // Make mergeJars be able to use inputs from any task that outputs a jar and setup ordering so it runs after them
//                it.tasks.findAll { task ->
//                        task.outputs.files.files.any { it.name.endsWith('.jar') }
//                }.each {
//                    rootProject.tasks.mergeJars.mustRunAfter(it)
//                }
//            }
//        }
        rootProject.gradle.projectsEvaluated(gradle -> {
            gradle.rootProject.tasks.getByName("jar").finalizedBy(gradle.rootProject.tasks.getByName("mergeJars")); // Only run mergeJars if the root project is being built (the user ran `gradle build` or `gradle assemble`)
            gradle.rootProject.subprojects.forEach(subproject -> // Make mergeJars be able to use inputs from any task that outputs a jar and setup ordering so it runs after them
                subproject.tasks.matching(task ->
                        task.outputs.files.files.stream().anyMatch(file -> file.name.endsWith(".jar"))
                ).forEach(task ->
                        gradle.rootProject.tasks.getByName("mergeJars").mustRunAfter(task)
                )
            );
        });
    }
}
