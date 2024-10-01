package io.github.pacifistmc.forgix.plugin;

import groovy.lang.Closure;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.pacifistmc.forgix.plugin.ForgixPlugin.rootProject;

// I couldn't find any good resources on how to do this, so I just went with it and wrote a lot of dumb code.
@SuppressWarnings("InnerClassMayBeStatic")
public class ForgixMergeExtension {
	String group;
	
	/**
	 * Note:Require with postfix: `jar`
	 */
	String mergedJarName;
	@NotNull List<String> removeDuplicates = new LinkedList<>();
	
	String outputDir = "Merged";
	
	public ForgixMergeExtension() {
		if (rootProject.hasProperty("maven_group")) {
			Object maven_group = rootProject.property("maven_group");
			if (maven_group != null) group = maven_group.toString();
		}
		if (rootProject.hasProperty("archives_base_name")) {
			Object archives_base_name = rootProject.property("archives_base_name");
			if (archives_base_name != null) mergedJarName = archives_base_name.toString();
			
			if (rootProject.hasProperty("mod_version")) {
				Object mod_version = rootProject.property("mod_version");
				if (mod_version != null) mergedJarName += "-" + mod_version;
			}
			mergedJarName += ".jar";
		}
	}
	
	public String getGroup() {
		return Objects.requireNonNull(group, "No `maven_group` property found! Please configure `group` manually!\n"
				+ "Check out on how to configure `group`: " + "https://github.com/PacifistMC/Forgix#configuration");
	}
	
	public void setGroup(String group) {
		this.group = group;
	}
	
	public String getMergedJarName() {
		return Objects.requireNonNull(mergedJarName, "No archives_base_name` or `mod_version` property found! Please configure `mergedJarName` manually!\n"
				+ "Check out on how to configure `mergedJarName`: https://github.com/PacifistMC/Forgix#configuration");
	}
	
	public void setMergedJarName(String mergedJarName) {
		//doesn't have an extension
		if (FilenameUtils.isExtension(mergedJarName)) mergedJarName += ".jar";
		this.mergedJarName = mergedJarName;
	}
	
	public String getOutputDir() {
		return outputDir;
	}
	
	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}
	
	//Containers Configure
	
	Set<ForgixContainer> containers = new LinkedHashSet<>();
	
	public ForgeContainer forge(Closure<?> closure) {
		return configure(new ForgeContainer(), closure);
	}
	
	public NeoForgeContainer neoforge(Closure<?> closure) {
		return configure(new NeoForgeContainer(), closure);
	}
	
	public FabricContainer fabric(Closure<?> closure) {
		return configure(new FabricContainer(), closure);
	}
	
	public QuiltContainer quilt(Closure<?> closure) {
		return configure(new QuiltContainer(), closure);
	}
	
	public ForgixContainer custom(Closure<?> closure) {
		return configure(new ForgixContainer(), closure);
	}
	
	private <T extends ForgixContainer> T configure(T container, Closure<?> closure) {
		rootProject.configure(container, closure);
		Objects.requireNonNull(container.getProjectName(),
				"For the custom loader you have to specify the `projectName`!\n"
						+ "Check out on how to configure `projectName`: `https://github.com/PacifistMC/Forgix#configuration`");
		boolean added = containers.add(container);
		if (!added) {
			throw new IllegalStateException("Duplicate project reference" + container.getProjectName());
		}
		return container;
	}
	
	public Set<ForgixContainer> getContainers() {
		return containers;
	}
	
	public @NotNull List<String> getRemoveDuplicates() {
		return removeDuplicates;
	}
	
	public void removeDuplicate(String duplicate) {
		removeDuplicates.add(duplicate);
	}
	
	public void removeDuplicates(List<String> duplicates) {
		removeDuplicates.addAll(duplicates);
	}
	
	//Only LexForge still use META-INF to declare Mixin now
	public class ForgeContainer extends ForgixContainer {
		List<String> mixins;
		
		public List<String> getMixins() {
			return mixins;
		}
		
		public void mixin(String mixin) {
			if (this.mixins == null) this.mixins = new ArrayList<>();
			this.mixins.add(mixin);
		}
		
		{
			setProjectName("forge");
		}
	}
	
	public class NeoForgeContainer extends ForgixContainer {
		{
			setProjectName("neoforge");
		}
	}
	
	public class FabricContainer extends ForgixContainer {
		{
			setProjectName("fabric");
		}
	}
	
	public class QuiltContainer extends ForgixContainer {
		{
			setProjectName("quilt");
		}
	}
	
	public class ForgixContainer {
		
		private String projectName;
		private @Nullable String jarLocation;
		private @NotNull Map<String, String> additionalRelocates = new HashMap<>();
		
		public String getProjectName() {
			return projectName;
		}
		
		//FIXME Currently, only one layer of subprojects is supported
		// forge->forge.xxx but forge:neoforge->EXCEPTION
		public String getTransformedPrefix() {
			return getProjectName();
		}
		
		public void setProjectName(String projectName) {
			Objects.requireNonNull(projectName, "project reference can't be null!");
			this.projectName = projectName.startsWith(":") ? projectName.substring(1) : projectName;
		}
		
		public @Nullable String getJarLocation() {
			return jarLocation;
		}
		
		
		public void setJarLocation(@Nullable String jarLocation) {
			this.jarLocation = jarLocation;
		}
		
		public @NotNull Map<String, String> getAdditionalRelocates() {
			return additionalRelocates;
		}
		
		public void additionalRelocate(String from, String to) {
			this.additionalRelocates.put(from, to);
		}
		
		@Override
		public final boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof ForgixContainer)) return false;
			
			ForgixContainer that = (ForgixContainer) o;
			return Objects.equals(projectName, that.projectName);
		}
		
		@Override
		public int hashCode() {
			return Objects.hashCode(projectName);
		}
	}
}
