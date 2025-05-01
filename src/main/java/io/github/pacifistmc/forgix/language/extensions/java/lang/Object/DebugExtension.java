package io.github.pacifistmc.forgix.language.extensions.java.lang.Object;

import io.github.pacifistmc.forgix.Forgix;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;

/**
 * Extensions to the Object class. (Basically all classes)
 */
@Extension
public class DebugExtension {
    /**
     * Prints the object.
     */
    public static void println(@This Object self) {
        System.out.println(self);
    }

    /**
     * Prints the object to stderr.
     */
    public static void errorPrintln(@This Object self) {
        System.err.println(self);
    }

    /**
     * How did we get here?
     * In your code, you can do `stacktrace()` anywhere to print the stacktrace.
     */
    @Extension
    public static void stacktrace() {
        Thread.currentThread().getStackTrace().println();
    }
}
