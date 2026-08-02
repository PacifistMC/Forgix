package io.github.pacifistmc.forgix.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Works out which Minecraft versions a mod jar is for, by reading the same metadata that its loader reads.
 * We try the strongest source first since picking the wrong range here quietly loads the wrong jar in game.
 */
public class VersionDetector {
    private static final Gson gson = new Gson();

    private static final List<String> TOML_PATHS = List.of("META-INF/mods.toml", "META-INF/neoforge.mods.toml");
    private static final String LEGACY_METADATA_PATH = "mcmod.info";

    // The @Mod annotation moved from cpw.mods.fml to net.minecraftforge.fml in 1.8
    private static final Set<String> MOD_ANNOTATIONS = Set.of("Lnet/minecraftforge/fml/common/Mod;", "Lcpw/mods/fml/common/Mod;");

    // Use Regex for now to extract the version range
    // TODO: Use a proper TOML parser
    private static final Pattern MINECRAFT_DEPENDENCY = Pattern.compile(
            "\\[\\[dependencies\\.[^]]+]]\\s*" +  // Match dependency section
                    "(?:.|\\s)*?" +                           // Any content in between
                    "modId\\s*=\\s*\"minecraft\"\\s*" +       // Match modId = "minecraft"
                    "(?:.|\\s)*?" +                           // Any content in between
                    "versionRange\\s*=\\s*\"([^\"]*)\"",      // Capture the version range
            Pattern.DOTALL
    );

    /**
     * Checks whether the jar is a Forge mod, whichever version of Forge it was built for.
     *
     * @param jar The jar to check
     * @return true if it's a Forge mod, false otherwise
     */
    public static boolean isForge(ZipFile jar) {
        for (String path : TOML_PATHS) {
            if (jar.getFileHeader(path) != null) return true;
        }
        return jar.getFileHeader(LEGACY_METADATA_PATH) != null || findModAnnotation(jar) != null;
    }

    /**
     * Gets the Minecraft version range that the jar declares.
     *
     * @param jar The jar to read
     * @return The version range, or null if the jar doesn't declare one
     */
    public static String detect(ZipFile jar) {
        // The modern loaders make the mod declare the Minecraft version it depends on
        for (String path : TOML_PATHS) {
            String range = fromToml(jar, path);
            if (range != null) return range;
        }

        // 1.12.2 and below have mcmod.info instead, though the version field in it is optional
        String legacy = fromLegacyMetadata(jar);
        if (legacy != null) return legacy;

        // Nothing outside the classes says it, so the annotation is all we have left to go on
        String annotation = findModAnnotation(jar);
        return annotation == null || annotation.isEmpty() ? null : annotation;
    }

    /**
     * Reads the Minecraft dependency out of a mods.toml.
     *
     * @param jar  The jar to read
     * @param path The path of the toml inside the jar
     * @return The version range, or null if the toml isn't there or doesn't declare one
     */
    private static String fromToml(ZipFile jar, String path) {
        var header = jar.getFileHeader(path);
        if (header == null) return null;

        var matcher = MINECRAFT_DEPENDENCY.matcher(IOUtils.toString(jar.getInputStream(header), StandardCharsets.UTF_8));
        return matcher.find() ? usableRange(matcher.group(1)) : null;
    }

    /**
     * Reads the Minecraft version out of an mcmod.info.
     *
     * @param jar The jar to read
     * @return The version, or null if the file isn't there or doesn't declare one
     */
    private static String fromLegacyMetadata(ZipFile jar) {
        var header = jar.getFileHeader(LEGACY_METADATA_PATH);
        if (header == null) return null;

        var root = gson.fromJson(IOUtils.toString(jar.getInputStream(header), StandardCharsets.UTF_8), JsonElement.class);
        if (root == null || root.isJsonNull()) return null;

        // The file is either a plain list of mods, or a modListVersion 2 object with that list inside it
        var mods = root.isJsonObject() ? root.getAsJsonObject().getAsJsonArray("modList") : root.getAsJsonArray();
        if (mods == null) return null;

        for (JsonElement mod : mods) {
            if (!mod.isJsonObject()) continue;
            var entry = mod.getAsJsonObject();
            if (!entry.has("mcversion")) continue;

            String range = usableRange(entry.get("mcversion").getAsString());
            if (range != null) return range;
        }
        return null;
    }

    /**
     * Looks for the @Mod annotation in the classes and reads its acceptedMinecraftVersions.
     * This is what FML itself does to find mods on the old versions.
     *
     * @param jar The jar to scan
     * @return The version range, an empty string if the mod declares none, or null if there's no @Mod at all
     */
    private static String findModAnnotation(ZipFile jar) {
        for (var header : jar.getFileHeaders()) {
            if (header.isDirectory() || !header.getFileName().endsWith(".class")) continue;

            var accepted = new AtomicReference<String>();
            var found = new AtomicReference<>(false);

            new ClassReader(jar.getInputStream(header)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!MOD_ANNOTATIONS.contains(descriptor)) return null;
                    found.set(true);
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("acceptedMinecraftVersions".equals(name) && value instanceof String range) accepted.set(range);
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (accepted.get() != null) return accepted.get();

            // The mod didn't say which versions it takes, but we at least know now that this is a Forge jar
            if (found.get()) return "";
        }
        return null;
    }

    /**
     * Filters out the values that look like a version but don't actually tell us one.
     * A build that never expanded its template leaves us the placeholder, and a catch all matches everything,
     * so trusting either of those would give us a range that silently overlaps the other jars.
     *
     * @param range The declared range
     * @return The range, or null if we can't use it
     */
    private static String usableRange(String range) {
        if (range == null) return null;

        String trimmed = range.trim();
        if (trimmed.isEmpty() || trimmed.equals("[]") || trimmed.equals("*")) return null;
        return trimmed.contains("${") ? null : trimmed;
    }
}
