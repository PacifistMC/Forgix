package io.github.pacifistmc.forgix;

import fr.stevecohen.jarmanager.JarPacker;
import fr.stevecohen.jarmanager.JarUnpacker;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.apache.commons.io.FileUtils;

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
    private final String version = "1.0";

    private File forgeJar;
    private Map<String, String> forgeRelocations;
    private List<String> forgeMixins;
    private File fabricJar;
    private Map<String, String> fabricRelocations;
    private File quiltJar;
    private Map<String, String> quiltRelocations;
    private final String group;
    private final File tempDir;
    private final String mergedJarName;

    public Forgix(@Nullable File forgeJar, @Nullable Map<String, String> forgeRelocations, @Nullable List<String> forgeMixins, @Nullable File fabricJar, Map<String, String> fabricRelocations, @Nullable File quiltJar, Map<String, String> quiltRelocations, String group, File tempDir, String mergedJarName) {
        this.forgeJar = forgeJar;
        this.forgeRelocations = forgeRelocations;
        this.forgeMixins = forgeMixins;
        this.fabricJar = fabricJar;
        this.fabricRelocations = fabricRelocations;
        this.quiltJar = quiltJar;
        this.quiltRelocations = quiltRelocations;
        this.group = group;
        this.tempDir = tempDir;
        this.mergedJarName = mergedJarName;
    }

    public File merge() throws IOException {
        tempDir.mkdirs();
        if (forgeJar == null && fabricJar == null && quiltJar == null) {
            throw new IllegalArgumentException("No jars were provided.");
        }

        if (forgeJar != null && !forgeJar.exists()) {
            System.out.println("Forge jar does not exist! You can ignore this if you are not using forge.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        if (fabricJar != null && !fabricJar.exists()) {
            System.out.println("Fabric jar does not exist! You can ignore this if you are not using fabric.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        if (quiltJar != null && !quiltJar.exists()) {
            System.out.println("Quilt jar does not exist! You can ignore this if you are not using quilt.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        System.out.println();
        System.out.println("Settings:");
        System.out.println("Forge: " + (forgeJar == null || !forgeJar.exists() ? "No" : "Yes"));
        System.out.println("Fabric: " + (fabricJar == null || !fabricJar.exists() ? "No" : "Yes"));
        System.out.println("Quilt: " + (quiltJar == null || !quiltJar.exists() ? "No" : "Yes"));
        System.out.println("Group: " + group);
        System.out.println("Merged Jar Name: " + mergedJarName);
        System.out.println();

        remap();

        File mergedJar = new File(tempDir, mergedJarName);
        if (mergedJar.exists()) mergedJar.delete();

        File fabricTemps = new File(tempDir, "fabric-temps");
        File forgeTemps = new File(tempDir, "forge-temps");
        File quiltTemps = new File(tempDir, "quilt-temps");

        if (fabricTemps.exists()) FileUtils.deleteQuietly(fabricTemps);
        fabricTemps.mkdirs();

        if (forgeTemps.exists()) FileUtils.deleteQuietly(forgeTemps);
        forgeTemps.mkdirs();

        if (quiltTemps.exists()) FileUtils.deleteQuietly(quiltTemps);
        quiltTemps.mkdirs();

        JarUnpacker jarUnpacker = new JarUnpacker();
        if (forgeJar != null && forgeJar.exists()) jarUnpacker.unpack(forgeJar.getAbsolutePath(), forgeTemps.getAbsolutePath());
        if (fabricJar != null && fabricJar.exists()) jarUnpacker.unpack(fabricJar.getAbsolutePath(), fabricTemps.getAbsolutePath());
        if (quiltJar != null && quiltJar.exists()) jarUnpacker.unpack(quiltJar.getAbsolutePath(), quiltTemps.getAbsolutePath());

        File mergedTemps = new File(tempDir, "merged-temps");
        if (mergedTemps.exists()) FileUtils.deleteQuietly(mergedTemps);
        mergedTemps.mkdirs();

        Manifest mergedManifest = new Manifest();
        Manifest forgeManifest = new Manifest();
        Manifest fabricManifest = new Manifest();
        Manifest quiltManifest = new Manifest();

        FileInputStream fileInputStream = null;
        if (forgeJar != null && forgeJar.exists()) forgeManifest.read(fileInputStream = new FileInputStream(new File(forgeTemps, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();
        if (fabricJar != null && fabricJar.exists()) fabricManifest.read(fileInputStream = new FileInputStream(new File(fabricTemps, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();
        if (quiltJar != null && quiltJar.exists()) quiltManifest.read(fileInputStream = new FileInputStream(new File(quiltTemps, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();

        forgeManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        fabricManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        quiltManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));

        if (this.forgeMixins != null) {
            List<String> newForgeMixins = new ArrayList<>();
            for (String mixin : this.forgeMixins) {
                newForgeMixins.add("forge-" + mixin);
            }
            this.forgeMixins = newForgeMixins;
            if (!forgeMixins.isEmpty()) mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
        }

        if (mergedManifest.getMainAttributes().getValue("MixinConfigs") == null) {
            System.out.println();
            System.out.println("Couldn't detect forge mixins. You can ignore this if you are not using mixins with forge.");
            System.out.println("If this is an issue then you can configure mixins manually");
            System.out.println("Though we'll try to detect them automatically.");
            System.out.println();
        }

        remapResources(forgeTemps, fabricTemps, quiltTemps);

        if (this.forgeMixins != null && mergedManifest.getMainAttributes().getValue("MixinConfigs") == null) {
            System.out.println();
            System.out.println("Forge mixins detected: " + String.join(",", this.forgeMixins));
            System.out.println();
            if (!forgeMixins.isEmpty()) mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
        }

        mergedManifest.getMainAttributes().putValue("Forgix", version);

        if (forgeJar != null && forgeJar.exists()) new File(forgeTemps, "META-INF/MANIFEST.MF").delete();
        if (fabricJar != null && fabricJar.exists()) new File(fabricTemps, "META-INF/MANIFEST.MF").delete();
        if (quiltJar != null && quiltJar.exists()) new File(quiltTemps, "META-INF/MANIFEST.MF").delete();

        new File(metaInf(mergedTemps), "MANIFEST.MF").createNewFile();
        FileOutputStream outputStream = new FileOutputStream(new File(mergedTemps, "META-INF/MANIFEST.MF"));
        mergedManifest.write(outputStream);
        outputStream.close();

        if (forgeJar != null && forgeJar.exists()) FileUtils.copyDirectory(forgeTemps, mergedTemps);
        if (fabricJar != null && fabricJar.exists()) FileUtils.copyDirectory(fabricTemps, mergedTemps);
        if (quiltJar != null && quiltJar.exists()) FileUtils.copyDirectory(quiltTemps, mergedTemps);

        JarPacker jarPacker = new JarPacker();
        jarPacker.pack(mergedTemps.getAbsolutePath(), mergedJar.getAbsolutePath());

        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_WRITE);
            perms.add(PosixFilePermission.OTHERS_READ);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.GROUP_READ);
            Files.setPosixFilePermissions(mergedJar.toPath(), perms);
        } catch (UnsupportedOperationException | IOException ignored) { }

        FileUtils.deleteQuietly(mergedTemps);
        FileUtils.deleteQuietly(forgeTemps);
        FileUtils.deleteQuietly(fabricTemps);
        FileUtils.deleteQuietly(quiltTemps);

        forgeJar.delete();
        fabricJar.delete();
        quiltJar.delete();

        return mergedJar;
    }

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
                }
            });

            if (architectury.get() != null) quiltRelocation.add(new Relocation(architectury.get(), "quilt." + architectury.get()));

            JarRelocator quiltRelocator = new JarRelocator(quiltJar, remappedQuiltJar, quiltRelocation);
            quiltRelocator.run();

            quiltJar = remappedQuiltJar;
        }
    }

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
    }

}
