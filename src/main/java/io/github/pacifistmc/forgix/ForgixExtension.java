package io.github.pacifistmc.forgix;

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

// I couldn't find any good resources on how to do this, so I just went with it and wrote a lot of dumb code.
@SuppressWarnings("unused")
public class ForgixExtension {
    private String group = ForgixPlugin.rootProject.getGroup().toString();
    private String mergedJarName = ForgixPlugin.rootProject.getName() + "-" + ForgixPlugin.rootProject.getVersion().toString() + ".jar";

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
        this.mergedJarName = mergedJarName;
    }

    private ForgeContainer forgeContainer;

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

    private FabricContainer fabricContainer;

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

    private QuiltContainer quiltContainer;

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

    class ForgeContainer {
        private String projectName = "forge";
        private String jarLocation;
        private Map<String, String> additionalRelocates;
        private List<String> mixins;

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

        public void setAdditionalRelocates(Map<String, String> additionalRelocates) {
            this.additionalRelocates = additionalRelocates;
        }

        public List<String> getMixins() {
            return mixins;
        }

        public void setMixins(List<String> mixins) {
            this.mixins = mixins;
        }
    }

    class FabricContainer {
        private String projectName = "fabric";
        private String jarLocation;
        private Map<String, String> additionalRelocates;

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

        public void setAdditionalRelocates(Map<String, String> additionalRelocates) {
            this.additionalRelocates = additionalRelocates;
        }
    }

    class QuiltContainer {
        private String projectName = "quilt";
        private String jarLocation;
        private Map<String, String> additionalRelocates;

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

        public void setAdditionalRelocates(Map<String, String> additionalRelocates) {
            this.additionalRelocates = additionalRelocates;
        }
    }
}
