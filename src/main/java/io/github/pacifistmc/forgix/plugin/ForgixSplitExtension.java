package io.github.pacifistmc.forgix.plugin;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import io.github.pacifistmc.forgix.Forgix;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class ForgixSplitExtension extends GroovyObjectSupport {
    private final Project project;

    public static Map<Project, ForgixSplitExtension> instances = new HashMap<>();
    public final Map<String, String> relocated = new HashMap<>();

    String loader;

    public ForgixSplitExtension(Project project) {
        this.project = project;
        instances.put(project, this);
    }

    public void setLoader(String loader) {
        this.loader = loader;

        instances.remove(project);
        instances.put(project, this);
    }

    public static Map<String, String> getRelocated(Project project) {
        return instances.get(project).relocated;
    }

    public Dependency split(Object dependency) {
        return split(dependency, null);
    }

    public Dependency split(Object dependency, @Nullable Closure<?> configure) {
        if (loader == null) {
            if (project.getName().equalsIgnoreCase("common")) {
                loader = "fabric";
                System.out.println("[Forgix] Using Fabric as default loader for common project.\n" + "[Forgix] You can override this by setting the loader property.");
            } else {
                loader = project.getName();
            }
        }

        Dependency baseDependency = project.getDependencies().create(dependency, configure);

        return splitDependency(baseDependency);
    }

    private Dependency splitDependency(Dependency baseDependency) {
        if (baseDependency instanceof ModuleDependency) {
            ((ModuleDependency) baseDependency).getArtifacts().forEach(artifact -> {
                this.splitFile(new File(artifact.getName()));
            });
        }
        if (baseDependency instanceof FileCollectionDependency) {
            ((FileCollectionDependency) baseDependency).getFiles().forEach(this::splitFile);
        }
        return baseDependency;
    }

    private void splitFile(File file) {
        try {
            File orginalFile = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + ".original" + FilenameUtils.getExtension(file.getName()));
            if (orginalFile.exists()) return;

            File splitFile = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + ".split" + FilenameUtils.getExtension(file.getName()));

            new Forgix.Split(file, splitFile, loader, new File(ForgixPlugin.rootProject.getRootDir(), ".gradle" + File.separator + "forgix")).split(true, project);

            Files.move(file.toPath(), orginalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(splitFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (splitFile.exists()) splitFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
