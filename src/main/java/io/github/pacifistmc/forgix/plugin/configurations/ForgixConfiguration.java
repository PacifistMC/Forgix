package io.github.pacifistmc.forgix.plugin.configurations;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ForgixConfiguration {
    private final Property<Boolean> silence;
    private final Property<Boolean> autoRun;
    private final Property<String> archiveBaseName;
    private final Property<String> archiveClassifier;
    private final Property<String> archiveVersion;
    private final Property<Directory> destinationDirectory;
    private final Map<String, MergeLoaderConfiguration> mergeConfigurations = new HashMap<>();
    public MultiversionConfiguration multiversionConfiguration;

    private final Map<String, Consumer<ForgixConfiguration>> LOADER_DEFAULT_METHOD_MAP = new HashMap<>();
    private final Project rootProject;

    @Inject
    public ForgixConfiguration(ObjectFactory objects, Project project) {
        this.rootProject = project.rootProject;
        this.silence = objects.property(Boolean.class);
        this.autoRun = objects.property(Boolean.class);
        this.archiveBaseName = objects.property(String.class);
        this.archiveClassifier = objects.property(String.class);
        this.archiveVersion = objects.property(String.class);
        this.destinationDirectory = objects.directoryProperty();
    }

    public Property<Boolean> getSilence() {
        return silence.convention(false);
    }

    public Property<Boolean> getAutoRun() {
        return autoRun.convention(false);
    }

    public Property<String> getArchiveBaseName() {
        return archiveBaseName.convention(rootProject.getName());
    }

    public Property<String> getArchiveClassifier() {
        return archiveClassifier.convention(String.join("-", mergeConfigurations.keySet()));
    }

    public Property<String> getArchiveVersion() {
        return archiveVersion.convention(rootProject.getVersion().toString());
    }

    public Property<Directory> getDestinationDirectory() {
        return destinationDirectory.convention(rootProject.getLayout().getBuildDirectory().dir("forgix"));
    }

    // Fabric

    public void fabric(Action<? super MergeLoaderConfiguration> action) {
        merge("fabric", action);
    }
    public void fabric() {
        fabric(_ -> {});
    }

    // Forge

    public void forge(Action<? super MergeLoaderConfiguration> action) {
        merge("forge", action);
    }
    public void forge() {
        forge(_ -> {});
    }

    // Quilt

    public void quilt(Action<? super MergeLoaderConfiguration> action) {
        merge("quilt", action);
    }
    public void quilt() {
        quilt(_ -> {});
    }

    // NeoForge

    public void neoforge(Action<? super MergeLoaderConfiguration> action) {
        merge("neoforge", action);
    }
    public void neoforge() {
        neoforge(_ -> {});
    }

    // LiteLoader

    public void liteloader(Action<? super MergeLoaderConfiguration> action) {
        merge("liteloader", action);
    }
    public void liteloader() {
        liteloader(_ -> {});
    }

    // Rift

    public void rift(Action<? super MergeLoaderConfiguration> action) {
        merge("rift", action);
    }
    public void rift() {
        rift(_ -> {});
    }

    // General plugin project

    public void plugin(Action<? super MergeLoaderConfiguration> action) {
        merge("plugin", action);
    }
    public void plugin() {
        plugin(_ -> {});
    }

    // Bukkit

    public void bukkit(Action<? super MergeLoaderConfiguration> action) {
        merge("bukkit", action);
    }
    public void bukkit() {
        bukkit(_ -> {});
    }

    // Spigot

    public void spigot(Action<? super MergeLoaderConfiguration> action) {
        merge("spigot", action);
    }
    public void spigot() {
        spigot(_ -> {});
    }

    // Paper

    public void paper(Action<? super MergeLoaderConfiguration> action) {
        merge("papermc", action);
    }
    public void paper() {
        paper(_ -> {});
    }

    // Sponge

    public void sponge(Action<? super MergeLoaderConfiguration> action) {
        merge("sponge", action);
    }
    public void sponge() {
        sponge(_ -> {});
    }

    // Foila

    public void foila(Action<? super MergeLoaderConfiguration> action) {
        merge("foila", action);
    }
    public void foila() {
        foila(_ -> {});
    }

    // BungeeCord

    public void bungeecoord(Action<? super MergeLoaderConfiguration> action) {
        merge("bungeecord", action);
    }
    public void bungeecoord() {
        bungeecoord(_ -> {});
    }

    // Waterfall

    public void waterfall(Action<? super MergeLoaderConfiguration> action) {
        merge("waterfall", action);
    }
    public void waterfall() {
        waterfall(_ -> {});
    }

    // Velocity

    public void velocity(Action<? super MergeLoaderConfiguration> action) {
        merge("velocity", action);
    }
    public void velocity() {
        velocity(_ -> {});
    }

    // When adding a new loader remember to update the LOADER_DEFAULT_METHOD_MAP

    public void merge(String name, Action<? super MergeLoaderConfiguration> action) {
        Action<? super Project> afterEvaluateAction = _ -> {
            var config = new MergeLoaderConfiguration(getObjects());

            if (rootProject.getAllprojects().stream().noneMatch(project -> project.getName().equalsIgnoreCase(name))
                    && (!config.getInputJar().isPresent() || !config.getInputJar().getAsFile().isPresent() || !config.getInputJar().getAsFile().get().exists())) {
                throw new IllegalArgumentException("""
                        Project with name ${name} does not exist and unable to detect input jar.
                        Please do something like:
                        ```build.gradle
                        forgix {
                            // ...
                            merge("<actual existing project name>") // no configuration, will try to detect the best input jar based on heuristics
                            // or
                            merge("<name>") {
                                inputJar = ... // your input jar file here
                            }
                            // ...
                        }
                        ```""");
            }

            action.execute(config);
            mergeConfigurations.put(name, config);
        };

        // If the project has already been evaluated, execute the action immediately
        if (rootProject.getState().getExecuted()) {
            afterEvaluateAction.execute(rootProject);
            return;
        }
        rootProject.afterEvaluate(afterEvaluateAction);
    }
    public void merge(String name) {
        merge(name, _ -> {});
    }

    public Map<String, MergeLoaderConfiguration> getMergeConfigurations() {
        return mergeConfigurations;
    }

    public void setupDefaultConfiguration() {
        LOADER_DEFAULT_METHOD_MAP.put("fabric", _ -> fabric());
        LOADER_DEFAULT_METHOD_MAP.put("forge", _ -> forge());
        LOADER_DEFAULT_METHOD_MAP.put("quilt", _ -> quilt());
        LOADER_DEFAULT_METHOD_MAP.put("neoforge", _ -> neoforge());
        LOADER_DEFAULT_METHOD_MAP.put("liteloader", _ -> liteloader());
        LOADER_DEFAULT_METHOD_MAP.put("rift", _ -> rift());
        LOADER_DEFAULT_METHOD_MAP.put("plugin", _ -> plugin());
        LOADER_DEFAULT_METHOD_MAP.put("bukkit", _ -> bukkit());
        LOADER_DEFAULT_METHOD_MAP.put("spigot", _ -> spigot());
        LOADER_DEFAULT_METHOD_MAP.put("papermc", _ -> paper());
        LOADER_DEFAULT_METHOD_MAP.put("sponge", _ -> sponge());
        LOADER_DEFAULT_METHOD_MAP.put("foila", _ -> foila());
        LOADER_DEFAULT_METHOD_MAP.put("bungeecord", _ -> bungeecoord());
        LOADER_DEFAULT_METHOD_MAP.put("waterfall", _ -> waterfall());
        LOADER_DEFAULT_METHOD_MAP.put("velocity", _ -> velocity());
        LOADER_DEFAULT_METHOD_MAP.entrySet().stream()
                .filter(entry -> rootProject.getAllprojects().stream().anyMatch(project -> project.getName().equalsIgnoreCase(entry.key)))
                .map(Map.Entry::getValue)
                .forEach(consumer -> consumer.accept(this));
    }

    public static class MergeLoaderConfiguration {
        @SuppressWarnings("FieldMayBeFinal") // We use afterEvaluate so this can't be final
        private RegularFileProperty inputJar;

        @Inject
        public MergeLoaderConfiguration(ObjectFactory objects) {
            this.inputJar = objects.fileProperty();
        }

        public RegularFileProperty getInputJar() {
            return inputJar;
        }
    }

    // Multiversion stuff

    public void multiversion(Action<? super MultiversionConfiguration> action) {
        Action<? super Project> afterEvaluateAction = _ -> action.execute(multiversionConfiguration = new MultiversionConfiguration(getObjects(), rootProject));
        if (rootProject.getState().getExecuted()) {
            afterEvaluateAction.execute(rootProject);
            return;
        }
        rootProject.afterEvaluate(afterEvaluateAction);
    }

    public static class MultiversionConfiguration {
        private final Project rootProject;
        private final Property<String> archiveBaseName;
        private final Property<String> archiveClassifier;
        private final Property<String> archiveVersion;
        private final Property<Directory> destinationDirectory;

		@SuppressWarnings("FieldMayBeFinal") // We use afterEvaluate so this can't be final
        private Property<FileCollection> inputJars;

        @Inject
        public MultiversionConfiguration(ObjectFactory objects, Project rootProject) {
            this.rootProject = rootProject;
            this.inputJars = objects.property(FileCollection.class);
            this.archiveBaseName = objects.property(String.class);
            this.archiveClassifier = objects.property(String.class);
            this.archiveVersion = objects.property(String.class);
            this.destinationDirectory = objects.directoryProperty();
		}

        public Property<String> getArchiveBaseName() {
            return archiveBaseName.convention(rootProject.getName());
        }

        public Property<String> getArchiveClassifier() {
            return archiveClassifier.convention("multi");
        }

        public Property<String> getArchiveVersion() {
            return archiveVersion.convention(rootProject.getVersion().toString());
        }

        public Property<Directory> getDestinationDirectory() {
            return destinationDirectory.convention(rootProject.getLayout().getBuildDirectory().dir("forgix/multiversion"));
        }

        public Property<FileCollection> getInputJars() {
            return inputJars;
        }
    }

    // Internal Gradle stuff

    @Inject
    protected ObjectFactory getObjects() {
        throw new UnsupportedOperationException();
    }
}