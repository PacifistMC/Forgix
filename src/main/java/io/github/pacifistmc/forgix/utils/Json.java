package io.github.pacifistmc.forgix.utils;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.StringReader;
import java.util.Map;

/**
 * Utility for comparing and merging JSON by meaning rather than by bytes.
 */
public class Json {
    private static final Gson gson = new Gson();

    private Json() { }

    /**
     * Parses strict JSON.
     * @return The parsed element, or null if the content isn't valid JSON
     */
    public static JsonElement parse(String content) {
        try {
            var reader = new JsonReader(new StringReader(content));
            reader.setStrictness(Strictness.STRICT);
            var element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) return null; // Trailing content means it's not a JSON document
            return element;
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Puts the content into a canonical form (sorted keys, no formatting),
     * so two JSON files that only differ in formatting or key order compare as equal.
     * @return The canonical form, or null if the content isn't valid JSON
     */
    public static String canonicalize(String content) {
        var element = parse(content);
        return element == null ? null : gson.toJson(sorted(element));
    }

    /**
     * Merges JSON documents that don't contradict each other,
     * objects merge key by key, arrays become the union of their elements and equal values collapse.
     * @return The merged element, or null if the documents genuinely disagree somewhere
     */
    public static JsonElement merge(JsonElement a, JsonElement b) {
        if (a.equals(b)) return a;
        if (a.isJsonObject() && b.isJsonObject()) {
            var merged = new JsonObject();
            for (var entry : a.getAsJsonObject().entrySet()) {
                var other = b.getAsJsonObject().get(entry.getKey());
                var value = other == null ? entry.getValue() : merge(entry.getValue(), other);
                if (value == null) return null;
                merged.add(entry.getKey(), value);
            }
            b.getAsJsonObject().entrySet().stream().filter(entry -> !merged.has(entry.getKey())).forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
            return merged;
        }
        if (a.isJsonArray() && b.isJsonArray()) {
            var merged = new JsonArray();
            merged.addAll(a.getAsJsonArray());
            b.getAsJsonArray().forEach(element -> { if (!merged.contains(element)) merged.add(element); });
            return merged;
        }
        return null;
    }

    /**
     * @return The element serialized as JSON
     */
    public static String write(JsonElement element) {
        return gson.toJson(element);
    }

    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            var object = new JsonObject();
            element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> object.add(entry.getKey(), sorted(entry.getValue())));
            return object;
        }
        if (element.isJsonArray()) {
            var array = new JsonArray();
            element.getAsJsonArray().forEach(item -> array.add(sorted(item)));
            return array;
        }
        return element;
    }
}
