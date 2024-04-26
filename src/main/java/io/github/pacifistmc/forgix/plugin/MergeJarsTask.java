package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.Forgix;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@SuppressWarnings({"ConstantConditions", "OptionalGetWithoutIsPresent", "ResultOfMethodCallIgnored"})
public class MergeJarsTask extends DefaultTask {
    @TaskAction
    void mergeJars() throws IOException {
        long time = System.currentTimeMillis();
        if (ForgixPlugin.settings.mergedJarName == null || ForgixPlugin.settings.group == null) {
            ForgixPlugin.rootProject.getLogger().error("Please configure \"group\" and \"mergedJarName\" manually!");
            ForgixPlugin.rootProject.getLogger().info("Check out how to configure them here: " + "https://github.com/PacifistMC/Forgix#configuration");
            return;
        }
        ForgixMergeExtension.ForgeContainer forgeSettings = ForgixPlugin.settings.getForgeContainer();
        ForgixMergeExtension.FabricContainer fabricSettings = ForgixPlugin.settings.getFabricContainer();
        ForgixMergeExtension.QuiltContainer quiltSettings = ForgixPlugin.settings.getQuiltContainer();

        List<Forgix.Merge.CustomContainer> customSettingsList = ForgixPlugin.settings.getForgixCustomContainers();

        Project forgeProject = null;
        Project fabricProject = null;
        Project quiltProject = null;

        Map<Project, Forgix.Merge.CustomContainer> customProjects = new HashMap<>();

        List<Boolean> validation = new ArrayList<>();
        try {
            forgeProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(forgeSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }
        try {
            fabricProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(fabricSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }
        try {
            quiltProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(quiltSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }

        for (Forgix.Merge.CustomContainer customSettings : customSettingsList) {
            try {
                customProjects.put(ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equals(customSettings.loaderName)).findFirst().get(), customSettings);
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (validation.size() < 2) {
            if (validation.size() == 1) ForgixPlugin.rootProject.getLogger().error("Only one project was found. Skipping mergeJar task.");
            if (validation.size() == 0) ForgixPlugin.rootProject.getLogger().error("No projects were found. Skipping mergeJar task.");
            return;
        }
        validation.clear();

        Map<Forgix.Merge.CustomContainer, File> customJars = new HashMap<>();

        File forgeJar = getProjectJarFile(forgeProject, forgeSettings.getJarLocation());

        File fabricJar = getProjectJarFile(fabricProject, fabricSettings.getJarLocation());

        File quiltJar = getProjectJarFile(quiltProject, quiltSettings.getJarLocation());

        for (Map.Entry<Project, Forgix.Merge.CustomContainer> entry : customProjects.entrySet()) {
            customJars.put(entry.getValue(), getProjectJarFile(entry.getKey(), entry.getValue().jarLocation));
        }

        File mergedJar = new File(ForgixPlugin.rootProject.getRootDir(), ForgixPlugin.settings.getOutputDir() + File.separator + ForgixPlugin.settings.getMergedJarName());
        if (mergedJar.exists()) FileUtils.forceDelete(mergedJar);
        if (!mergedJar.getParentFile().exists()) mergedJar.getParentFile().mkdirs();

        Path tempMergedJarPath = new Forgix.Merge(forgeJar, forgeSettings.getAdditionalRelocates(), forgeSettings.getMixins(), fabricJar, fabricSettings.getAdditionalRelocates(), quiltJar, quiltSettings.getAdditionalRelocates(), customJars, ForgixPlugin.settings.getGroup(), new File(ForgixPlugin.rootProject.getRootDir(), ".gradle" + File.separator + "forgix"), ForgixPlugin.settings.getMergedJarName(), ForgixPlugin.settings.getRemoveDuplicates(), ForgixPlugin.rootProject.getLogger()).merge(false).toPath();
        Files.move(tempMergedJarPath, mergedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.setPosixFilePermissions(mergedJar.toPath(), Forgix.Merge.perms);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) { }

        ForgixPlugin.rootProject.getLogger().debug("Merged jar created in " + (System.currentTimeMillis() - time) / 1000.0 + " seconds.");
    }

    private File getProjectJarFile(Project project, String jarLocation) {
        File jar = null;
        if (project != null) {
            if (jarLocation != null) {
                return new File(project.getProjectDir(), jarLocation);
            } else {
                int i = 0;
                for (File file : new File(project.getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            jar = file;
                        }
                    }
                }
            }
        }
        return jar;
    }
}
