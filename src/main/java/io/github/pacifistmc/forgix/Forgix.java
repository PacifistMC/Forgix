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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.pacifistmc.forgix.utils.FileUtils.listAllTextFiles;
import static io.github.pacifistmc.forgix.utils.FileUtils.manifestJars;

// This is the class that does the magic.
@SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "FieldCanBeLocal"})
public class Forgix {
    private final String version = "1.0";

    private File forgeJar;
    private Map<String, String> forgeRelocations;
    private final List<String> forgeMixins;
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
        if (forgeJar != null && forgeJar.exists()) mergedManifest.read(new FileInputStream(new File(forgeTemps, "META-INF/MANIFEST.MF")));
        if (fabricJar != null && fabricJar.exists()) mergedManifest.read(new FileInputStream(new File(fabricTemps, "META-INF/MANIFEST.MF")));
        if (quiltJar != null && quiltJar.exists()) mergedManifest.read(new FileInputStream(new File(quiltTemps, "META-INF/MANIFEST.MF")));

        mergedManifest.getMainAttributes().putValue("Forgix", version);

        if (this.forgeMixins != null) {
            mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
        }

        if (forgeJar != null && forgeJar.exists()) new File(forgeTemps, "META-INF/MANIFEST.MF").delete();
        if (fabricJar != null && fabricJar.exists()) new File(fabricTemps, "META-INF/MANIFEST.MF").delete();
        if (quiltJar != null && quiltJar.exists()) new File(quiltTemps, "META-INF/MANIFEST.MF").delete();

        new File(mergedTemps, "META-INF/MANIFEST.MF").createNewFile();
        FileOutputStream outputStream = new FileOutputStream(new File(mergedTemps, "META-INF/MANIFEST.MF"));
        mergedManifest.write(outputStream);
        outputStream.close();

        remapResources(forgeTemps, fabricTemps, quiltTemps);

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

    private void remapResources(File forgeTemps, File fabricTemps, File quiltTemps) throws IOException {
        if (forgeRelocations == null) forgeRelocations = new HashMap<>();
        if (forgeJar != null && forgeJar.exists()) {
            for (File file : manifestJars(forgeTemps)) {
                File remappedFile = new File(file.getParentFile(), "forge-" + file.getName());
                forgeRelocations.put(file.getName(), remappedFile.getName());
                file.renameTo(remappedFile);
            }

            for (File file : listAllTextFiles(forgeTemps)) {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());
                if (!Pattern.matches("forge." + Matcher.quoteReplacement(group), text)) {
                    String newText = text.replaceAll(Matcher.quoteReplacement(group), "forge." + group);
                    FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                }

                if (forgeRelocations != null) {
                    for (Map.Entry<String, String> entry : forgeRelocations.entrySet()) {
                        if (!Pattern.matches(Matcher.quoteReplacement(entry.getValue()), text)) {
                            String newText = text.replaceAll(Matcher.quoteReplacement(entry.getKey()), entry.getValue());
                            FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                        }
                    }
                }
            }
        }

        if (fabricRelocations == null) fabricRelocations = new HashMap<>();
        if (fabricJar != null && fabricJar.exists()) {
            for (File file : manifestJars(fabricTemps)) {
                File remappedFile = new File(file.getParentFile(), "fabric-" + file.getName());
                fabricRelocations.put(file.getName(), remappedFile.getName());
                file.renameTo(remappedFile);
            }

            for (File file : listAllTextFiles(fabricTemps)) {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());
                if (!Pattern.matches("fabric." + Matcher.quoteReplacement(group), text)) {
                    String newText = text.replaceAll(Matcher.quoteReplacement(group), "fabric." + group);
                    FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                }

                if (fabricRelocations != null) {
                    for (Map.Entry<String, String> entry : fabricRelocations.entrySet()) {
                        if (!Pattern.matches(Matcher.quoteReplacement(entry.getValue()), text)) {
                            String newText = text.replaceAll(Matcher.quoteReplacement(entry.getKey()), entry.getValue());
                            FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                        }
                    }
                }
            }
        }

        if (quiltRelocations == null) quiltRelocations = new HashMap<>();
        if (quiltJar != null && quiltJar.exists()) {
            for (File file : manifestJars(quiltTemps)) {
                File remappedFile = new File(file.getParentFile(), "quilt-" + file.getName());
                quiltRelocations.put(file.getName(), remappedFile.getName());
                file.renameTo(remappedFile);
            }

            for (File file : listAllTextFiles(quiltTemps)) {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());
                if (!Pattern.matches("quilt." + Matcher.quoteReplacement(group), text)) {
                    String newText = text.replaceAll(Matcher.quoteReplacement(group), "quilt." + group);
                    FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                }

                if (quiltRelocations != null) {
                    for (Map.Entry<String, String> entry : quiltRelocations.entrySet()) {
                        if (!Pattern.matches(Matcher.quoteReplacement(entry.getValue()), text)) {
                            String newText = text.replaceAll(Matcher.quoteReplacement(entry.getKey()), entry.getValue());
                            FileUtils.writeStringToFile(file, newText, Charset.defaultCharset(), false);
                        }
                    }
                }
            }
        }
    }

}
