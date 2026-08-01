package io.github.pacifistmc.forgix.tests;

import com.google.gson.JsonParser;
import io.github.pacifistmc.forgix.utils.Json;
import io.github.pacifistmc.forgix.utils.Text;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Text and Json utilities.
 */
public class UtilsTest {
    @Test
    void testTokenBoundaries() {
        assertEquals("call com.example.Cat now", Text.replaceToken("call com.example.Meow now", "com.example.Meow", "com.example.Cat", ""));
        assertEquals("\"com.example.Cat\"", Text.replaceToken("\"com.example.Meow\"", "com.example.Meow", "com.example.Cat", ""));
        assertEquals("com.example.Cat.FIELD", Text.replaceToken("com.example.Meow.FIELD", "com.example.Meow", "com.example.Cat", ""));
        assertEquals("com.example.Meow2", Text.replaceToken("com.example.Meow2", "com.example.Meow", "com.example.Cat", ""), "A token must not match inside a longer name");
        assertEquals("x.com.example.Meow", Text.replaceToken("x.com.example.Meow", "com.example.Meow", "com.example.Cat", ""), "A token must not match as the tail of a longer dotted name");
        assertEquals("acom.example.Meow", Text.replaceToken("acom.example.Meow", "com.example.Meow", "com.example.Cat", ""));

        assertEquals("com/example/Cat", Text.replaceToken("com/example/Meow", "com/example/Meow", "com/example/Cat"));
        assertEquals("assets/com/example/Meow", Text.replaceToken("assets/com/example/Meow", "com/example/Meow", "com/example/Cat"), "A token must not match as the tail of a longer path");
        assertEquals("com/example/Meow/sub", Text.replaceToken("com/example/Meow/sub", "com/example/Meow", "com/example/Cat"), "A token must not match as a directory of a longer path");
        assertEquals("com/example/Cat.json", Text.replaceToken("com/example/Meow.json", "com/example/Meow", "com/example/Cat"));

        assertTrue(Text.containsToken("META-INF/services/com.example.Meow", "com.example.Meow", ""));
        assertFalse(Text.containsToken("META-INF/services/com.example.Meowth", "com.example.Meow", ""));
    }

    @Test
    void testRewrite() {
        Map<String, String> mappings = Map.of("com/example/mixin/ExampleMixin.class", "com/example/mixin/ExampleMixin_neoforge.class");
        assertEquals("{\"package\": \"com.example.mixin\", \"mixins\": [\"ExampleMixin_neoforge\"]}", Text.rewrite("{\"package\": \"com.example.mixin\", \"mixins\": [\"ExampleMixin\"]}", mappings), "Simple names follow the class when the package is mentioned in the same file");
        assertEquals("mixins: [ExampleMixin]", Text.rewrite("mixins: [ExampleMixin]", mappings), "Simple names must not match when the package isn't mentioned");
        assertEquals("com.example.mixin.ExampleMixin_neoforge", Text.rewrite("com.example.mixin.ExampleMixin", mappings));
        assertEquals("com/example/mixin/ExampleMixin_neoforge", Text.rewrite("com/example/mixin/ExampleMixin", mappings));
        assertEquals("com/example/mixin/ExampleMixin_neoforge.class", Text.rewrite("com/example/mixin/ExampleMixin.class", mappings));

        Map<String, String> ambiguous = Map.of("com/a/Same.class", "com/a/Same_fabric.class", "com/b/Same.class", "com/b/Same_fabric.class");
        assertEquals("com.a com.b Same", Text.rewrite("com.a com.b Same", ambiguous), "Ambiguous simple names stay untouched");
    }

    @Test
    void testDecode() {
        assertEquals("meow", Text.decode("meow".getBytes(StandardCharsets.UTF_8)));
        assertNull(Text.decode(new byte[] { (byte) 0x89, 'P', 'N', 'G', (byte) 0xFF, (byte) 0xFE }), "Binary content isn't text");
    }

    @Test
    void testJsonCanonicalize() {
        assertEquals(Json.canonicalize("{\"b\": 1, \"a\": {\"y\": true, \"x\": []}}"), Json.canonicalize("{\"a\":{\"x\":[],\"y\":true},\"b\":1}"), "Formatting and key order don't matter");
        assertNotEquals(Json.canonicalize("{\"a\": 1}"), Json.canonicalize("{\"a\": 2}"));
        assertNull(Json.canonicalize("not json at all {"));
        assertNull(Json.canonicalize("{\"a\": 1} trailing"));
    }

    @Test
    void testJsonMerge() {
        var merged = Json.merge(JsonParser.parseString("{\"jars\": [{\"path\": \"a.jar\"}], \"same\": 1}"), JsonParser.parseString("{\"jars\": [{\"path\": \"b.jar\"}], \"same\": 1, \"extra\": true}"));
        assertEquals(JsonParser.parseString("{\"jars\": [{\"path\": \"a.jar\"}, {\"path\": \"b.jar\"}], \"same\": 1, \"extra\": true}"), merged);
        assertNull(Json.merge(JsonParser.parseString("{\"a\": 1}"), JsonParser.parseString("{\"a\": 2}")), "Contradicting values can't merge");
        assertNull(Json.merge(JsonParser.parseString("{\"a\": 1}"), JsonParser.parseString("[1]")), "Different shapes can't merge");
    }
}
