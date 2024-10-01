package io.github.pacifistmc.forgix;

import fr.stevecohen.jarmanager.JarPacker;
import fr.stevecohen.jarmanager.JarUnpacker;
import io.github.pacifistmc.forgix.plugin.ForgixMergeExtension.ForgixContainer;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.pacifistmc.forgix.Forgix.version;
import static io.github.pacifistmc.forgix.utils.ForgixFileUtils.*;

/**
 * Created in 2024/10/1 13:44
 * Project: Forgix
 **/
// This is the class that does the magic.
public class Merge {

//	private File forgeJar;
//	private Map<String, String> forgeRelocations;
//	private List<String> forgeMixins;
//	private File neoforgeJar;
//	private Map<String, String> neoforgeRelocations;
//	private List<String> neoforgeMixins;
//	private File fabricJar;
//	private Map<String, String> fabricRelocations;
//	private File quiltJar;
//	private Map<String, String> quiltRelocations;
	
	public static final String ARCHITECTURY_INJECT = "architectury_inject";
	public static final String MANIFEST_FORGE_MIXIN_CONFIG_KEY = "MixinConfigs";
	private final Logger logger;
	
	private final Set<ForgixContainer> containers;
	/**
	 * original jars
	 */
	private final Map<ForgixContainer, File> libs;
	private final Map<ForgixContainer, File> subTempDirs = new HashMap<>();
	private final Map<ForgixContainer, File> remappedJars = new HashMap<>();
	private final String group;
	private final File tempDir;
	private final String mergedJarName;
	private final List<String> removeDuplicates;
	private final Map<String, String> removeDuplicateRelocations = new HashMap<>();
	private final Map<String, String> removeDuplicateRelocationResources = new HashMap<>();
	
	//public Merge(@Nullable File forgeJar, Map<String, String> forgeRelocations, List<String> forgeMixins, @Nullable File neoforgeJar, Map<String, String> neoforgeRelocations, List<String> neoforgeMixins, @Nullable File fabricJar, Map<String, String> fabricRelocations, @Nullable File quiltJar, Map<String, String> quiltRelocations, Map<ForgixMergeExtension.CustomContainer, File> customContainerMap, String group, File tempDir, String mergedJarName, List<String> removeDuplicates, Logger logger) {
	public Merge(Set<ForgixContainer> containers,
	             Map<ForgixContainer, File> libs,
	             String group, File tempDir, String mergedJarName, List<String> removeDuplicates, Logger logger) {
		this.logger = logger;
		this.containers = containers;
		this.libs = libs;
		this.group = group;
		this.tempDir = tempDir;
		this.mergedJarName = mergedJarName;
		this.removeDuplicates = removeDuplicates;
		containers.forEach((container) -> subTempDirs.put(container, new File(tempDir, container.getProjectName())));
	}
	
	public String repr() {
		StringBuilder builder = new StringBuilder();
		builder.append("ForgixSettings:")
				.append("\n\tProjectsAttached:");
		containers.forEach((container) -> {
			builder.append("\n\t\t").append(container.getProjectName())
					.append("\n\t\t\tJarLocation: ").append(container.getJarLocation())
					.append("\n\t\t\tPackagePrefix: ").append(container.getTransformedPrefix());
		});
		builder.append("\n\tGroup: ").append(group)
				.append("\n\tMergedJarName: ").append(mergedJarName);
		return builder.toString().replace("\t", "  ");
	}
	
