package io.github.pacifistmc.forgix;

import fr.stevecohen.jarmanager.JarPacker;
import fr.stevecohen.jarmanager.JarUnpacker;
import io.github.pacifistmc.forgix.plugin.ForgixSplitExtension;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.pacifistmc.forgix.utils.FileUtils.*;

// This is the class that does the magic.
@SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "FieldCanBeLocal"})
public class Forgix {
    public static final String manifestVersionKey = "Forgix-Version";

    public static class Merge {
        public static class CustomContainer {
            public final String loaderName;
            public final String jarLocation;
            public Map<String, String> additionalRelocates;
            public final boolean remapMainPackage;

            /**
             * Initializes an instance of {@link CustomContainer} or CustomLoader
             * which is used for doing stuff with loaders that isn't supported by default in Forgix
             * @param loaderName The name of the loader
             * @param jarLocation The location of the jar
             * @param additionalRelocates A map of additional relocates/relocations/mappings
             */
            public CustomContainer(String loaderName, String jarLocation, Map<String, String> additionalRelocates, boolean remapMainPackage) {
                this.loaderName = loaderName;
                this.jarLocation = jarLocation;
                this.additionalRelocates = additionalRelocates;
                this.remapMainPackage = remapMainPackage;
            }

            void setAdditionalRelocates(Map<String, String> additionalRelocates) {
                this.additionalRelocates = additionalRelocates;
            }
        }

        private final String version = "1.2.2";
        public static Set<PosixFilePermission> perms;

