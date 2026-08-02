package io.github.pacifistmc.forgix.tests;

import io.github.pacifistmc.forgix.core.VersionDetector;
import manifold.rt.api.DisableStringLiteralTemplates;
import net.lingala.zip4j.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VersionDetectorTest {
    @TempDir
    Path tempDir;

    private static final String MODERN_TOML = """
            modLoader="javafml"
            [[mods]]
            modId="example"
            [[dependencies.example]]
            modId="minecraft"
            versionRange="[1.20.1,1.21)"
            """;

    /**
     * Writes a JAR file with the given entries into the temp directory.
     *
     * @param name    The name of the JAR file
     * @param entries The entries to put in it
     * @return The JAR file
     */
    private File jar(String name, Map<String, byte[]> entries) throws IOException {
        File file = tempDir.resolve(name).toFile();
        try (var zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return file;
    }

    /**
     * Builds a class with the old @Mod annotation on it.
     *
     * @param annotationDescriptor      The descriptor of the annotation, which differs between 1.7.10 and 1.12.2
     * @param acceptedMinecraftVersions The range to put on the annotation, or null to leave it off
     * @return The class bytes
     */
    private byte[] modClass(String annotationDescriptor, String acceptedMinecraftVersions) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/ExampleMod", null, "java/lang/Object", null);

        var annotation = writer.visitAnnotation(annotationDescriptor, true);
        annotation.visit("modid", "example");
        if (acceptedMinecraftVersions != null) annotation.visit("acceptedMinecraftVersions", acceptedMinecraftVersions);
        annotation.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testModernTomlRange() throws IOException {
        var file = jar("modern.jar", Map.of("META-INF/mods.toml", bytes(MODERN_TOML)));

        // Verify the range comes from the minecraft dependency
        try (var zip = new ZipFile(file)) {
            assertTrue(VersionDetector.isForge(zip));
            assertEquals("[1.20.1,1.21)", VersionDetector.detect(zip));
        }
    }

    @Test
    void testLegacyMcmodInfo() throws IOException {
        var file = jar("legacy.jar", Map.of("mcmod.info", bytes("""
                [{"modid": "example", "name": "Example", "mcversion": "1.12.2"}]""")));

        // Verify a mcmod.info is enough on its own
        try (var zip = new ZipFile(file)) {
            assertTrue(VersionDetector.isForge(zip));
            assertEquals("1.12.2", VersionDetector.detect(zip));
        }
    }

    @Test
    void testLegacyMcmodInfoModListVersionTwo() throws IOException {
        var file = jar("legacy2.jar", Map.of("mcmod.info", bytes("""
                {"modListVersion": 2, "modList": [{"modid": "example", "mcversion": "1.7.10"}]}""")));

        // Verify we also read the shape that wraps the mod list in an object
        try (var zip = new ZipFile(file)) {
            assertEquals("1.7.10", VersionDetector.detect(zip));
        }
    }

    /**
     * Test that a template the build never expanded is ignored, we fall through to the annotation instead.
     */
    @Test
    @DisableStringLiteralTemplates // Manifold would expand the very placeholder that this test is about
    void testUnexpandedTemplateIsIgnored() throws IOException {
        var file = jar("template.jar", Map.of(
                "mcmod.info", bytes("[{\"modid\": \"example\", \"mcversion\": \"${mcversion}\"}]"),
                "com/example/ExampleMod.class", modClass("Lnet/minecraftforge/fml/common/Mod;", "[1.12,1.13)")
        ));

        try (var zip = new ZipFile(file)) {
            assertEquals("[1.12,1.13)", VersionDetector.detect(zip));
        }
    }

    @Test
    void testModAnnotationFromBeforeTheRename() throws IOException {
        var file = jar("cpw.jar", Map.of("com/example/ExampleMod.class", modClass("Lcpw/mods/fml/common/Mod;", "[1.7.10]")));

        // Verify we read the 1.7.10 annotation as well as the newer one
        try (var zip = new ZipFile(file)) {
            assertTrue(VersionDetector.isForge(zip));
            assertEquals("[1.7.10]", VersionDetector.detect(zip));
        }
    }

    @Test
    void testModWithoutADeclaredVersion() throws IOException {
        var file = jar("bare.jar", Map.of("com/example/ExampleMod.class", modClass("Lcpw/mods/fml/common/Mod;", null)));

        // Verify we recognise the jar but don't invent a version for it
        try (var zip = new ZipFile(file)) {
            assertTrue(VersionDetector.isForge(zip), "a @Mod class on its own makes this a Forge jar");
            assertNull(VersionDetector.detect(zip), "guessing here would quietly load the wrong jar in game");
        }
    }

    @Test
    void testNonForgeJar() throws IOException {
        var file = jar("fabric.jar", Map.of("fabric.mod.json", bytes("""
                {"schemaVersion": 1, "id": "example"}""")));

        // Verify a fabric-only mod isn't mistaken for a Forge one
        try (var zip = new ZipFile(file)) {
            assertFalse(VersionDetector.isForge(zip));
            assertNull(VersionDetector.detect(zip));
        }
    }
}
