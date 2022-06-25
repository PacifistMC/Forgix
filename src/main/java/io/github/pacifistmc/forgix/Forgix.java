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
import java.util.jar.Manifest;

// This is the class that does the magic.
public class Forgix {
    private File forgeJar;
    private final Map<String, String> forgeRelocations;
    private final List<String> forgeMixins;
    private File fabricJar;
    private final Map<String, String> fabricRelocations;
    private File quiltJar;
    private final Map<String, String> quiltRelocations;
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
        if (forgeJar == null && fabricJar == null && quiltJar == null) {
            throw new IllegalArgumentException("No jars were provided.");
        }

        if (forgeJar == null || !forgeJar.exists()) {
            System.out.println("Forge jar does not exist! You can ignore this if you are not using forge.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        if (fabricJar == null || !fabricJar.exists()) {
            System.out.println("Fabric jar does not exist! You can ignore this if you are not using fabric.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        if (quiltJar == null || !quiltJar.exists()) {
            System.out.println("Quilt jar does not exist! You can ignore this if you are not using quilt.");
            System.out.println("You might want to change Forgix settings if something is wrong.");
        }

        remap();

        File mergedJar = new File(tempDir, mergedJarName);
        if (mergedJar.exists()) mergedJar.delete();

        File fabricTemps = new File(tempDir, "fabric-temps");
        File forgeTemps = new File(tempDir, "forge-temps");
        File quiltTemps = new File(tempDir, "quilt-temps");

        if (fabricTemps.exists()) {
            deleteDirectory(fabricTemps);
            fabricTemps.delete();
        }
        fabricTemps.mkdirs();

        if (forgeTemps.exists()) {
            deleteDirectory(forgeTemps);
            forgeTemps.delete();
        }
        forgeTemps.mkdirs();

        if (quiltTemps.exists()) {
            deleteDirectory(quiltTemps);
            quiltTemps.delete();
        }
        quiltTemps.mkdirs();

        JarUnpacker jarUnpacker = new JarUnpacker();
        if (forgeJar != null && forgeJar.exists()) jarUnpacker.unpack(forgeJar.getAbsolutePath(), forgeTemps.getAbsolutePath());
        if (fabricJar != null && fabricJar.exists()) jarUnpacker.unpack(fabricJar.getAbsolutePath(), fabricTemps.getAbsolutePath());
        if (quiltJar != null && quiltJar.exists()) jarUnpacker.unpack(quiltJar.getAbsolutePath(), quiltTemps.getAbsolutePath());

        File mergedTemps = new File(tempDir, "merged-temps");
        if (mergedTemps.exists()) {
            deleteDirectory(mergedTemps);
            mergedTemps.delete();
        }
        mergedTemps.mkdirs();

        String forgeMixins = null;
        if (this.forgeMixins != null) {
            forgeMixins = String.join(",", this.forgeMixins);
        }

        Manifest mergedManifest = new Manifest();
        if (forgeJar != null && forgeJar.exists()) mergedManifest.read(new FileInputStream(new File(forgeTemps, "META-INF/MANIFEST.MF")));
        if (fabricJar != null && fabricJar.exists()) mergedManifest.read(new FileInputStream(new File(fabricTemps, "META-INF/MANIFEST.MF")));
        if (quiltJar != null && quiltJar.exists()) mergedManifest.read(new FileInputStream(new File(quiltTemps, "META-INF/MANIFEST.MF")));

        if (forgeMixins != null) {
            mergedManifest.getMainAttributes().remove("MixinConfigs");
            mergedManifest.getMainAttributes().putValue("MixinConfigs", forgeMixins);
        }

        if (forgeJar != null && forgeJar.exists()) new File(forgeTemps, "META-INF/MANIFEST.MF").delete();
        if (fabricJar != null && fabricJar.exists()) new File(fabricTemps, "META-INF/MANIFEST.MF").delete();
        if (quiltJar != null && quiltJar.exists()) new File(quiltTemps, "META-INF/MANIFEST.MF").delete();

        new File(mergedTemps, "META-INF/MANIFEST.MF").createNewFile();
        mergedManifest.write(new FileOutputStream(new File(mergedTemps, "META-INF/MANIFEST.MF")));

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

        deleteDirectory(mergedTemps);
        deleteDirectory(forgeTemps);
        deleteDirectory(fabricTemps);
        deleteDirectory(quiltTemps);

        forgeJar.delete();
        fabricJar.delete();
        quiltJar.delete();

        return mergedJar;
    }

    private void remap() throws IOException {
        if (forgeJar != null && forgeJar.exists()) {
            File remappedForgeJar = new File(tempDir, "tempForgeInMerging.jar");
            if (remappedForgeJar.exists()) remappedForgeJar.delete();

            List<Relocation> forgeRelocation = new ArrayList<>();
            forgeRelocation.add(new Relocation(group, "forge." + group));
            if (forgeRelocations != null)
                forgeRelocation.addAll(forgeRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            JarRelocator forgeRelocator = new JarRelocator(forgeJar, remappedForgeJar, forgeRelocation);
            forgeRelocator.run();

            forgeJar = remappedForgeJar;
        }

        if (fabricJar != null && fabricJar.exists()) {
            File remappedFabricJar = new File(tempDir, "tempFabricInMerging.jar");
            if (remappedFabricJar.exists()) remappedFabricJar.delete();

            List<Relocation> fabricRelocation = new ArrayList<>();
            fabricRelocation.add(new Relocation(group, "fabric." + group));
            if (fabricRelocations != null)
                fabricRelocation.addAll(fabricRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            JarRelocator fabricRelocator = new JarRelocator(fabricJar, remappedFabricJar, fabricRelocation);
            fabricRelocator.run();

            fabricJar = remappedFabricJar;
        }

        if (quiltJar != null && quiltJar.exists()) {
            File remappedQuiltJar = new File(tempDir, "tempQuiltInMerging.jar");
            if (remappedQuiltJar.exists()) remappedQuiltJar.delete();

            List<Relocation> quiltRelocation = new ArrayList<>();
            quiltRelocation.add(new Relocation(group, "quilt." + group));
            if (quiltRelocations != null)
                quiltRelocation.addAll(quiltRelocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            JarRelocator quiltRelocator = new JarRelocator(quiltJar, remappedQuiltJar, quiltRelocation);
            quiltRelocator.run();

            quiltJar = remappedQuiltJar;
        }
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
