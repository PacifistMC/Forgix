package io.github.pacifistmc.forgix.tests;

import io.github.pacifistmc.forgix.core.Multiversion;
import net.lingala.zip4j.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the multiversion merge for the LaunchWrapper versions, where the entry point is named
 * in the manifest instead of in a services file.
 */
public class MultiversionLegacyTest {
    @TempDir
    Path tempDir;

    private static final String TWEAKER = "io.github.pacifistmc.forgix.multiversion.LegacyMultiversionTweaker";

    private File jar(String name, Map<String, String> entries) throws IOException {
        File file = tempDir.resolve(name).toFile();
        try (var zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return file;
    }

    /**
     * Writes a mod jar the way 1.12.2 and below build them, with an mcmod.info and no toml.
     */
    private File legacyJar(String name, String mcVersion) throws IOException {
        return jar(name, Map.of(
                "mcmod.info", "[{\"modid\": \"example\", \"mcversion\": \"" + mcVersion + "\"}]",
                "example/Thing.class", "not really a class, only the metadata gets read here"
        ));
    }

    /**
     * Writes a mod jar the way the modern loaders build them, with a mods.toml.
     */
    private File modernJar(String name, String range) throws IOException {
        return jar(name, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                [[mods]]
                modId="example"
                [[dependencies.example]]
                modId="minecraft"
                versionRange="%s"
                """.formatted(range)));
    }

    private File merge(String name, List<File> jars) throws IOException {
        File output = tempDir.resolve(name).toFile();
        try (var baos = Multiversion.mergeVersions(jars); var fos = new FileOutputStream(output)) {
            baos.writeTo(fos);
        }
        return output;
    }

    @Test
    void testLegacyAndModernMerge() throws IOException {
        var output = merge("merged.jar", List.of(legacyJar("old.jar", "1.12.2"), modernJar("new.jar", "[1.20.1,1.21)")));

        try (var merged = new ZipFile(output)) {
            // Verify the merged JAR describes both versions
            var versions = merged.getFileHeader("META-INF/forgix/multiversion.json");
            assertNotNull(versions, "a forge merge has to describe its versions");

            String json = new String(merged.getInputStream(versions).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("1.12.2"), "the legacy range should have carried over: " + json);
            assertTrue(json.contains("[1.20.1,1.21)"), "the modern range should have carried over: " + json);

            // Verify both jars are nested inside it
            assertNotNull(merged.getFileHeader("META-INF/forgix/multiversion/old.jar"));
            assertNotNull(merged.getFileHeader("META-INF/forgix/multiversion/new.jar"));
        }
    }

    /**
     * Test that the TweakClass attribute follows the classes into the relocated package.
     * LaunchWrapper reads the entry point out of the manifest, so a stale name there kills the game on startup.
     */
    @Test
    void testManifestEntryPointRelocation() throws IOException {
        var output = merge("relocated.jar", List.of(legacyJar("a.jar", "1.7.10"), legacyJar("b.jar", "1.12.2")));

        try (var merged = new ZipFile(output)) {
            var manifest = new Manifest(merged.getInputStream(merged.getFileHeader("META-INF/MANIFEST.MF")));
            String tweakClass = manifest.getMainAttributes().getValue("TweakClass");

            // Verify the attribute is there and was moved rather than left alone
            assertNotNull(tweakClass, "LaunchWrapper can't find the tweaker without this attribute");
            assertTrue(tweakClass.endsWith(TWEAKER), "the class name should have survived: " + tweakClass);
            assertNotEquals(TWEAKER, tweakClass, "the name has to move along with the relocated classes");

            // Verify it points at a class that's really in there
            String path = tweakClass.replace('.', '/') + ".class";
            assertNotNull(merged.getFileHeader(path), "no class at " + path);
        }
    }

    @Test
    void testUndeclaredVersionIsRejected() throws IOException {
        var bare = jar("bare.jar", Map.of("mcmod.info", "[{\"modid\": \"example\"}]"));
        var modern = modernJar("new.jar", "[1.20.1,1.21)");

        // Verify we say which jar is the problem instead of guessing a range for it
        var failure = assertThrows(RuntimeException.class, () -> Multiversion.mergeVersions(List.of(bare, modern)));
        System.out.println("=====MESSAGE=====\n" + failure.getMessage() + "\n=====END=====");
        assertTrue(failure.getMessage().contains("bare.jar"));
    }

    @Test
    void testDuplicateVersionIsRejected() throws IOException {
        var first = legacyJar("first.jar", "1.12.2");
        var second = legacyJar("second.jar", "1.12.2");

        // Verify we don't produce a jar where the two overlap and only one of them can ever load
        assertThrows(RuntimeException.class, () -> Multiversion.mergeVersions(List.of(first, second)));
    }
}
