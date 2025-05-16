package io.github.pacifistmc.forgix.plugin.configurations;

import io.github.pacifistmc.forgix.plugin.ForgixGradlePlugin;
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

public class ForgixMergeConfiguration {
    private final Property<Boolean> silence;
    private final Property<Boolean> autoRun;
    private final Property<String> archiveBaseName;
    private final Property<String> archiveClassifier;
    private final Property<String> archiveVersion;
    private final Property<Directory> destinationDirectory;
    private final Map<String, MergeLoaderConfiguration> mergeConfigurations = new HashMap<>();
    public MultiversionConfiguration multiversionConfiguration;

    private static final Map<String, Consumer<ForgixMergeConfiguration>> LOADER_DEFAULT_METHOD_MAP = new HashMap<>();

    @Inject
    public ForgixMergeConfiguration(ObjectFactory objects) {
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
        return archiveBaseName.convention(ForgixGradlePlugin.rootProject.getName());
    }

    public Property<String> getArchiveClassifier() {
        return archiveClassifier.convention(String.join("-", mergeConfigurations.keySet()));
    }

    public Property<String> getArchiveVersion() {
        return archiveVersion.convention(ForgixGradlePlugin.rootProject.getVersion().toString());
    }

    public Property<Directory> getDestinationDirectory() {
        return destinationDirectory.convention(ForgixGradlePlugin.rootProject.getLayout().getBuildDirectory().dir("forgix"));
    }

    // Fabric

    public void fabric(Action<? super MergeLoaderConfiguration> action) {
        merge("fabric", action);
    }
    public void fabric() {
        fabric(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("fabric", _ -> ForgixGradlePlugin.settings.fabric());
    }

    // Forge

    public void forge(Action<? super MergeLoaderConfiguration> action) {
        merge("forge", action);
    }
    public void forge() {
        forge(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("forge", _ -> ForgixGradlePlugin.settings.forge());
    }

    // Quilt

    public void quilt(Action<? super MergeLoaderConfiguration> action) {
        merge("quilt", action);
    }
    public void quilt() {
        quilt(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("quilt", _ -> ForgixGradlePlugin.settings.quilt());
    }

    // NeoForge

    public void neoforge(Action<? super MergeLoaderConfiguration> action) {
        merge("neoforge", action);
    }
    public void neoforge() {
        neoforge(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("neoforge", _ -> ForgixGradlePlugin.settings.neoforge());
    }

    // LiteLoader

    public void liteloader(Action<? super MergeLoaderConfiguration> action) {
        merge("liteloader", action);
    }
    public void liteloader() {
        liteloader(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("liteloader", _ -> ForgixGradlePlugin.settings.liteloader());
    }

    // Rift

    public void rift(Action<? super MergeLoaderConfiguration> action) {
        merge("rift", action);
    }
    public void rift() {
        rift(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("rift", _ -> ForgixGradlePlugin.settings.rift());
    }

    // General plugin project

    public void plugin(Action<? super MergeLoaderConfiguration> action) {
        merge("plugin", action);
    }
    public void plugin() {
        plugin(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("plugin", _ -> ForgixGradlePlugin.settings.plugin());
    }

    // Bukkit

    public void bukkit(Action<? super MergeLoaderConfiguration> action) {
        merge("bukkit", action);
    }
    public void bukkit() {
        bukkit(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("bukkit", _ -> ForgixGradlePlugin.settings.bukkit());
    }

    // Spigot

    public void spigot(Action<? super MergeLoaderConfiguration> action) {
        merge("spigot", action);
    }
    public void spigot() {
        spigot(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("spigot", _ -> ForgixGradlePlugin.settings.spigot());
    }

    // Paper

    public void paper(Action<? super MergeLoaderConfiguration> action) {
        merge("papermc", action);
    }
    public void paper() {
        paper(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("papermc", _ -> ForgixGradlePlugin.settings.paper());
    }

    // Sponge

    public void sponge(Action<? super MergeLoaderConfiguration> action) {
        merge("sponge", action);
    }
    public void sponge() {
        sponge(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("sponge", _ -> ForgixGradlePlugin.settings.sponge());
    }

    // Foila

    public void foila(Action<? super MergeLoaderConfiguration> action) {
        merge("foila", action);
    }
    public void foila() {
        foila(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("foila", _ -> ForgixGradlePlugin.settings.foila());
    }

    // BungeeCord

    public void bungeecoord(Action<? super MergeLoaderConfiguration> action) {
        merge("bungeecord", action);
    }
    public void bungeecoord() {
        bungeecoord(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("bungeecord", _ -> ForgixGradlePlugin.settings.bungeecoord());
    }

    // Waterfall

    public void waterfall(Action<? super MergeLoaderConfiguration> action) {
        merge("waterfall", action);
    }
    public void waterfall() {
        waterfall(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("waterfall", _ -> ForgixGradlePlugin.settings.waterfall());
    }

    // Velocity

    public void velocity(Action<? super MergeLoaderConfiguration> action) {
        merge("velocity", action);
    }
    public void velocity() {
        velocity(_ -> {});
    }
    static {
        LOADER_DEFAULT_METHOD_MAP.put("velocity", _ -> ForgixGradlePlugin.settings.velocity());
    }

    public void merge(String name, Action<? super MergeLoaderConfiguration> action) {
        Action<? super Project> afterEvaluateAction = _ -> {
            var config = new MergeLoaderConfiguration(getObjects());

            if (ForgixGradlePlugin.rootProject.getAllprojects().stream().noneMatch(project -> project.getName().equalsIgnoreCase(name))
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
        if (ForgixGradlePlugin.rootProject.getState().getExecuted()) {
            afterEvaluateAction.execute(ForgixGradlePlugin.rootProject);
            return;
        }
        ForgixGradlePlugin.rootProject.afterEvaluate(afterEvaluateAction);
    }
    public void merge(String name) {
        merge(name, _ -> {});
    }

    public Map<String, MergeLoaderConfiguration> getMergeConfigurations() {
        return mergeConfigurations;
    }

    public void setupDefaultConfiguration() {
        LOADER_DEFAULT_METHOD_MAP.entrySet().stream()
                .filter(entry -> ForgixGradlePlugin.rootProject.getAllprojects().stream().anyMatch(project -> project.getName().equalsIgnoreCase(entry.key)))
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
        Action<? super Project> afterEvaluateAction = _ -> action.execute(multiversionConfiguration = new MultiversionConfiguration(getObjects()));
        if (ForgixGradlePlugin.rootProject.getState().getExecuted()) {
            afterEvaluateAction.execute(ForgixGradlePlugin.rootProject);
            return;
        }
        ForgixGradlePlugin.rootProject.afterEvaluate(afterEvaluateAction);
    }

    public static class MultiversionConfiguration {
        @SuppressWarnings("FieldMayBeFinal") // We use afterEvaluate so this can't be final
        private FileCollection inputJars;

        @Inject
        public MultiversionConfiguration(ObjectFactory objects) {
            this.inputJars = objects.fileCollection();
        }

        public FileCollection getInputJars() {
            return inputJars;
        }
    }

    // Internal Gradle stuff

    @Inject
    protected ObjectFactory getObjects() {
        throw new UnsupportedOperationException();
    }
}