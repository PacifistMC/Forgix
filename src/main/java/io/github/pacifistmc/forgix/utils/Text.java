package io.github.pacifistmc.forgix.utils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Finds and rewrites references to renamed files inside text content.
 * A reference only ever matches as a whole token, so `com.example.Meow` can never match inside `com.example.Meow2`.
 */
public class Text {
    private Text() { }

    /**
     * Decodes bytes as text.
     * @return The decoded text, or null if the bytes aren't valid UTF-8 (binary files)
     */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException _) {
            return null;
        }
    }

    /**
     * Rewrites every reference to the given renamed files inside the content.
     * Files are matched by their path in all its spellings, for classes that includes the binary name,
     * the dotted name and the standalone simple name when the package is also mentioned.
     * @param mappings Map of original entry paths to relocated entry paths
     * @param context What's being rewritten, only used in warning messages
     */
    public static String rewrite(String content, Map<String, String> mappings, String context = null) {
        // Longest first so a path can never match inside a longer mapped path
        var ordered = mappings.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> -entry.getKey().length())).toList();
        for (var mapping : ordered) {
            var original = mapping.getKey();
            var relocated = mapping.getValue();
            content = replaceToken(content, original, relocated);
            content = replaceToken(content, original.replace('/', '\\'), relocated.replace('/', '\\'), "\\");
            if (original.endsWith(".class")) {
                var originalName = original.removeExtension();
                var relocatedName = relocated.removeExtension();
                content = replaceToken(content, originalName, relocatedName); // binary name (com/example/Foo)
                content = replaceToken(content, originalName.replace('/', '.'), relocatedName.replace('/', '.'), ""); // dotted name (com.example.Foo)
                content = replaceToken(content, originalName.replace('/', '\\'), relocatedName.replace('/', '\\'), "\\");
            }
        }
        return rewriteSimpleNames(content, ordered, context);
    }

    /**
     * Rewrites standalone simple class names, this is how files that declare a package refer to
     * classes (mixin configs list their classes relative to a "package" field).
     * We only do this when the content also mentions the package and exactly one renamed class has that simple name.
     */
    private static String rewriteSimpleNames(String content, List<Map.Entry<String, String>> mappings, String context) {
        Map<String, List<Map.Entry<String, String>>> candidatesBySimpleName = new HashMap<>();
        for (var mapping : mappings) {
            if (!mapping.getKey().endsWith(".class")) continue;
            var packageName = mapping.getKey().getPath().replace('/', '.');
            if (packageName.isEmpty() || !containsToken(content, packageName, "")) continue;
            candidatesBySimpleName.computeIfAbsent(mapping.getKey().getBaseName(), _ -> new ArrayList<>()).add(mapping);
        }
        for (var candidates : candidatesBySimpleName.entrySet()) {
            var simpleName = candidates.getKey();
            if (!containsToken(content, simpleName, "/\\")) continue;
            if (candidates.getValue().size() > 1) {
                var where = context == null ? "" : " in ${context}";
                var couldBe = candidates.getValue().stream().map(Map.Entry::getKey).toList();
                "Not rewriting ${simpleName}${where} since multiple renamed classes have that name! It could be any of ${couldBe}".err();
                continue;
            }
            var mapping = candidates.getValue().getFirst();
            content = replaceToken(content, simpleName, mapping.getValue().getBaseName(), "/\\");
        }
        return content;
    }

    /**
     * Replaces whole-token occurrences. A token doesn't match when it's surrounded by identifier characters,
     * preceded by a '.' (it would be the tail of a longer dotted name) or touching a separator.
     * @param separators Characters that act as path separators for this form of the token
     */
    public static String replaceToken(String content, String token, String replacement, String separators = "/") {
        var index = nextToken(content, token, separators, 0);
        if (index == -1) return content;
        var builder = new StringBuilder(content.length());
        var last = 0;
        while (index != -1) {
            builder.append(content, last, index).append(replacement);
            last = index + token.length();
            index = nextToken(content, token, separators, last);
        }
        return builder.append(content, last, content.length()).toString();
    }

    /**
     * @return Whether the content contains the token as a whole token
     */
    public static boolean containsToken(String content, String token, String separators = "/") {
        return nextToken(content, token, separators, 0) != -1;
    }

    private static int nextToken(String content, String token, String separators, int fromIndex) {
        if (token.isEmpty()) return -1;
        for (var index = content.indexOf(token, fromIndex); index != -1; index = content.indexOf(token, index + 1)) {
            if (isTokenBoundary(content, index, token.length(), separators)) return index;
        }
        return -1;
    }

    private static boolean isTokenBoundary(String content, int index, int length, String separators) {
        if (index > 0) {
            var before = content.charAt(index - 1);
            if (isIdentifierChar(before) || before == '.' || separators.indexOf(before) != -1) return false;
        }
        if (index + length < content.length()) {
            var after = content.charAt(index + length);
            if (isIdentifierChar(after) || separators.indexOf(after) != -1) return false;
        }
        return true;
    }

    private static boolean isIdentifierChar(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c);
    }
}
