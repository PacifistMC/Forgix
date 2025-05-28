package io.github.pacifistmc.forgix.language.extensions.java.lang.String;

import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.apache.commons.io.FilenameUtils;

import java.io.File;

@Extension
public class StringExtension {
    public static String removeExtension(@This String self) {
        return FilenameUtils.removeExtension(self);
    }

    public static String getExtension(@This String self) {
        return FilenameUtils.getExtension(self);
    }

    public static String getBaseName(@This String self) {
        return FilenameUtils.getBaseName(self);
    }

    public static String getPath(@This String self) {
        return FilenameUtils.getPathNoEndSeparator(self);
    }

    public static String setExtension(@This String self, String extension) {
        return "${self.removeExtension()}.${extension}";
    }

    public static String setBaseNameExtension(@This String self, String extension) {
        return "${self.getBaseName()}.${extension}";
    }

    public static String addPrefixExtension(@This String self, String prefix) {
        return "${removeExtension(self)}_${prefix}.${getExtension(self)}";
    }

    public static String first(@This String self, int n) {
        return self.substring(0, Math.min(n, self.length()));
    }

    public static String everythingAfterFirst(@This String self, String substring) {
        return self.substring(self.indexOf(substring) + substring.length());
    }
}