	/**
	 * This is the main merge method
	 *
	 * @return The merged jar file
	 * @throws IOException If something went wrong
	 */
	//since you run the task, it means you want to get a complete new merged jar, not an old one
	public File merge() throws IOException {
		logger.warn("\nForgix is still very new so refer any issues that you might encounter to `https://github.com/PacifistMC/Forgix/issues`");
		logger.warn(repr());
		
		if (libs.isEmpty()) {
			throw new IllegalArgumentException("No jars were provided.");
		}
		//Create temp zone
		if (tempDir.exists()) {
			FileUtils.deleteQuietly(tempDir);
		}
		subTempDirs.values().forEach(File::mkdirs);
		File mergedJar = new File(tempDir, mergedJarName);
		
		primaryRemap();
		
		//Prepare merge jars
		JarUnpacker jarUnpacker = new JarUnpacker();
		remappedJars.forEach((container, jar) -> {
			//all remapped jars will be unpacked to its own zone
			jarUnpacker.unpack(jar.getAbsolutePath(), subTempDirs.get(container).getAbsolutePath());
			jar.delete();//prevent repack to final-artifact
		});
		//to store merged,unpacked jars
		File mergedTemps = new File(tempDir, "merged-temps");
		mergedTemps.mkdirs();
		
		//Merge Manifests
		Manifest mergedManifest = mergeManifests();
		
		//for LexForgeMixin, NeoForge needn't it
		String mixinConfigs = mergedManifest.getMainAttributes().getValue(MANIFEST_FORGE_MIXIN_CONFIG_KEY);
		if (mixinConfigs != null) {
			List<String> remappedMixin = new LinkedList<>();
			String[] mixins = mixinConfigs.split(",");
			for (String mixin : mixins) {
				remappedMixin.add("forge-" + mixin);
			}
			mergedManifest.getMainAttributes().putValue(MANIFEST_FORGE_MIXIN_CONFIG_KEY, String.join(",", remappedMixin));
		}
		
		mergedManifest.getMainAttributes().putValue(Forgix.MANIFEST_VERSION_KEY, version);
		
		remapResources();
		
		//Cleanup extra META-INF
		subTempDirs.forEach((containers, dir) -> {
			getManifestFile(dir).delete();
		});
		//Store merged META-INF
		try (FileOutputStream outputStream = new FileOutputStream(createManifestFile(mergedTemps))) {
			mergedManifest.write(outputStream);
		}
		//Merge!
		for (Map.Entry<ForgixContainer, File> entry : subTempDirs.entrySet()) {
			FileUtils.copyDirectory(entry.getValue(), mergedTemps);
		}
		//Pack it
		JarPacker jarPacker = new JarPacker();
		jarPacker.pack(mergedTemps.getAbsolutePath(), mergedJar.getAbsolutePath());
		
		File dupeTemps = new File(mergedTemps.getParentFile(), "duplicate-temps");
		dupeTemps.mkdirs();
		//? pack, then unpack it again
		
		//collect duplicates
		setupDuplicates();
		removeDuplicate(mergedJar, new File(tempDir, mergedJarName + ".duplicate.remover"), mergedTemps, dupeTemps);
		FileUtils.deleteQuietly(mergedTemps);
		jarUnpacker.unpack(mergedJar.getAbsolutePath(), mergedTemps.getAbsolutePath());
		removeDuplicateResources(mergedTemps, dupeTemps);
		
		//repack
		FileUtils.deleteQuietly(mergedJar);
		jarPacker.pack(mergedTemps.getAbsolutePath(), mergedJar.getAbsolutePath());
		//cleanup
//		FileUtils.deleteQuietly(tempDir);
		//Complete!
		// *★,°*:.☆(￣▽￣)/$:*.°★* 。❀
		return mergedJar;
	}
	
	private Manifest mergeManifests() throws IOException {
		Manifest mergedManifest = new Manifest();
		List<Manifest> manifests = new LinkedList<>();
		for (Map.Entry<ForgixContainer, File> e : subTempDirs.entrySet()) {
			ForgixContainer container = e.getKey();
			File subTempDir = e.getValue();
			File spec = new File(subTempDir, "META-INF/MANIFEST.MF");
			if (spec.exists()) {
				try (FileInputStream fis = new FileInputStream(spec)) {
					Manifest manifest = new Manifest();
					manifest.read(fis);
					manifests.add(manifest);
				}
			} else {
				logger.warn("MANIFEST.MF not found in project {}", container.getProjectName());
			}
		}
		for (Manifest manifest : manifests) {
			manifest.getMainAttributes().forEach((key, value) -> {
				String prev = mergedManifest.getMainAttributes().putValue(key.toString(), value.toString());
				if (prev != null) {
					logger.warn("replace the manifest value of `{}` from `{}` to `{}` ", key, prev, value);
				}
			});
		}
		return mergedManifest;
	}
	
	/**
	 * This is the method that remaps the bytecode
	 * We do this remapping in order to not get any conflicts
	 */
	private void primaryRemap() throws IOException {
		for (Map.Entry<ForgixContainer, File> lib : libs.entrySet()) {
			ForgixContainer container = lib.getKey();
			File jar = lib.getValue();
			String packagePrefix = container.getTransformedPrefix();
			
			File subTemp = subTempDirs.get(container);
			File remappedJar = new File(subTemp, "remapped.jar");
			remappedJar.createNewFile();
			
			//Collect Relocations
			List<Relocation> relocations = new LinkedList<>();
			relocations.add(new Relocation(group, packagePrefix + "." + group));
			container.getAdditionalRelocates().forEach((from, dest) -> {
				relocations.add(new Relocation(from, dest));
			});
			
			//Architectury
			try (JarFile jarFile = new JarFile(jar)) {
				jarFile.stream().forEach(entry -> {
					if (entry.isDirectory()) {
						String packageName = entry.getName();
						if (packageName.startsWith(ARCHITECTURY_INJECT)) {
							relocations.add(new Relocation(packageName, packagePrefix + "." + packageName));
						}
					}
				});
			}
			JarRelocator relocator = new JarRelocator(jar, remappedJar, relocations);
			relocator.run();
			remappedJars.put(container, remappedJar);
		}
	}
	
