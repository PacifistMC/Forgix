package io.github.pacifistmc.forgix.utils;

import io.github.pacifistmc.forgix.plugin.configurations.ForgixConfiguration;
import org.gradle.api.Project;

import java.io.File;

public class GradleProjectUtils {
    public static File getBestOutputFile(ForgixConfiguration.MergeLoaderConfiguration configuration, Project project) {
        if (project == null) return null;
        if (configuration.getInputJar().isPresent()) return configuration.getInputJar().get().getAsFile();
        var buildLibsFolder = project.getLayout().getBuildDirectory().dir("libs").get().getAsFile();

        File bestFile = null;
        try {
            if (!buildLibsFolder.exists() || buildLibsFolder.listFiles().length == 0) {
                return null;
            }

            // We calculate the best possible based on multiple factors and return the most probable one:
            //  Only jars
            //  File sizes, bigger file sizes are more likely to be the correct one
            //  File names, shorter file names are more likely to be the correct one
            //  Names containing -sources, -javadocs are less likely to be the correct one
            //  Timestamp, newer files are more likely to be the correct one

            double bestScore = Double.NEGATIVE_INFINITY;
            long currentTime = System.currentTimeMillis();

            for (File file : buildLibsFolder.listFiles((_, name) -> name.getExtension().equals("jar"))) {
                double score = 0;
                String fileName = file.getName().getBaseName().toLowerCase();

                // Base score from file size (normalized to prevent extremely large files from dominating)
                score += Math.log10(file.length());

                // Penalize longer filenames
                score -= fileName.length() * 0.1;

                // Heavy penalties for certain keywords
                if (fileName.contains("-sources")) score -= 100;
                if (fileName.contains("-javadoc")) score -= 100;

                // Penalize older files
                score -= (currentTime - file.lastModified()) * 0.15;

                // Bonus for files that contain the project name
                if (fileName.contains(project.getName().toLowerCase())) score += 10;

                // Update the best file if this one is better
                if (score > bestScore) {
                    bestScore = score;
                    bestFile = file;
                }
            }
        } catch (Exception e) { // This is very unlikely to happen
            "Failed to find best output file for project ${project.getName()}: ${e.getMessage()}".err();
        }
        return bestFile;
    }
}
