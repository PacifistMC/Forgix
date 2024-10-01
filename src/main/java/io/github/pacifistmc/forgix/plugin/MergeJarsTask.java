package io.github.pacifistmc.forgix.plugin;

import io.github.pacifistmc.forgix.Forgix;
import io.github.pacifistmc.forgix.Merge;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.pacifistmc.forgix.plugin.ForgixPlugin.rootProject;
import static io.github.pacifistmc.forgix.plugin.ForgixPlugin.settings;
import static io.github.pacifistmc.forgix.utils.ForgixFileUtils.findLatestFile;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class MergeJarsTask extends DefaultTask {
	@TaskAction
	void mergeJars() throws IOException {
		long time = System.currentTimeMillis();
		Logger logger = rootProject.getLogger();
		if (settings.mergedJarName == null || settings.group == null) {
			logger.error("Please configure `group` and `mergedJarName` manually!");
			logger.info("Check out how to configure them here: " + "https://github.com/PacifistMC/Forgix#configuration");
			return;
		}
		File dest = new File(rootProject.getRootDir(), settings.getOutputDir());
//		getArchiveFileName().set(settings.mergedJarName);
//		getDestinationDirectory().set(dest);
		//Prepare Stage
		
		//collect containers
		Map<ForgixMergeExtension.ForgixContainer, Project> containers = settings.getContainers().stream()
				.collect(Collectors.toMap(c -> c, c -> (rootProject.project(c.getProjectName()))));
		if (containers.isEmpty()) {
			logger.error("No projects were found. Skipping mergeJar task.");
			return;
		} else if (containers.size() == 1) {
			logger.error("Only one project was found. Skipping mergeJar task.");
			return;
		}
		
		//file existence checked
		Map<ForgixMergeExtension.ForgixContainer, File> libs = containers.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> {
							String jarLocation = e.getKey().getJarLocation();
							if (jarLocation != null) {
								Project project = e.getValue();
								File file = new File(project.getProjectDir(), jarLocation);
								if (!file.exists()) {
									throw new IllegalArgumentException("Unable to access jar file in " + file.getAbsoluteFile());
								}
								return file;
							} else {
								File defaultPath = new File(rootProject.getBuildDir(), "libs");
								return Objects.requireNonNull(findLatestFile(defaultPath), () -> "no jars found in default path: " + defaultPath.getAbsoluteFile());
							}
						}));
		
		File mergedJar = new File(dest, File.separator + settings.getMergedJarName());
		if (!mergedJar.getParentFile().exists()) mergedJar.getParentFile().mkdirs();

//		Path tempMergedJarPath = new Forgix.Merge(forgeJar, forgeSettings.getAdditionalRelocates(), forgeSettings.getMixins(), neoforgeJar, neoforgeSettings.getAdditionalRelocates(), neoforgeSettings.getMixins(), fabricJar, fabricSettings.getAdditionalRelocates(), quiltJar, quiltSettings.getAdditionalRelocates(), customJars, settings.getGroup(), new File(rootProject.getRootDir(), ".gradle" + File.separator + "forgix"), settings.getMergedJarName(), settings.getRemoveDuplicates(), logger).merge(false).toPath();
		Path tempMergedJarPath = new Merge(containers.keySet(), libs, settings.getGroup(), new File(rootProject.getRootDir(), ".gradle" + File.separator + "forgix"), settings.getMergedJarName(), settings.getRemoveDuplicates(), logger)
				.merge().toPath();
		Files.move(tempMergedJarPath, mergedJar.toPath(), REPLACE_EXISTING);
		
		try {
			Files.setPosixFilePermissions(mergedJar.toPath(), Forgix.perms);
		} catch (Exception e) {
			logger.debug("unable to set file permission", e);
		}
		
		logger.info("merged jar created in {} seconds.", (System.currentTimeMillis() - time) / 1000.0);
	}
	
	protected CopyAction createCopyAction() {
		return null;
	}
}
