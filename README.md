![Forgix](https://raw.githubusercontent.com/PacifistMC/Forgix/main/assets/forgix-with-text.png)
---
Forgix is a tool that allows Minecraft modders to combine multiple plugin/modloaders into one jar!

### How does this benefit me as a regular user?
You don’t need to know much about Forgix **as this is a tool for developers**, but the mods you use may simply have a single file that you need to download, so you don’t have to worry about which modloader you’re installing for.

### Is it stable enough for production use?
Yes, in its current state, it is quite ready for production usage and has worked on all mods I’ve tested and should work on your mod as well, even if your mod’s code base is very cursed. If anything breaks, simply open an [issue on GitHub](https://github.com/PacifistMC/Forgix/issues).

### How it works
Forgix makes advantage of a JVM feature, the fact that it only loads the classes that are called; by altering the packages slightly, we can make it such that each modloader calls its own package and does not interfere with other modloaders.

So, for example, Quilt goes into `quilt.mod.json` and calls the entry-point from there, but we’ve updated the packages so that it only calls Quilt entry-points and not other modloader entry-points, and JVM will never load other classes since Quilt would never call them.

### Usage and Documentation
First apply the plugin in your **root** build.gradle

<details closed>
<summary>Applying the plugin</summary>

---
#### Groovy
Using [plugins DSL](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block):
<details closed>
<summary>Click to view</summary>

```groovy
plugins {
    id "io.github.pacifistmc.forgix" version "<version>"
}
```
</details>

Using the [legacy plugin application](https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application):
<details closed>
<summary>Click to view</summary>

```groovy
buildscript {
    repositories {
        maven {
            url "https://plugins.gradle.org/m2/"
        }
    }
    dependencies {
        classpath "io.github.pacifistmc.forgix:Forgix:<version>"
    }
}

apply plugin: "io.github.pacifistmc.forgix"
```
</details>

#### Kotlin
Using [plugins DSL](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block):
<details closed>
<summary>Click to view</summary>

```kotlin
plugins {
    id("io.github.pacifistmc.forgix") version "<version>"
}
```
</details>

Using the [legacy plugin application](https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application):
<details closed>
<summary>Click to view</summary>

```kotlin
buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("io.github.pacifistmc.forgix:Forgix:<version>")
    }
}

apply(plugin = "io.github.pacifistmc.forgix")
```
</details>

Remember to change `<version>` with the latest version! You can get the latest version from [Forgix Version](https://github.com/PacifistMC/Forgix/releases).

---
</details>

Now run `mergeJars` and it should work!\
Builds by default are generated in `build/forgix` but can be altered.

<details closed>
<summary>OPTIONAL: Configuration</summary>

---
This is an example configuration to give a general idea.

```groovy
forgix {
    destinationDirectory = layout.projectDirectory.dir("build/forgix")
    archiveClassifier = "merged"
    archiveVersion = "1.0.0"
    
    fabric()
    neoforge { // How to set a custom input jar in case the automatic detection fails
        inputJar = project(":neoforge").tasks.shadowJar.archiveFile
    }
    merge("nyaLoader") // How to add a custom modloader (note your project must be named "nyaLoader")
}
```
---
</details>

<details closed>
<summary>Documentation for each Forgix configuration</summary>

---
#### Root container ("forgix")
- `silence` (Boolean)
  - Whether to silence the thank you message.
  - Defaults to `false`.
- `autoRun` (Boolean)
  - Whether to automatically run the `mergeJars` task.
  - Defaults to `false`.
- `archiveClassifier` (String)
  - Sets the classifier for the merged archive.
  - Defaults to a string joining all the platforms.
- `archiveVersion` (String)
  - Sets the version for the merged archive.
  - Defaults to the root project's version.
- `destinationDirectory` (Directory)
  - Sets the directory where the merged jar will be placed.
  - Defaults to `build/forgix` in the root project.

##### Loader configurations
Forgix supports various modloaders and plugin platforms. For each one, you can either call the method with no arguments to use defaults, or provide a configuration block:\
By default it should automatically detect and enable them accordingly.

```groovy
forgix {
    // Simple usage with defaults
    fabric()

    // With configuration
    forge {
        inputJar = project(":forge").tasks.shadowJar.archiveFile
    }
}
```

Default platforms:
- `fabric()` - Fabric modloader
- `forge()` - Forge modloader
- `quilt()` - Quilt modloader
- `neoforge()` - NeoForge modloader
- `liteloader()` - LiteLoader modloader
- `rift()` - Rift modloader
- `plugin()` - General plugin project
- `bukkit()` - Bukkit plugin
- `spigot()` - Spigot plugin
- `paper()` - Paper plugin
- `sponge()` - Sponge plugin
- `foila()` - Foila plugin
- `bungeecoord()` - BungeeCord plugin
- `waterfall()` - Waterfall plugin
- `velocity()` - Velocity plugin

##### MergeLoaderConfiguration options
Each loader configuration accepts the following options:
- `inputJar` (RegularFileProperty)
  - Sets the input jar file to be merged.
  - If not specified, Forgix will attempt to automatically detect the jar file.

##### Generic merge method
You can also use the generic `merge()` method to specify any project:

```groovy
forgix {
    // Simple usage with defaults
    merge("customLoader")

    // With configuration
    merge("customLoader") {
        inputJar = project(":customLoader").tasks.shadowJar.archiveFile
    }
}
```

An example of a complete Forgix configuration:

```groovy
forgix {
    silence = false
    autoRun = false
    archiveClassifier = "all-platforms"
    archiveVersion = "1.0.0"
    destinationDirectory = layout.projectDirectory.dir("build/forgix")
    
    paper()
    fabric()
    forge {
      inputJar = project(":forge").tasks.remapJar.archiveFile
    }
    merge("customLoader") {
      inputJar = project(":customLoader").tasks.jar.archiveFile
    }
}
```
---
</details>

If you don’t want to run `mergeJars` manually then you can set this\
This will automatically run `mergeJars` at the end if you run the global `assemble` or `build` task.

```groovy
forgix {
    autoRun = true
}
```

### This project feels dead
Forgix is loader and minecraft independent, it is its own project and doesn't need much maintenance.\
Depending on how far in the future you are, it very well could be dead. But the chances are that it'll still work!

___
Also you should checkout this [very cool template generator](https://template.lewds.dev)!\
_Report any issues regarding the template generator [here](https://github.com/Ran-Mewo/universal-mod-template-generator)_
