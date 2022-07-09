package io.github.pacifistmc.forgix;

import fr.stevecohen.jarmanager.JarPacker;
import fr.stevecohen.jarmanager.JarUnpacker;
import io.github.pacifistmc.forgix.plugin.ForgixExtension;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static io.github.pacifistmc.forgix.utils.FileUtils.*;

// This is the class that does the magic.
@SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "FieldCanBeLocal"})
public class Forgix {
    private final String version = "1.2.0";
    public static Set<PosixFilePermission> perms;

    static {
        perms = new HashSet<>();
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_READ);
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
    @SuppressWarnings("FieldMayBeFinal")
    private Map<ForgixExtension.CustomContainer, File> customContainerMap;
    private Map<ForgixExtension.CustomContainer, Map<File, File>> customContainerTemps;
    private final String group;
    private final File tempDir;
    private final String mergedJarName;

    private final Logger logger;

    public Forgix(@Nullable File forgeJar, Map<String, String> forgeRelocations, List<String> forgeMixins, @Nullable File fabricJar, Map<String, String> fabricRelocations, @Nullable File quiltJar, Map<String, String> quiltRelocations, Map<ForgixExtension.CustomContainer, File> customContainerMap, String group, File tempDir, String mergedJarName, Logger logger) {
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
        this.logger = logger;
    }

