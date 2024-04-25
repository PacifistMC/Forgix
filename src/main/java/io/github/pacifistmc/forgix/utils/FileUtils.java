package io.github.pacifistmc.forgix.utils;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FileUtils {
    /**
     * This is the method that lists all the manifestJars
     * @param dir That contains the META-INF folder
     * @return A list of all the manifestJars
     */
    public static List<File> manifestJars(File dir) {
        List<File> jars = new ArrayList<>();
        File metaInf = new File(dir, "META-INF");
        File jarsLocation = new File(metaInf, "jars");
        File jarJarLocation = new File(metaInf, "jarjar");
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

    /**
     * @param dir That should contain the META-INF directory
     * @return The META-INF directory
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File metaInf(File dir) {
        File meta = new File(dir, "META-INF");
        meta.mkdirs();
        return meta;
    }

    /**
     * This method lists all text files recursively that aren't binary files
     * @return List of all text files
     */
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

    /**
     * This method returns all the mixins recursively
     * @param refmaps If true it also returns all the refmaps
     * @return List of all mixins
     * @throws IOException If something went wrong
     */
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

    /**
     * This method returns all the mixin refmaps recursively
     * @return A list of all the refmaps
     * @throws IOException If something went wrong
     */
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

    /**
     * This method returns all the accesswideners recursively
     * @return A list of all the accesswideners
     * @throws IOException If something went wrong
     */
    public static List<File> listAllAccessWideners(File dir) throws IOException {
        List<File> files = listAllTextFiles(dir);
        List<File> wideners = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("accesswidener")) {
                wideners.add(file);
                continue;
            }

            FileInputStream fis = new FileInputStream(file);
            Scanner scanner = new Scanner(fis);
            if (scanner.hasNext()) {
                if (scanner.nextLine().startsWith("accessWidener")) {
                    wideners.add(file);
                }
            }
            scanner.close();
            fis.close();
        }

        return wideners;
    }

    public static List<File> listAllPlatformServices(File dir, String group) {
        List<File> services = new ArrayList<>();

        File metaInf = new File(dir, "META-INF");
        File servicesLocation = new File(metaInf, "services");

        if (servicesLocation.exists()) {
            File[] list = servicesLocation.listFiles();
            if (list != null) {
                for (File service : list) {
                    if (FilenameUtils.getBaseName(service.getName()).contains(group)) {
                        services.add(service);
                    }
                }
            }
        }

        return services;
    }

    /**
     * @return The first directory in the fileName
     */
    public static String getFirstDirectory(String fileName) {
        int end = fileName.indexOf(File.separator);
        if (end != -1) {
            return fileName.substring(0, end);
        }
        end = fileName.indexOf("/");
        if (end != -1) {
            return fileName.substring(0, end);
        }
        return "";
    }

    /**
     * @param file To check
     * @return If the file is a zip file
     */
    public static boolean isZipFile(File file) {
        try {
            if (file.isDirectory()) return false;
            byte[] bytes = new byte[4];
            FileInputStream fis = new FileInputStream(file);
            if (fis.read(bytes) != 4) {
                return false;
            }
            fis.close();
            final int header = bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
            return 0x04034b50 == header;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * This method checks if the file is a binary file or a text file
     * @param file to check if it's a binary file or text file
     * @return If it's a binary file
     */
    private static boolean isBinary(File file) {
        try {
            FileInputStream fis;
            BufferedInputStream bis = new BufferedInputStream(fis = new FileInputStream(file));
            int read = bis.read();
            while (read != -1) {
                if (isMagicCharacter(read)) return true;
                read = bis.read();
            }
            bis.close();
            fis.close();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Try to find the latest file with the shortest name in a directory
     * @param directory The directory to find the file in
     * @return The newest file or null if no files were found
     */
    @Nullable
    public static File findLatestFile(File directory) {
        File latestFile = null;
        long lastModifiedTime = Long.MIN_VALUE;
        int i = 0;
        List<File> processedFiles = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files == null)
            return null;

        for (File f : files) {
            if (f.isDirectory() || !isZipFile(f))
                continue;

            if (f.getName().contains("-shadow") || f.getName().contains("-sources") || f.getName().contains("-dev"))
                continue;

            if (f.lastModified() > lastModifiedTime) {
                lastModifiedTime = f.lastModified();
                processedFiles.add(f);
            }
        }

        for (File f : processedFiles) {
            if (f.getName().length() < i || i == 0) {
                latestFile = f;
                i = f.getName().length();
            }
        }

        return latestFile;
    }

    /**
     * This method returns true if the character is a magic character that's used in binary files
     * @return If it's a magic character
     */
    private static boolean isMagicCharacter(int decimal) {
//        if (decimal > 127) return true;
//        if (decimal < 37) {
//            return decimal != 10 && decimal != 13 && decimal != 9 && decimal != 32 && decimal != 11 && decimal != 12 && decimal != 8;
//        }
//        return false;
        return decimal > 127;
    }
}
