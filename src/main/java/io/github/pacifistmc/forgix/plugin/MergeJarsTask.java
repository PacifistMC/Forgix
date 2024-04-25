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
        ForgixMergeExtension.NeoForgeContainer neoforgeSettings = ForgixPlugin.settings.getNeoForgeContainer();
        ForgixMergeExtension.FabricContainer fabricSettings = ForgixPlugin.settings.getFabricContainer();
        ForgixMergeExtension.QuiltContainer quiltSettings = ForgixPlugin.settings.getQuiltContainer();

        List<ForgixMergeExtension.CustomContainer> customSettingsList = ForgixPlugin.settings.getCustomContainers();

        Project forgeProject = null;
        Project neoforgeProject = null;
        Project fabricProject = null;
        Project quiltProject = null;

        Map<Project, ForgixMergeExtension.CustomContainer> customProjects = new HashMap<>();

        List<Boolean> validation = new ArrayList<>();
        try {
            forgeProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(forgeSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }
        try {
            neoforgeProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(neoforgeSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) {}
        try {
            fabricProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(fabricSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }
        try {
            quiltProject = ForgixPlugin.rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(ForgixPlugin.rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(quiltSettings.getProjectName())).findFirst().get();
            validation.add(true);
        } catch (NoSuchElementException ignored) { }

        for (ForgixMergeExtension.CustomContainer customSettings : customSettingsList) {
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
        File neoforgeJar = null;
        File fabricJar = null;
        File quiltJar = null;

        Map<ForgixMergeExtension.CustomContainer, File> customJars = new HashMap<>();

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

        if (neoforgeProject != null) {
            if (neoforgeSettings.getJarLocation() != null) {
                neoforgeJar = new File(neoforgeProject.getProjectDir(), neoforgeSettings.getJarLocation());
            } else {
                int i = 0;
                for (File file : new File(neoforgeProject.getBuildDir(), "libs").listFiles()) {
                    if (file.isDirectory()) continue;
                    if (io.github.pacifistmc.forgix.utils.FileUtils.isZipFile(file)) {
                        if (file.getName().length() < i || i == 0) {
                            i = file.getName().length();
                            neoforgeJar = file;
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

        for (Map.Entry<Project, ForgixMergeExtension.CustomContainer> entry : customProjects.entrySet()) {
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

        Path tempMergedJarPath = new Forgix.Merge(forgeJar, forgeSettings.getAdditionalRelocates(), forgeSettings.getMixins(), neoforgeJar, neoforgeSettings.getAdditionalRelocates(), neoforgeSettings.getMixins(), fabricJar, fabricSettings.getAdditionalRelocates(), quiltJar, quiltSettings.getAdditionalRelocates(), customJars, ForgixPlugin.settings.getGroup(), new File(ForgixPlugin.rootProject.getRootDir(), ".gradle" + File.separator + "forgix"), ForgixPlugin.settings.getMergedJarName(), ForgixPlugin.settings.getRemoveDuplicates(), ForgixPlugin.rootProject.getLogger()).merge(false).toPath();
        Files.move(tempMergedJarPath, mergedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.setPosixFilePermissions(mergedJar.toPath(), Forgix.Merge.perms);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) { }

        ForgixPlugin.rootProject.getLogger().debug("Merged jar created in " + (System.currentTimeMillis() - time) / 1000.0 + " seconds.");
    }
}
