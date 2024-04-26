package io.github.pacifistmc.forgix.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

@SuppressWarnings("NullableProblems")
public class ForgixPlugin implements Plugin<Project> {
    static ForgixMergeExtension settings;
    static Project rootProject;

    @Override
    public void apply(Project project) {
        ForgixPlugin.rootProject = project;

//        if (!rootProject.getSubprojects().isEmpty() && rootProject.getAllprojects().size() != 1) {
            settings = rootProject.getExtensions().create("forgix", ForgixMergeExtension.class);

//            rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).forEach(this::setupSplit);
////            ForgixSplitExtension.instances.forEach((p, e) -> p.afterEvaluate(_p -> e.split()));

            rootProject.getTasks().register("mergeJars", MergeJarsTask.class).configure(forgix -> {
                forgix.setGroup("forgix");
                forgix.setDescription("Merges Fabric (also Quilt) and Forge jars into a single jar!");
            });
//        } else {
//            setupSplit(project);
////            ForgixSplitExtension.instances.forEach((p, e) -> p.afterEvaluate(_p -> e.split()));
//        }
    }

//    private void setupSplit(Project project) {
//        project.getExtensions().create("forgix", ForgixSplitExtension.class, project);
//
//        project.getTasks().register("remapSplit", SplitJarsTask.class).configure(task -> {
//            task.setGroup("forgix");
//            if (project.getTasks().named("shadowJar", AbstractArchiveTask.class).isPresent()) {
//                task.setJar(project.getTasks().named("shadowJar", AbstractArchiveTask.class).get());
//            } else {
//                task.setJar(project.getTasks().named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).get());
//            }
//        });
//
//        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, AbstractArchiveTask.class).configure(task -> {
//            task.finalizedBy(project.getTasks().named("remapSplit", SplitJarsTask.class).get());
//        });
//    }
}
