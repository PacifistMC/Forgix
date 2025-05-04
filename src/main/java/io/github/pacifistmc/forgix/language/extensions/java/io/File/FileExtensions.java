package io.github.pacifistmc.forgix.language.extensions.java.io.File;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Extension
public class FileExtensions {
    public static void deleteQuietly(@This File self) {
        FileUtils.deleteQuietly(self);
    }

    public static File createFileWithParents(@This File self, String content = null) throws IOException {
        self.parentFile.mkdirs();
        if (self.createNewFile() && content != null) FileUtils.write(self, content, "UTF-8");
        return self;
    }

    public static void mustDeleteOnExit(@This File self) {
        self.deleteOnExit();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileExtensions.deleteQuietly(self))); // No, this cannot be replaced with a lambda reference
    }
}
