package io.github.pacifistmc.forgix.core;

import manifold.ext.props.rt.api.val;
import manifold.ext.rt.api.Structural;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

@Structural
public interface RelocationConfig {
    @val JarFile jarFile;
    @val String conflictPrefix;

    // INTERNAL USE ONLY. DO NOT TOUCH.
    @val Map<String, String> mappings = new ConcurrentHashMap<>();
    @val File tinyFile = null;
}
