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
        ForgixExtension.ForgeContainer forgeSettings = ForgixPlugin.settings.getForgeContainer();
        ForgixExtension.FabricContainer fabricSettings = ForgixPlugin.settings.getFabricContainer();
        ForgixExtension.QuiltContainer quiltSettings = ForgixPlugin.settings.getQuiltContainer();

        List<ForgixExtension.CustomContainer> customSettingsList = ForgixPlugin.settings.getCustomContainers();

        Project forgeProject = null;
        Project fabricProject = null;
        Project quiltProject = null;

        Map<Project, ForgixExtension.CustomContainer> customProjects = new HashMap<>();

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

        for (ForgixExtension.CustomContainer customSettings : customSettingsList) {
            try {
                customProjects.put(ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equals(customSettings.getProjectName())).findFirst().get(), customSettings);
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (validation.size() < 2) {
            if (validation.size() == 1) ForgixPlugin.rootProject.getLogger().error("Only one project was found. Skipping mergeJar task.");
            if (validation.size() == 0) ForgixPlugin.rootProject.getLogger().error("No projects were found. Skipping mergeJar task.");
            return;
        }
        validation.clear();

        File forgeJar = null;
        File fabricJar = null;
        File quiltJar = null;

        Map<ForgixExtension.CustomContainer, File> customJars = new HashMap<>();

        if (forgeProject != null) {
            if (forgeSettings.getJarLocation() != null) {
                forgeJar = new File(forgeProject.getProjectDir(), forgeSettings.getJarLocation());
            } else {
                int i = 0;
                for (File file : new File(forgeProject.getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            forgeJar = file;
                        }
                    }
                }
            }
        }

        if (fabricProject != null) {
            if (fabricSettings.getJarLocation() != null) {
                fabricJar = new File(fabricProject.getProjectDir(), fabricSettings.getJarLocation());
            } else {
                int i = 0;
                for (File file : new File(fabricProject.getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            fabricJar = file;
                        }
                    }
                }
            }
        }

        if (quiltProject != null) {
            if (quiltSettings.getJarLocation() != null) {
                quiltJar = new File(quiltProject.getProjectDir(), quiltSettings.getJarLocation());
            } else {
                int i = 0;
                for (File file : new File(quiltProject.getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            quiltJar = file;
                        }
                    }
                }
            }
        }

        for (Map.Entry<Project, ForgixExtension.CustomContainer> entry : customProjects.entrySet()) {
            if (entry.getValue().getJarLocation() != null) {
                customJars.put(entry.getValue(), new File(entry.getKey().getProjectDir(), entry.getValue().getJarLocation()));
            } else {
                int i = 0;
                for (File file : new File(entry.getKey().getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            customJars.put(entry.getValue(), file);
                        }
                    }
                }
            }
        }

        File mergedJar = new File(ForgixPlugin.rootProject.getRootDir(), ForgixPlugin.settings.getOutputDir() + File.separator + ForgixPlugin.settings.getMergedJarName());
        if (mergedJar.exists()) FileUtils.forceDelete(mergedJar);
        if (!mergedJar.getParentFile().exists()) mergedJar.getParentFile().mkdirs();

        Path tempMergedJarPath = new Forgix(forgeJar, forgeSettings.getAdditionalRelocates(), forgeSettings.getMixins(), fabricJar, fabricSettings.getAdditionalRelocates(), quiltJar, quiltSettings.getAdditionalRelocates(), customJars, ForgixPlugin.settings.getGroup(), new File(ForgixPlugin.rootProject.getRootDir(), ".gradle" + File.separator + "forgix"), ForgixPlugin.settings.getMergedJarName(), ForgixPlugin.rootProject.getLogger()).merge().toPath();
        Files.move(tempMergedJarPath, mergedJar.toPath());
        try {
            Files.setPosixFilePermissions(mergedJar.toPath(), Forgix.perms);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) { }

        ForgixPlugin.rootProject.getLogger().debug("Merged jar created in " + (System.currentTimeMillis() - time) / 1000.0 + " seconds.");
    }
}
