package io.github.pacifistmc.forgix.plugin;

import groovy.lang.Closure;
import io.github.pacifistmc.forgix.Forgix;
import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// I couldn't find any good resources on how to do this, so I just went with it and wrote a lot of dumb code.
@SuppressWarnings({"unused", "ConstantConditions"})
public class ForgixMergeExtension {
    String group;
    String mergedJarName;
    List<String> removeDuplicates;

    String outputDir = "Merged";

    public ForgixMergeExtension() {
        if (group != null) {
            if (ForgixPlugin.rootProject.hasProperty("maven_group")) {
                group = ForgixPlugin.rootProject.property("maven_group").toString();
            } else {
                ForgixPlugin.rootProject.getLogger().error("No \"maven_group\" property found! Please configure group manually!");
                ForgixPlugin.rootProject.getLogger().info("Check out on how to configure \"group\": " + "https://github.com/PacifistMC/Forgix#configuration");
            }
        }

        if (mergedJarName != null) {
            if (ForgixPlugin.rootProject.hasProperty("mod_version")) {
                if (ForgixPlugin.rootProject.hasProperty("archives_base_name")) {
                    mergedJarName = ForgixPlugin.rootProject.property("archives_base_name").toString() + "-" + ForgixPlugin.rootProject.property("mod_version").toString() + ".jar";
                } else {
                    ForgixPlugin.rootProject.getLogger().error("No \"archives_base_name\" property found! Please configure mergedJarName manually!");
                    ForgixPlugin.rootProject.getLogger().info("Check out on how to configure \"mergedJarName\": " + "https://github.com/PacifistMC/Forgix#configuration");
                }
            } else {
                ForgixPlugin.rootProject.getLogger().error("No \"mod_version\" property found! Please configure mergedJarName manually!");
                ForgixPlugin.rootProject.getLogger().info("Check out on how to configure \"mergedJarName\": " + "https://github.com/PacifistMC/Forgix#configuration");
            }
        }
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getMergedJarName() {
        return mergedJarName;
    }

    public void setMergedJarName(String mergedJarName) {
        if (FilenameUtils.isExtension(mergedJarName)) mergedJarName = mergedJarName + FilenameUtils.EXTENSION_SEPARATOR_STR + "jar";
        this.mergedJarName = mergedJarName;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    ForgeContainer forgeContainer;

    public ForgeContainer forge(Closure<ForgeContainer> closure) {
        forgeContainer = new ForgeContainer();
        ForgixPlugin.rootProject.configure(forgeContainer, closure);
        return forgeContainer;
    }

    public void setForgeContainer(ForgeContainer forgeContainer) {
        this.forgeContainer = forgeContainer;
    }

    public ForgeContainer getForgeContainer() {
        if (forgeContainer == null) forgeContainer = new ForgeContainer();
        return forgeContainer;
    }

    FabricContainer fabricContainer;

    public FabricContainer fabric(Closure<FabricContainer> closure) {
        fabricContainer = new FabricContainer();
        ForgixPlugin.rootProject.configure(fabricContainer, closure);
        return fabricContainer;
    }

    public void setFabricContainer(FabricContainer fabricContainer) {
        this.fabricContainer = fabricContainer;
    }

    public FabricContainer getFabricContainer() {
        if (fabricContainer == null) fabricContainer = new FabricContainer();
        return fabricContainer;
    }

    QuiltContainer quiltContainer;

    public QuiltContainer quilt(Closure<QuiltContainer> closure) {
        quiltContainer = new QuiltContainer();
        ForgixPlugin.rootProject.configure(quiltContainer, closure);
        return quiltContainer;
    }

    public void setQuiltContainer(QuiltContainer quiltContainer) {
        this.quiltContainer = quiltContainer;
    }

    public QuiltContainer getQuiltContainer() {
        if (quiltContainer == null) quiltContainer = new QuiltContainer();
        return quiltContainer;
    }

    List<CustomContainer> customContainers = new ArrayList<>();

    public CustomContainer custom(Closure<CustomContainer> closure) {
        CustomContainer customContainer = new CustomContainer();
        ForgixPlugin.rootProject.configure(customContainer, closure);
        if (customContainer.getProjectName() == null) {
            throw new IllegalStateException("For the custom loader you have to specify the \"projectName\"!\n" +
                    "Check out on how to configure \"projectName\": " + "https://github.com/PacifistMC/Forgix#configuration");
        }
        customContainers.add(customContainer);
        return customContainer;
    }

    public List<CustomContainer> getCustomContainers() {
        return customContainers;
    }

    public List<Forgix.Merge.CustomContainer> getForgixCustomContainers() {
        List<Forgix.Merge.CustomContainer> containers = new ArrayList<>();
        getCustomContainers().forEach(c -> containers.add(c.toForgixCustomContainter()));
        return containers;
    }

    public List<String> getRemoveDuplicates() {
        return removeDuplicates;
    }

    public void removeDuplicate(String duplicate) {
        if (removeDuplicates == null) removeDuplicates = new ArrayList<>();
        removeDuplicates.add(duplicate);
    }

    public void removeDuplicates(List<String> duplicates) {
        if (removeDuplicates == null) removeDuplicates = new ArrayList<>();
        removeDuplicates.addAll(duplicates);
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class ForgeContainer {
        String projectName = "forge";
        String jarLocation;
        Map<String, String> additionalRelocates;
        List<String> mixins;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getJarLocation() {
            return jarLocation;
        }

        public void setJarLocation(String jarLocation) {
            this.jarLocation = jarLocation;
        }

        public Map<String, String> getAdditionalRelocates() {
            return additionalRelocates;
        }

        public void additionalRelocate(String from, String to) {
            if (this.additionalRelocates == null) this.additionalRelocates = new java.util.HashMap<>();
            this.additionalRelocates.put(from, to);
        }

        public List<String> getMixins() {
            return mixins;
        }

        public void mixin(String mixin) {
            if (this.mixins == null) this.mixins = new java.util.ArrayList<>();
            this.mixins.add(mixin);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class FabricContainer {
        String projectName = "fabric";
        String jarLocation;
        Map<String, String> additionalRelocates;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getJarLocation() {
            return jarLocation;
        }

        public void setJarLocation(String jarLocation) {
            this.jarLocation = jarLocation;
        }

        public Map<String, String> getAdditionalRelocates() {
            return additionalRelocates;
        }

        public void additionalRelocate(String from, String to) {
            if (this.additionalRelocates == null) this.additionalRelocates = new java.util.HashMap<>();
            this.additionalRelocates.put(from, to);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class QuiltContainer {
        String projectName = "quilt";
        String jarLocation;
        Map<String, String> additionalRelocates;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getJarLocation() {
            return jarLocation;
        }

        public void setJarLocation(String jarLocation) {
            this.jarLocation = jarLocation;
        }

        public Map<String, String> getAdditionalRelocates() {
            return additionalRelocates;
        }

        public void additionalRelocate(String from, String to) {
            if (this.additionalRelocates == null) this.additionalRelocates = new java.util.HashMap<>();
            this.additionalRelocates.put(from, to);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class CustomContainer {
        String projectName;
        String jarLocation;
        Map<String, String> additionalRelocates;
        boolean remap = true;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getJarLocation() {
            return jarLocation;
        }

        public void setJarLocation(String jarLocation) {
            this.jarLocation = jarLocation;
        }

        public Map<String, String> getAdditionalRelocates() {
            return additionalRelocates;
        }

        public void _setAdditionalRelocates(Map<String, String> additionalRelocates) {
            this.additionalRelocates = additionalRelocates;
        }

        public void additionalRelocate(String from, String to) {
            if (this.additionalRelocates == null) this.additionalRelocates = new java.util.HashMap<>();
            this.additionalRelocates.put(from, to);
        }

        public void setRemap(boolean remap) {
            this.remap = remap;
        }

        Forgix.Merge.CustomContainer toForgixCustomContainter() {
            return new Forgix.Merge.CustomContainer(projectName, jarLocation, additionalRelocates, remap);
        }
    }
}
