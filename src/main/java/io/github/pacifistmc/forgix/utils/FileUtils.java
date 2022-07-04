package io.github.pacifistmc.forgix.utils;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static List<File> manifestJars(File dir) {
        List<File> jars = new ArrayList<>();
        File jarsLocation = new File(dir, "META-INF/jars");
        File jarJarLocation = new File(dir, "META-INF/jarjar");
        if (jarsLocation.exists()) {
            File[] list = jarsLocation.listFiles();
            if (list != null) {
                for (File jar : list) {
                    if (FilenameUtils.getExtension(jar.getName()).equals("jar")) {
                        jars.add(jar);
                    }
                }
            }
        }
        if (jarJarLocation.exists()) {
            File[] list = jarJarLocation.listFiles();
            if (list != null) {
                for (File jar : list) {
                    if (FilenameUtils.getExtension(jar.getName()).equals("jar")) {
                        jars.add(jar);
                    }
                }
            }
        }
        return jars;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File metaInf(File dir) {
        File meta = new File(dir, "META-INF");
        meta.mkdirs();
        return meta;
    }

    public static List<File> listAllTextFiles(File dir) {
        List<File> files = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null) return files;
        for (File file : list) {
            if (file.isDirectory()) {
                files.addAll(listAllTextFiles(file));
            } else {
                if (!FilenameUtils.getExtension(file.getName()).equals("class")) {
                    if (!isBinary(file)) files.add(file);
                }
            }
        }
        return files;
    }

    public static List<File> listAllMixins(File dir, boolean refmaps) throws IOException {
        List<File> files = listAllTextFiles(dir);
        List<File> mixins = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("json")) {
                String text = org.apache.commons.io.FileUtils.readFileToString(file, Charset.defaultCharset());
                if (refmaps) {
                    if (text.contains("\"mappings\":") || text.contains("\"data\":")) {
                        mixins.add(file);
                        continue;
                    }
                }

                if (text.contains("\"package\":")) {
                    mixins.add(file);
                }
            }
        }

        return mixins;
    }

    public static List<File> listAllRefmaps(File dir) throws IOException {
        List<File> files = listAllTextFiles(dir);
        List<File> refmaps = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("json")) {
                String text = org.apache.commons.io.FileUtils.readFileToString(file, Charset.defaultCharset());
                if (text.contains("\"mappings\":") || text.contains("\"data\":")) {
                    refmaps.add(file);
                }
            }
        }

        return refmaps;
    }

    public static List<File> listAllAccessWideners(File dir) throws IOException {
        List<File> files = listAllTextFiles(dir);
        List<File> wideners = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("accesswidener")) {
                wideners.add(file);
                continue;
            }

            String text = org.apache.commons.io.FileUtils.readFileToString(file, Charset.defaultCharset());
            if (text.startsWith("accessWidener")) {
                wideners.add(file);
            }
        }

        return wideners;
    }

    private static boolean isBinary(File file) {
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            int read = bis.read();
            while (read != -1) {
                if (isMagicCharacter(read)) return true;
                read = bis.read();
            }
            bis.close();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isMagicCharacter(int decimal) {
//        if (decimal > 127) return true;
//        if (decimal < 37) {
//            return decimal != 10 && decimal != 13 && decimal != 9 && decimal != 32 && decimal != 11 && decimal != 12 && decimal != 8;
//        }
//        return false;
        return decimal > 127;
    }
}
