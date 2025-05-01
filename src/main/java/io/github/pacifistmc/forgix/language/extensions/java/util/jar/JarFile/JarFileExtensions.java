package io.github.pacifistmc.forgix.language.extensions.java.util.jar.JarFile;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;

import java.io.File;
import java.nio.file.Path;
import java.util.jar.JarFile;

@Extension
public class JarFileExtensions {
    public static File asFile(@This JarFile self) {
        return new File(self.getName());
    }

    public static Path asPath(@This JarFile self) {
        return Path.of(self.getName());
    }

//    // Find fields and make them accessible
//    static Field closeRequestedField = ZipFile.class.getDeclaredField("closeRequested");
//    static Field resField = ZipFile.class.getDeclaredField("res");
//    static {
//        resField.setAccessible(true);
//        closeRequestedField.setAccessible(true);
//    }
//    public static void open(@This JarFile self) {
//        @manifold.ext.rt.api.Jailbreak JarFile self_jailbroken = self;
//        self_jailbroken.closeRequested = false;
//        self_jailbroken.res = new ZipFile(self.getName()).jailbreak().res;
//
//        // Reset closeRequested flag and set the res field in the JarFile to the one from a new ZipFile
////        closeRequestedField.setBoolean(self, false);
////        resField.set(self, resField.get(new ZipFile(self.getName())));
//    }
//
//    public static JarFile ensureOpen(@This JarFile self) throws IOException {
//        if (self.isClosed()) self.open();
//        return self;
//    }
//
//    public static boolean isClosed(@This JarFile self) {
//        return JAR.isClosed(self);
//    }
}