	/**
	 * This is the second remapping method
	 * This basically remaps all resources such as mixins, manifestJars, etc.
	 * This method also finds all the forge/neoforge mixins for you if not detected
	 *
	 * @throws IOException If something went wrong
	 */
	private void remapResources() throws IOException {
		for (Map.Entry<ForgixContainer, File> entry : subTempDirs.entrySet()) {
			ForgixContainer container = entry.getKey();
			File temps = entry.getValue();
			Map<String, String> relocates = new HashMap<>(container.getAdditionalRelocates());
			String prefix = container.getTransformedPrefix();
			
			transform(manifestJars(temps), prefix + "-", relocates);
			transform(listAllPlatformServices(temps, group), prefix + ".", relocates);
			transform(listAllMixins(temps, true), prefix + "-", relocates);
			transform(listAllRefmaps(temps), prefix + "-", relocates);
			relocates.put(group, prefix + "." + group);
			relocates.put(group.replace(".", "/"), prefix + "/" + group.replace(".", "/"));
			
			replaceAllTextFiles(temps, relocates);
		}
	}
	
	private void transform(List<File> files, String prefix, Map<String, String> relocates) {
		for (File file : files) {
			File remappedFile = new File(file.getParentFile(), prefix + file.getName());
			relocates.put(file.getName(), remappedFile.getName());
			file.renameTo(remappedFile);
		}
	}
	
	
	private void setupDuplicates() {
		for (String duplicate : removeDuplicates) {
			String duplicatePath = duplicate.replace(".", "/");
			for (ForgixContainer container : containers) {
				String prefix = container.getTransformedPrefix();
				removeDuplicateRelocations.put(prefix + "." + duplicate, duplicate);
				removeDuplicateRelocationResources.put(prefix + "/" + duplicatePath, duplicatePath);
			}
		}
		removeDuplicateRelocationResources.putAll(removeDuplicateRelocations);
	}
	
	/**
	 * This method removes the duplicates specified in resources
	 */
	private void removeDuplicateResources(File mergedTemps, File duplicateTemp) throws IOException {
		try (Stream<Path> pathStream = Files.list(duplicateTemp.toPath())) {
			for (Path path : pathStream.collect(Collectors.toList())) {
				File file = path.toFile();
				if (file.isDirectory()) {
					FileUtils.copyDirectory(file, mergedTemps);
				} else {
					FileUtils.copyFileToDirectory(file, mergedTemps);
				}
			}
		}
		replaceAllTextFiles(mergedTemps, removeDuplicateRelocationResources);
	}
	
	/**
	 * This method removes the duplicates specified
	 */
	private void removeDuplicate(File mergedJar, File mergedOutputJar, File mergedTemps, File duplicateTemp) throws IOException {
		// Have to do it this way cause there's a bug with jar-relocator where it doesn't accept duplicate values
		while (!removeDuplicateRelocations.isEmpty()) {
			Map.Entry<String, String> removeDuplicate = removeDuplicateRelocations.entrySet().stream().findFirst().get();
			try (ZipFile zipFile = new ZipFile(mergedJar)) {
				try {
					zipFile.extractFile(removeDuplicate.getValue().replace(".", "/") + "/", duplicateTemp.getPath());
				} catch (ZipException e) {
					if (!e.getType().equals(ZipException.Type.FILE_NOT_FOUND)) {
						throw e;
					}
				}
			}
			
			if (mergedOutputJar.exists()) mergedOutputJar.delete();
			mergedOutputJar.createNewFile();
			
			List<Relocation> relocations = new ArrayList<>();
			
			relocations.add(new Relocation(removeDuplicate.getKey(), removeDuplicate.getValue()));
			
			JarRelocator jarRelocator = new JarRelocator(mergedJar, mergedOutputJar, relocations);
			jarRelocator.run();
			
			Files.move(mergedOutputJar.toPath(), mergedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
			
			if (mergedOutputJar.exists()) mergedOutputJar.delete();
			removeDuplicateRelocations.remove(removeDuplicate.getKey(), removeDuplicate.getValue());
			
			try (ZipFile zipFile = new ZipFile(mergedJar)) {
				zipFile.extractFile(removeDuplicate.getValue().replace(".", "/") + "/", new File(mergedTemps.getParentFile(), "duplicate-temps" + File.separator + removeDuplicate.getValue() + File.separator).getPath());
				if (removeDuplicateRelocations.containsValue(removeDuplicate.getValue())) {
					zipFile.removeFile(removeDuplicate.getValue().replace(".", "/") + "/");
				}
			}
		}
	}
}