    /**
     * This is the main merge method
     *
     * @return The merged jar file
     * @throws IOException If something went wrong
     */
    public File merge() throws IOException {
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

        for (Map.Entry<ForgixExtension.CustomContainer, File> entry : customContainerMap.entrySet()) {
            if (!entry.getValue().exists()) {
                logger.warn(entry.getKey().getProjectName() + " jar does not exist! You can ignore this if you are not using custom containers.\nYou might want to change Forgix settings if something is wrong.");
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

        File mergedJar = new File(tempDir, mergedJarName);
        if (mergedJar.exists()) mergedJar.delete();

        File fabricTemps = new File(tempDir, "fabric-temps");
        File forgeTemps = new File(tempDir, "forge-temps");
        File quiltTemps = new File(tempDir, "quilt-temps");

        customContainerTemps = new HashMap<>();
        for (Map.Entry<ForgixExtension.CustomContainer, File> entry : customContainerMap.entrySet()) {
            Map<File, File> temp = new HashMap<>();
            // The first file is the jar, the second file is the temps folder.
            temp.put(entry.getValue(), new File(tempDir, entry.getKey().getProjectName() + "-temps"));
            customContainerTemps.put(entry.getKey(), temp);
        }

        if (fabricTemps.exists()) FileUtils.deleteQuietly(fabricTemps);
        fabricTemps.mkdirs();

        if (forgeTemps.exists()) FileUtils.deleteQuietly(forgeTemps);
        forgeTemps.mkdirs();

        if (quiltTemps.exists()) FileUtils.deleteQuietly(quiltTemps);
        quiltTemps.mkdirs();

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (entry2.getValue().exists()) FileUtils.deleteQuietly(entry2.getValue());
                entry2.getValue().mkdirs();
            }
        }

        JarUnpacker jarUnpacker = new JarUnpacker();
        if (forgeJar != null && forgeJar.exists()) jarUnpacker.unpack(forgeJar.getAbsolutePath(), forgeTemps.getAbsolutePath());
        if (fabricJar != null && fabricJar.exists()) jarUnpacker.unpack(fabricJar.getAbsolutePath(), fabricTemps.getAbsolutePath());
        if (quiltJar != null && quiltJar.exists()) jarUnpacker.unpack(quiltJar.getAbsolutePath(), quiltTemps.getAbsolutePath());

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
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

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
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

        mergedManifest.getMainAttributes().putValue("Forgix-Version", version);

        if (forgeJar != null && forgeJar.exists()) new File(forgeTemps, "META-INF/MANIFEST.MF").delete();
        if (fabricJar != null && fabricJar.exists()) new File(fabricTemps, "META-INF/MANIFEST.MF").delete();
        if (quiltJar != null && quiltJar.exists()) new File(quiltTemps, "META-INF/MANIFEST.MF").delete();

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
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

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (entry2.getKey() != null && entry2.getKey().exists()) FileUtils.copyDirectory(entry2.getValue(), mergedTemps);
            }
        }

        JarPacker jarPacker = new JarPacker();
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

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (entry2.getKey() != null && entry2.getKey().exists()) {
                    FileUtils.deleteQuietly(entry2.getValue());
                    entry2.getKey().delete();
                }
            }
        }

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
            if (forgeRelocations != null)
                forgeRelocation.addAll(forgeRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            AtomicReference<String> architectury = new AtomicReference<>();
            architectury.set(null);

            JarFile jarFile = new JarFile(forgeJar);
            jarFile.stream().forEach(jarEntry -> {
                if (jarEntry.isDirectory()) {
                    if (jarEntry.getName().startsWith("architectury_inject")) {
                        architectury.set(jarEntry.getName());
                    }
                } else {
                    String firstDirectory = getFirstDirectory(jarEntry.getName());
                    if (firstDirectory.startsWith("architectury_inject")) {
                        architectury.set(firstDirectory);
                    }
                }
            });
            jarFile.close();

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
            if (fabricRelocations != null)
                fabricRelocation.addAll(fabricRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            AtomicReference<String> architectury = new AtomicReference<>();
            architectury.set(null);

            JarFile jarFile = new JarFile(fabricJar);
            jarFile.stream().forEach(jarEntry -> {
                if (jarEntry.isDirectory()) {
                    if (jarEntry.getName().startsWith("architectury_inject")) {
                        architectury.set(jarEntry.getName());
                    }
                } else {
                    String firstDirectory = getFirstDirectory(jarEntry.getName());
                    if (firstDirectory.startsWith("architectury_inject")) {
                        architectury.set(firstDirectory);
                    }
                }
            });
            jarFile.close();

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
            if (quiltRelocations != null)
                quiltRelocation.addAll(quiltRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            AtomicReference<String> architectury = new AtomicReference<>();
            architectury.set(null);

            JarFile jarFile = new JarFile(quiltJar);
            jarFile.stream().forEach(jarEntry -> {
                if (jarEntry.isDirectory()) {
                    if (jarEntry.getName().startsWith("architectury_inject")) {
                        architectury.set(jarEntry.getName());
                    }
                } else {
                    String firstDirectory = getFirstDirectory(jarEntry.getName());
                    if (firstDirectory.startsWith("architectury_inject")) {
                        architectury.set(firstDirectory);
                    }
                }
            });

            if (architectury.get() != null) quiltRelocation.add(new Relocation(architectury.get(), "quilt." + architectury.get()));

            JarRelocator quiltRelocator = new JarRelocator(quiltJar, remappedQuiltJar, quiltRelocation);
            quiltRelocator.run();

            quiltJar = remappedQuiltJar;
        }

        for (Map.Entry<ForgixExtension.CustomContainer, File> entry : customContainerMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().exists()) {
                String name = entry.getKey().getProjectName();
                File remappedCustomJar = new File(tempDir, "tempCustomInMerging_" + name + ".jar");
                if (remappedCustomJar.exists()) remappedCustomJar.delete();

                List<Relocation> customRelocation = new ArrayList<>();
                customRelocation.add(new Relocation(group, name + "." + group));
                if (entry.getKey().getAdditionalRelocates() != null)
                    customRelocation.addAll(entry.getKey().getAdditionalRelocates().entrySet().stream().map(entry1 -> new Relocation(entry1.getKey(), entry1.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

                AtomicReference<String> architectury = new AtomicReference<>();
                architectury.set(null);

                JarFile jarFile = new JarFile(entry.getValue());
                jarFile.stream().forEach(jarEntry -> {
                    if (jarEntry.isDirectory()) {
                        if (jarEntry.getName().startsWith("architectury_inject")) {
                            architectury.set(jarEntry.getName());
                        }
                    } else {
                        String firstDirectory = getFirstDirectory(jarEntry.getName());
                        if (firstDirectory.startsWith("architectury_inject")) {
                            architectury.set(firstDirectory);
                        }
                    }
                });

                if (architectury.get() != null)
                    customRelocation.add(new Relocation(architectury.get(), name + "." + architectury.get()));

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
     * @param forgeTemps The extracted forge jar directory
     * @param fabricTemps The extracted fabric jar directory
     * @param quiltTemps The extracted quilt jar directory
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
            for (File file : listAllTextFiles(forgeTemps)) {
                FileInputStream fis = new FileInputStream(file);
                Scanner scanner = new Scanner(fis);
                StringBuilder sb = new StringBuilder();

                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    for (Map.Entry<String, String> entry : forgeRelocations.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
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

        if (fabricRelocations == null) fabricRelocations = new HashMap<>();
        if (fabricJar != null && fabricJar.exists()) {
            for (File file : manifestJars(fabricTemps)) {
                File remappedFile = new File(file.getParentFile(), "fabric-" + file.getName());
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
            for (File file : listAllTextFiles(fabricTemps)) {
                FileInputStream fis = new FileInputStream(file);
                Scanner scanner = new Scanner(fis);
                StringBuilder sb = new StringBuilder();

                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    for (Map.Entry<String, String> entry : fabricRelocations.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
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

        if (quiltRelocations == null) quiltRelocations = new HashMap<>();
        if (quiltJar != null && quiltJar.exists()) {
            for (File file : manifestJars(quiltTemps)) {
                File remappedFile = new File(file.getParentFile(), "quilt-" + file.getName());
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
            for (File file : listAllTextFiles(quiltTemps)) {
                FileInputStream fis = new FileInputStream(file);
                Scanner scanner = new Scanner(fis);
                StringBuilder sb = new StringBuilder();

                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    for (Map.Entry<String, String> entry : quiltRelocations.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
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

        for (Map.Entry<ForgixExtension.CustomContainer, Map<File, File>> entry : customContainerTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (entry2.getKey() != null && entry2.getKey().exists()) {
                    if (entry.getKey().getAdditionalRelocates() == null) entry.getKey()._setAdditionalRelocates(new HashMap<>());
                    File customTemps = entry2.getValue();
                    String name = entry.getKey().getProjectName();

                    for (File file : manifestJars(customTemps)) {
                        File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                        entry.getKey().getAdditionalRelocates().put(file.getName(), remappedFile.getName());
                        file.renameTo(remappedFile);
                    }

                    for (File file : listAllMixins(customTemps, true)) {
                        File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                        entry.getKey().getAdditionalRelocates().put(file.getName(), remappedFile.getName());
                        file.renameTo(remappedFile);
                    }

                    for (File file : listAllAccessWideners(customTemps)) {
                        File remappedFile = new File(file.getParentFile(), name + "-" + file.getName());
                        entry.getKey().getAdditionalRelocates().put(file.getName(), remappedFile.getName());
                        file.renameTo(remappedFile);
                    }

                    entry.getKey().getAdditionalRelocates().put(group, name + "." + group);
                    entry.getKey().getAdditionalRelocates().put(group.replace(".", "/"), name + "/" + group.replace(".", "/"));
                    for (File file : listAllTextFiles(customTemps)) {
                        FileInputStream fis = new FileInputStream(file);
                        Scanner scanner = new Scanner(fis);
                        StringBuilder sb = new StringBuilder();

                        while (scanner.hasNext()) {
                            String line = scanner.nextLine();
                            for (Map.Entry<String, String> entry3 : entry.getKey().getAdditionalRelocates().entrySet()) {
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

}