        static {
            perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_WRITE);
            perms.add(PosixFilePermission.OTHERS_READ);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.GROUP_READ);
        }

        private File forgeJar;
        private Map<String, String> forgeRelocations;
        private List<String> forgeMixins;
        private File fabricJar;
        private Map<String, String> fabricRelocations;
        private File quiltJar;
        private Map<String, String> quiltRelocations;
        private final Map<CustomContainer, File> customContainerMap;
        private Map<CustomContainer, Map<File, File>> customContainerTemps;
        private final String group;
        private final File tempDir;
        private final String mergedJarName;
        private final List<String> removeDuplicates;

        private final Logger logger;
        private final Map<String, String> removeDuplicateRelocations = new HashMap<>();

        /**
         * Initializes a new instance of the {@link Merge} class for merging jar.
         * @param forgeJar The forge jar (can be null)
         * @param forgeRelocations The forge relocations/mappings (can be empty map)
         * @param forgeMixins The forge mixins that you can manually add if it's not automatically detected (can be empty list)
         * @param fabricJar The fabric jar (can be null)
         * @param fabricRelocations The fabric relocations/mappings (can be empty map)
         * @param quiltJar The quilt jar (can be null)
         * @param quiltRelocations The quilt relocations/mappings (can be empty map)
         * @param customContainerMap The custom containers (can be empty map)
         * @param group The group to merge
         * @param tempDir The temp directory to use for temporary files
         * @param mergedJarName The name of the merged jar
         * @param removeDuplicates The list of duplicates to remove (can be empty list)
         * @param logger The logger to use for logging
         */
        public Merge(@Nullable File forgeJar, Map<String, String> forgeRelocations, List<String> forgeMixins, @Nullable File fabricJar, Map<String, String> fabricRelocations, @Nullable File quiltJar, Map<String, String> quiltRelocations, Map<CustomContainer, File> customContainerMap, String group, File tempDir, String mergedJarName, List<String> removeDuplicates, Logger logger) {
            this.forgeJar = forgeJar;
            this.forgeRelocations = forgeRelocations;
            this.forgeMixins = forgeMixins;
            this.fabricJar = fabricJar;
            this.fabricRelocations = fabricRelocations;
            this.quiltJar = quiltJar;
            this.quiltRelocations = quiltRelocations;
            this.customContainerMap = customContainerMap;
            this.group = group;
            this.tempDir = tempDir;
            this.mergedJarName = mergedJarName;
            this.removeDuplicates = removeDuplicates;
            this.logger = logger;
        }

        /**
         * This is the main merge method
         *
         * @param returnIfExists If true, will return if the merged jar already exists
         * @return The merged jar file
         * @throws IOException If something went wrong
         */
        public File merge(boolean returnIfExists) throws IOException {
            File mergedJar = new File(tempDir, mergedJarName);
            if (mergedJar.exists()) {
                if (returnIfExists) return mergedJar;
                mergedJar.delete();
            }

            tempDir.mkdirs();
            if (forgeJar == null && fabricJar == null && quiltJar == null && customContainerMap.isEmpty()) {
                throw new IllegalArgumentException("No jars were provided.");
            }

            if (forgeJar != null && !forgeJar.exists()) {
                logger.warn("Forge jar does not exist! You can ignore this if you are not using forge.\nYou might want to change Forgix settings if something is wrong.");
            }

            if (fabricJar != null && !fabricJar.exists()) {
                logger.warn("Fabric jar does not exist! You can ignore this if you are not using fabric.\nYou might want to change Forgix settings if something is wrong.");
            }

            if (quiltJar != null && !quiltJar.exists()) {
                logger.warn("Quilt jar does not exist! You can ignore this if you are not using quilt.\nYou might want to change Forgix settings if something is wrong.");
            }

            for (Map.Entry<CustomContainer, File> entry : customContainerMap.entrySet()) {
                if (!entry.getValue().exists()) {
                    logger.warn(entry.getKey().loaderName + " jar does not exist! You can ignore this if you are not using custom containers.\nYou might want to change Forgix settings if something is wrong.");
                }
            }

            logger.info("\nForgix is still very new so refer any issues that you might encounter to\n" + "https://github.com/PacifistMC/Forgix/issues");

            logger.info("\nSettings:\n" +
                    "Forge: " + (forgeJar == null || !forgeJar.exists() ? "No\n" : "Yes\n") +
                    "Fabric: " + (fabricJar == null || !fabricJar.exists() ? "No\n" : "Yes\n") +
                    "Quilt: " + (quiltJar == null || !quiltJar.exists() ? "No\n" : "Yes\n") +
                    "Custom Containers: " + (customContainerMap.isEmpty() ? "No\n" : "Yes\n") +
                    "Group: " + group + "\n" +
                    "Merged Jar Name: " + mergedJarName + "\n"
            );

            remap();

            File fabricTemps = new File(tempDir, "fabric-temps");
            File forgeTemps = new File(tempDir, "forge-temps");
            File quiltTemps = new File(tempDir, "quilt-temps");

            customContainerTemps = new HashMap<>();
            for (Map.Entry<CustomContainer, File> entry : customContainerMap.entrySet()) {
                Map<File, File> temp = new HashMap<>();
                // The first file is the jar, the second file is the temps folder.
                temp.put(entry.getValue(), new File(tempDir, entry.getKey().loaderName + "-temps"));
                customContainerTemps.put(entry.getKey(), temp);
            }

            if (fabricTemps.exists()) FileUtils.deleteQuietly(fabricTemps);
            fabricTemps.mkdirs();

            if (forgeTemps.exists()) FileUtils.deleteQuietly(forgeTemps);
            forgeTemps.mkdirs();

            if (quiltTemps.exists()) FileUtils.deleteQuietly(quiltTemps);
            quiltTemps.mkdirs();

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getValue().exists()) FileUtils.deleteQuietly(entry2.getValue());
                    entry2.getValue().mkdirs();
                }
            }

            JarUnpacker jarUnpacker = new JarUnpacker();
            if (forgeJar != null && forgeJar.exists()) jarUnpacker.unpack(forgeJar.getAbsolutePath(), forgeTemps.getAbsolutePath());
            if (fabricJar != null && fabricJar.exists()) jarUnpacker.unpack(fabricJar.getAbsolutePath(), fabricTemps.getAbsolutePath());
            if (quiltJar != null && quiltJar.exists()) jarUnpacker.unpack(quiltJar.getAbsolutePath(), quiltTemps.getAbsolutePath());

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getKey().exists()) jarUnpacker.unpack(entry2.getKey().getAbsolutePath(), entry2.getValue().getAbsolutePath());
                }
            }

            File mergedTemps = new File(tempDir, "merged-temps");
            if (mergedTemps.exists()) FileUtils.deleteQuietly(mergedTemps);
            mergedTemps.mkdirs();

            Manifest mergedManifest = new Manifest();
            Manifest forgeManifest = new Manifest();
            Manifest fabricManifest = new Manifest();
            Manifest quiltManifest = new Manifest();
            List<Manifest> customContainerManifests = new ArrayList<>();

            FileInputStream fileInputStream = null;
            if (forgeJar != null && forgeJar.exists()) forgeManifest.read(fileInputStream = new FileInputStream(new File(forgeTemps, "META-INF/MANIFEST.MF")));
            if (fileInputStream != null) fileInputStream.close();
            if (fabricJar != null && fabricJar.exists()) fabricManifest.read(fileInputStream = new FileInputStream(new File(fabricTemps, "META-INF/MANIFEST.MF")));
            if (fileInputStream != null) fileInputStream.close();
            if (quiltJar != null && quiltJar.exists()) quiltManifest.read(fileInputStream = new FileInputStream(new File(quiltTemps, "META-INF/MANIFEST.MF")));
            if (fileInputStream != null) fileInputStream.close();

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    Manifest manifest = new Manifest();
                    if (entry2.getKey() != null && entry2.getKey().exists()) {
                        manifest.read(fileInputStream = new FileInputStream(new File(entry2.getValue(), "META-INF/MANIFEST.MF")));
                        customContainerManifests.add(manifest);
                    }
                    if (fileInputStream != null) fileInputStream.close();
                }
            }

            forgeManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
            fabricManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
            quiltManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));

            for (Manifest manifest : customContainerManifests) {
                manifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
            }

            if (mergedManifest.getMainAttributes().getValue("MixinConfigs") != null) {
                String value = mergedManifest.getMainAttributes().getValue("MixinConfigs");
                String[] mixins;
                List<String> remappedMixin = new ArrayList<>();

                if (value.contains(",")) {
                    mixins = value.split(",");
                } else {
                    mixins = new String[]{value};
                }

                for (String mixin : mixins) {
                    remappedMixin.add("forge-" + mixin);
                }

                mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", remappedMixin));
            }

            if (this.forgeMixins != null) {
                List<String> newForgeMixins = new ArrayList<>();
                for (String mixin : this.forgeMixins) {
                    newForgeMixins.add("forge-" + mixin);
                }
                this.forgeMixins = newForgeMixins;
                if (!forgeMixins.isEmpty()) mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
            }

            remapResources(forgeTemps, fabricTemps, quiltTemps);

            if (this.forgeMixins != null && mergedManifest.getMainAttributes().getValue("MixinConfigs") == null) {
                logger.debug("Couldn't detect forge mixins. You can ignore this if you are not using mixins with forge.\n" +
                        "If this is an issue then you can configure mixins manually\n" +
                        "Though we'll try to detect them automatically.\n");
                if (!forgeMixins.isEmpty()) {
                    logger.debug("Detected forge mixins: " + String.join(",", this.forgeMixins) + "\n");
                    mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
                }
            }

            mergedManifest.getMainAttributes().putValue(manifestVersionKey, version);

            if (forgeJar != null && forgeJar.exists()) new File(forgeTemps, "META-INF/MANIFEST.MF").delete();
            if (fabricJar != null && fabricJar.exists()) new File(fabricTemps, "META-INF/MANIFEST.MF").delete();
            if (quiltJar != null && quiltJar.exists()) new File(quiltTemps, "META-INF/MANIFEST.MF").delete();

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getKey() != null && entry2.getKey().exists()) new File(entry2.getValue(), "META-INF/MANIFEST.MF").delete();
                }
            }

            new File(metaInf(mergedTemps), "MANIFEST.MF").createNewFile();
            FileOutputStream outputStream = new FileOutputStream(new File(mergedTemps, "META-INF/MANIFEST.MF"));
            mergedManifest.write(outputStream);
            outputStream.close();

            if (forgeJar != null && forgeJar.exists()) FileUtils.copyDirectory(forgeTemps, mergedTemps);
            if (fabricJar != null && fabricJar.exists()) FileUtils.copyDirectory(fabricTemps, mergedTemps);
            if (quiltJar != null && quiltJar.exists()) FileUtils.copyDirectory(quiltTemps, mergedTemps);

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getKey() != null && entry2.getKey().exists()) FileUtils.copyDirectory(entry2.getValue(), mergedTemps);
                }
            }

            JarPacker jarPacker = new JarPacker();
            jarPacker.pack(mergedTemps.getAbsolutePath(), mergedJar.getAbsolutePath());

            File dupeTemps = new File(mergedTemps.getParentFile(), "duplicate-temps");
            if (dupeTemps.exists()) FileUtils.deleteQuietly(dupeTemps);

            setupDuplicates();

            removeDuplicate(mergedJar, new File(tempDir, mergedJarName + ".duplicate.remover"), mergedTemps);

            FileUtils.deleteQuietly(mergedTemps);

            jarUnpacker.unpack(mergedJar.getAbsolutePath(), mergedTemps.getAbsolutePath());

            removeDuplicateResources(mergedTemps);

            FileUtils.deleteQuietly(mergedJar);
            jarPacker.pack(mergedTemps.getAbsolutePath(), mergedJar.getAbsolutePath());

            try {
                Files.setPosixFilePermissions(mergedJar.toPath(), perms);
            } catch (UnsupportedOperationException | IOException | SecurityException ignored) { }

            FileUtils.deleteQuietly(mergedTemps);
            if (forgeJar != null && forgeJar.exists()) {
                FileUtils.deleteQuietly(forgeTemps);
                forgeJar.delete();
            }
            if (fabricJar != null && fabricJar.exists()) {
                FileUtils.deleteQuietly(fabricTemps);
                fabricJar.delete();
            }
            if (quiltJar != null && quiltJar.exists()) {
                FileUtils.deleteQuietly(quiltTemps);
                quiltJar.delete();
            }

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getKey() != null && entry2.getKey().exists()) {
                        FileUtils.deleteQuietly(entry2.getValue());
                        entry2.getKey().delete();
                    }
                }
            }

            if (dupeTemps.exists()) FileUtils.deleteQuietly(dupeTemps);

            return mergedJar;
        }

        /**
         * This is the method that remaps the bytecode
         * We do this remapping in order to not get any conflicts
         *
         * @throws IOException If something went wrong
         */
        private void remap() throws IOException {
            if (forgeJar != null && forgeJar.exists()) {
                File remappedForgeJar = new File(tempDir, "tempForgeInMerging.jar");
                if (remappedForgeJar.exists()) remappedForgeJar.delete();
                remappedForgeJar.createNewFile();

                List<Relocation> forgeRelocation = new ArrayList<>();
                forgeRelocation.add(new Relocation(group, "forge." + group));
                if (forgeRelocations != null) forgeRelocation.addAll(forgeRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

                AtomicReference<String> architectury = architecturyFetcher(forgeJar);;

                if (architectury.get() != null) forgeRelocation.add(new Relocation(architectury.get(), "forge." + architectury.get()));

                JarRelocator forgeRelocator = new JarRelocator(forgeJar, remappedForgeJar, forgeRelocation);
                forgeRelocator.run();

                forgeJar = remappedForgeJar;
            }

            if (fabricJar != null && fabricJar.exists()) {
                File remappedFabricJar = new File(tempDir, "tempFabricInMerging.jar");
                if (remappedFabricJar.exists()) remappedFabricJar.delete();
                remappedFabricJar.createNewFile();

                List<Relocation> fabricRelocation = new ArrayList<>();
                fabricRelocation.add(new Relocation(group, "fabric." + group));
                if (fabricRelocations != null) fabricRelocation.addAll(fabricRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

                AtomicReference<String> architectury = architecturyFetcher(fabricJar);

                if (architectury.get() != null) fabricRelocation.add(new Relocation(architectury.get(), "fabric." + architectury.get()));

                JarRelocator fabricRelocator = new JarRelocator(fabricJar, remappedFabricJar, fabricRelocation);
                fabricRelocator.run();

                fabricJar = remappedFabricJar;
            }

            if (quiltJar != null && quiltJar.exists()) {
                File remappedQuiltJar = new File(tempDir, "tempQuiltInMerging.jar");
                if (remappedQuiltJar.exists()) remappedQuiltJar.delete();
                remappedQuiltJar.createNewFile();

                List<Relocation> quiltRelocation = new ArrayList<>();
                quiltRelocation.add(new Relocation(group, "quilt." + group));
                if (quiltRelocations != null) quiltRelocation.addAll(quiltRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

                AtomicReference<String> architectury = architecturyFetcher(quiltJar);

                if (architectury.get() != null) quiltRelocation.add(new Relocation(architectury.get(), "quilt." + architectury.get()));

                JarRelocator quiltRelocator = new JarRelocator(quiltJar, remappedQuiltJar, quiltRelocation);
                quiltRelocator.run();

                quiltJar = remappedQuiltJar;
            }

            for (Map.Entry<CustomContainer, File> entry : customContainerMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().exists()) {
                    String name = entry.getKey().loaderName;
                    File remappedCustomJar = new File(tempDir, "tempCustomInMerging_" + name + ".jar");
                    if (remappedCustomJar.exists()) remappedCustomJar.delete();

                    List<Relocation> customRelocation = new ArrayList<>();
                    if (entry.getKey().remapMainPackage) customRelocation.add(new Relocation(group, name + "." + group));
                    if (entry.getKey().additionalRelocates != null) customRelocation.addAll(entry.getKey().additionalRelocates.entrySet().stream().map(entry1 -> new Relocation(entry1.getKey(), entry1.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

                    AtomicReference<String> architectury = architecturyFetcher(entry.getValue());

                    if (architectury.get() != null) customRelocation.add(new Relocation(architectury.get(), name + "." + architectury.get()));

                    JarRelocator customRelocator = new JarRelocator(entry.getValue(), remappedCustomJar, customRelocation);
                    customRelocator.run();

                    customContainerMap.replace(entry.getKey(), entry.getValue(), remappedCustomJar);
                }
            }
        }

        /**
         * This is the second remapping method
         * This basically remaps all resources such as mixins, manifestJars, etc.
         * This method also finds all the forge mixins for you if not detected
         *
         * @param forgeTemps  The extracted forge jar directory
         * @param fabricTemps The extracted fabric jar directory
         * @param quiltTemps  The extracted quilt jar directory
         * @throws IOException If something went wrong
         */
        private void remapResources(File forgeTemps, File fabricTemps, File quiltTemps) throws IOException {
            if (forgeRelocations == null) forgeRelocations = new HashMap<>();
            if (forgeJar != null && forgeJar.exists()) {
                for (File file : manifestJars(forgeTemps)) {
                    File remappedFile = new File(file.getParentFile(), "forge-" + file.getName());
                    forgeRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllPlatformServices(forgeTemps, group)) {
                    File remappedFile = new File(file.getParentFile(), "forge." + file.getName());
                    forgeRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                forgeMixins = new ArrayList<>();
                for (File file : listAllMixins(forgeTemps, false)) {
                    File remappedFile = new File(file.getParentFile(), "forge-" + file.getName());
                    forgeRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);

                    forgeMixins.add(remappedFile.getName());
                }

                for (File file : listAllRefmaps(forgeTemps)) {
                    File remappedFile = new File(file.getParentFile(), "forge-" + file.getName());
                    forgeRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                forgeRelocations.put(group, "forge." + group);
                forgeRelocations.put(group.replace(".", "/"), "forge/" + group.replace(".", "/"));
                replaceAllTextFiles(forgeTemps, forgeRelocations);
            }

            if (fabricRelocations == null) fabricRelocations = new HashMap<>();
            if (fabricJar != null && fabricJar.exists()) {
                for (File file : manifestJars(fabricTemps)) {
                    File remappedFile = new File(file.getParentFile(), "fabric-" + file.getName());
                    fabricRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllPlatformServices(fabricTemps, group)) {
                    File remappedFile = new File(file.getParentFile(), "fabric." + file.getName());
                    fabricRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllMixins(fabricTemps, true)) {
                    File remappedFile = new File(file.getParentFile(), "fabric-" + file.getName());
                    fabricRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllAccessWideners(fabricTemps)) {
                    File remappedFile = new File(file.getParentFile(), "fabric-" + file.getName());
                    fabricRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                fabricRelocations.put(group, "fabric." + group);
                fabricRelocations.put(group.replace(".", "/"), "fabric/" + group.replace(".", "/"));
                replaceAllTextFiles(fabricTemps, fabricRelocations);
            }

            if (quiltRelocations == null) quiltRelocations = new HashMap<>();
            if (quiltJar != null && quiltJar.exists()) {
                for (File file : manifestJars(quiltTemps)) {
                    File remappedFile = new File(file.getParentFile(), "quilt-" + file.getName());
                    quiltRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllPlatformServices(quiltTemps, group)) {
                    File remappedFile = new File(file.getParentFile(), "quilt." + file.getName());
                    quiltRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllMixins(quiltTemps, true)) {
                    File remappedFile = new File(file.getParentFile(), "quilt-" + file.getName());
                    quiltRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                for (File file : listAllAccessWideners(quiltTemps)) {
                    File remappedFile = new File(file.getParentFile(), "quilt-" + file.getName());
                    quiltRelocations.put(file.getName(), remappedFile.getName());
                    file.renameTo(remappedFile);
                }

                quiltRelocations.put(group, "quilt." + group);
                quiltRelocations.put(group.replace(".", "/"), "quilt/" + group.replace(".", "/"));
                replaceAllTextFiles(quiltTemps, quiltRelocations);
            }

            for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                    if (entry2.getKey() != null && entry2.getKey().exists()) {
                        if (entry.getKey().additionalRelocates == null) entry.getKey().setAdditionalRelocates(new HashMap<>());
                        File customTemps = entry2.getValue();
                        String name = entry.getKey().loaderName;

                        for (File file : manifestJars(customTemps)) {
                            File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                            entry.getKey().additionalRelocates.put(file.getName(), remappedFile.getName());
                            file.renameTo(remappedFile);
                        }

                        for (File file : listAllPlatformServices(customTemps, group)) {
                            File remappedFile = new File(file.getParentFile(), name + "." + file.getName());
                            entry.getKey().additionalRelocates.put(file.getName(), remappedFile.getName());
                            file.renameTo(remappedFile);
                        }

                        for (File file : listAllMixins(customTemps, true)) {
                            File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                            entry.getKey().additionalRelocates.put(file.getName(), remappedFile.getName());
                            file.renameTo(remappedFile);
                        }

                        for (File file : listAllAccessWideners(customTemps)) {
                            File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                            entry.getKey().additionalRelocates.put(file.getName(), remappedFile.getName());
                            file.renameTo(remappedFile);
                        }

                        if (entry.getKey().remapMainPackage) {
                            entry.getKey().additionalRelocates.put(group, name + "." + group);
                            entry.getKey().additionalRelocates.put(group.replace(".", "/"), name + "/" + group.replace(".", "/"));
                        }
                        for (File file : listAllTextFiles(customTemps)) {
                            FileInputStream fis = new FileInputStream(file);
                            Scanner scanner = new Scanner(fis);
                            StringBuilder sb = new StringBuilder();

                            while (scanner.hasNext()) {
                                String line = scanner.nextLine();
                                for (Map.Entry<String, String> entry3 : entry.getKey().additionalRelocates.entrySet()) {
                                    line = line.replace(entry3.getKey(), entry3.getValue());
                                }
                                sb.append(line).append("\n");
                            }

                            scanner.close();
                            fis.close();
                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(sb.toString().getBytes());
                            fos.flush();
                            fos.close();
                        }
                    }
                }
            }
        }


        Map<String, String> removeDuplicateRelocationResources = new HashMap<>();

        /**
         * Sets up the mappings for the duplicates
         */
        private void setupDuplicates() {
            if (removeDuplicates != null) {
                for (String duplicate : removeDuplicates) {
                    String duplicatePath = duplicate.replace(".", "/");

                    if (forgeJar != null && forgeJar.exists()) {
                        removeDuplicateRelocations.put("forge." + duplicate, duplicate);
                        removeDuplicateRelocationResources.put("forge/" + duplicatePath, duplicatePath);
                    }

                    if (fabricJar != null && fabricJar.exists()) {
                        removeDuplicateRelocations.put("fabric." + duplicate, duplicate);
                        removeDuplicateRelocationResources.put("fabric/" + duplicatePath, duplicatePath);
                    }

                    if (quiltJar != null && quiltJar.exists()) {
                        removeDuplicateRelocations.put("quilt." + duplicate, duplicate);
                        removeDuplicateRelocationResources.put("quilt/" + duplicatePath, duplicatePath);
                    }

                    for (Map.Entry<CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
                        for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                            if (entry2.getKey() != null && entry2.getKey().exists()) {
                                String name = entry.getKey().loaderName;
                                removeDuplicateRelocations.put(name + "." + duplicate, duplicate);
                                removeDuplicateRelocationResources.put(name + "/" + duplicatePath, duplicatePath);
                            }
                        }
                    }
                }

                removeDuplicateRelocationResources.putAll(removeDuplicateRelocations);
            }
        }

        /**
         * This method removes the duplicates specified in resources
         */
        private void removeDuplicateResources(File mergedTemps) throws IOException {
            if (removeDuplicates != null) {
                try (Stream<Path> pathStream = Files.list(new File(mergedTemps.getParentFile(), "duplicate-temps").toPath())) {
                    for (Path path : pathStream.collect(Collectors.toList())) {
                        File file = path.toFile();
                        if (file.isDirectory()) {
                            FileUtils.copyDirectory(file, mergedTemps);
                        } else {
                            FileUtils.copyFileToDirectory(file, mergedTemps);
                        }
                    }
                }

                replaceAllTextFiles(mergedTemps, removeDuplicateRelocationResources);
            }
        }

        /**
         * This method removes the duplicates specified
         */
        private void removeDuplicate(File mergedJar, File mergedOutputJar, File mergedTemps) throws IOException {
            // Have to do it this way cause there's a bug with jar-relocator where it doesn't accept duplicate values
            while (!removeDuplicateRelocations.isEmpty()) {
                Map.Entry<String, String> removeDuplicate = removeDuplicateRelocations.entrySet().stream().findFirst().get();
                try (ZipFile zipFile = new ZipFile(mergedJar)) {
                    try {
                        zipFile.extractFile(removeDuplicate.getValue().replace(".", "/") + "/", new File(mergedTemps.getParentFile(), "duplicate-temps" + "/").getPath());
                    } catch (ZipException e) {
                        if (!e.getType().equals(ZipException.Type.FILE_NOT_FOUND)) {
                            throw e;
                        }
                    }
                }

                if (mergedOutputJar.exists()) mergedOutputJar.delete();
                mergedOutputJar.createNewFile();

                List<Relocation> relocations = new ArrayList<>();

                relocations.add(new Relocation(removeDuplicate.getKey(), removeDuplicate.getValue()));

                JarRelocator jarRelocator = new JarRelocator(mergedJar, mergedOutputJar, relocations);
                jarRelocator.run();

                Files.move(mergedOutputJar.toPath(), mergedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

                if (mergedOutputJar.exists()) mergedOutputJar.delete();
                removeDuplicateRelocations.remove(removeDuplicate.getKey(), removeDuplicate.getValue());

                try (ZipFile zipFile = new ZipFile(mergedJar)) {
                    zipFile.extractFile(removeDuplicate.getValue().replace(".", "/") + "/", new File(mergedTemps.getParentFile(), "duplicate-temps" + File.separator + removeDuplicate.getValue() + File.separator).getPath());
                    if (removeDuplicateRelocations.containsValue(removeDuplicate.getValue())) {
                        zipFile.removeFile(removeDuplicate.getValue().replace(".", "/") + "/");
                    }
                }
            }
        }
    }




    public static class Split {
        private final File inputJar;
        private final File outputJar;
        private final String loader;
        private final File tempDir;

        public Split(File inputJar, File outputJar, String loader, File tempDir) {
            this.inputJar = inputJar;
            this.outputJar = outputJar;
            this.loader = loader;
            this.tempDir = tempDir;
        }

        public File split(boolean returnIfExists, Project project) throws IOException {
            if (outputJar.exists()) {
                if (returnIfExists) return outputJar;
                outputJar.delete();
            }

            tempDir.mkdirs();

            File tempInSplitting = new File(tempDir, FilenameUtils.getBaseName(inputJar.getName()) +
                    "inSplitting-temp-" + loader + FilenameUtils.getExtension(inputJar.getName()));

            File tempSplittingDir = new File(tempDir, FilenameUtils.getBaseName(inputJar.getName()) +
                    "inSplitting-temp-" + loader);

            if (tempSplittingDir.exists()) FileUtils.deleteQuietly(tempSplittingDir);
            tempSplittingDir.mkdirs();

            JarUnpacker jarUnpacker = new JarUnpacker();
            jarUnpacker.unpack(inputJar.getAbsolutePath(), tempSplittingDir.getAbsolutePath());

            Map<String, String> relocations = new HashMap<>();

            File loaderFolder = new File(tempSplittingDir, loader);
            if (loaderFolder.exists() && loaderFolder.isDirectory()) {
                File[] files = loaderFolder.listFiles();
                if (files != null) {
                    for (File directory : files) {
                        if (directory.isDirectory()) {
                            relocations.put(loader + "." + directory.getName(), directory.getName());
                        }
                    }
                }
            }

            tempSplittingDir.delete();
            remap(tempInSplitting, relocations);

            ForgixSplitExtension.getRelocated(project).putAll(relocations);
            relocations.forEach((key, value) -> relocations.put(key.replace(".", "/"), value));

            jarUnpacker.unpack(tempInSplitting.getAbsolutePath(), tempSplittingDir.getAbsolutePath());
            remapResources(tempSplittingDir, relocations);

            return new JarPacker().pack(tempSplittingDir.getAbsolutePath(), outputJar.getAbsolutePath());
        }

        private void remap(File outputJar, Map<String, String> relocations) throws IOException {
            if (outputJar.exists()) outputJar.delete();
            outputJar.createNewFile();

            List<Relocation> relocations_ = new ArrayList<>();

            for (Map.Entry<String, String> relocation : relocations.entrySet()) {
                relocations_.add(new Relocation(relocation.getKey(), relocation.getValue()));
            }

            JarRelocator jarRelocator = new JarRelocator(inputJar, outputJar, relocations_);
            jarRelocator.run();
        }

        private void remapResources(File directory, Map<String, String> relocations) throws IOException {
            replaceAllTextFiles(directory, relocations);
        }

        public static class MergeBack {
            private final File inputJar;
            private final Map<String, String> relocations;
            private final File tempDir;

            public MergeBack(File inputJar, Map<String, String> relocations, File tempDir) {
                this.inputJar = inputJar;
                this.tempDir = tempDir;

                this.relocations = new HashMap<>();
                for (Map.Entry<String, String> relocation : relocations.entrySet()) {
                    this.relocations.put(relocation.getValue(), relocation.getKey());
                }
            }

            public void mergeBack() throws IOException {
                tempDir.mkdirs();

                File tempJar = new File(tempDir, FilenameUtils.getBaseName(inputJar.getName()) +
                        "mergingBack-temp-" + FilenameUtils.getExtension(inputJar.getName()));

                remap(tempJar);

                File tempJarDir = new File(tempDir, FilenameUtils.getBaseName(inputJar.getName()) + "mergingBack-temp");
                if (tempJarDir.exists()) FileUtils.deleteQuietly(tempJarDir);
                tempJarDir.mkdirs();

                JarUnpacker jarUnpacker = new JarUnpacker();
                jarUnpacker.unpack(tempJar.getAbsolutePath(), tempJarDir.getAbsolutePath());

                relocations.forEach((key, value) -> relocations.put(key.replace(".", "/"), value));
                remapResources(tempJarDir);

                inputJar.delete();
                new JarPacker().pack(tempJarDir.getAbsolutePath(), inputJar.getAbsolutePath());
            }

            private void remap(File outputJar) throws IOException {
                if (outputJar.exists()) outputJar.delete();
                outputJar.createNewFile();

                List<Relocation> relocations_ = new ArrayList<>();

                for (Map.Entry<String, String> relocation : relocations.entrySet()) {
                    relocations_.add(new Relocation(relocation.getKey(), relocation.getValue()));
                }

                JarRelocator jarRelocator = new JarRelocator(inputJar, outputJar, relocations_);
                jarRelocator.run();
            }

            private void remapResources(File directory) throws IOException {
                replaceAllTextFiles(directory, relocations);
            }
        }
    }
}
