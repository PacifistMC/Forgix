![Forgix](https://raw.githubusercontent.com/PacifistMC/Forgix/main/assets/forgix-with-text.png)
---
Forgix is a brand-new tool that allows Minecraft modders to combine multiple plugin/mod-loaders into one jar!

### How does this benefit me as a regular user?
You don’t need to know much about Forgix **as this is a tool for developers**, but the mods you use may simply have a single file that you need to download, so you don’t have to worry about which mod-loader you’re installing for.

### Is it stable enough for production use?
Yes, in its current state, it is quite ready for production usage and has worked on all mods I’ve tested and should work on your mod as well, even if your mod’s code base is very cursed. If anything breaks, simply open an [issue on GitHub](https://github.com/PacifistMC/Forgix/issues).  
You could wait for update `2.0.0` which is a complete rewrite of the project but it releases anytime between now and [January 1, 4096 (UTC)]().

### How it all began
Forgix began as an experiment to see if I could merge multiple mod-loaders into one; I knew it was possible _(despite the fact that a lot of people said it wasn’t)_, and after a lot of trial and error I managed to make a working prototype for semi-automatic jar merging, which was actually quite bad and was hard-coded to only work with the mod I was working on.  
After realizing that it was doable, I rewrote the entire thing so that it could be used by the public, and so Forigx was born.

### How it works
Forgix makes advantage of a JVM feature that only loads the classes that are called; by altering the packages slightly, we can make it such that each mod-loader calls its own package and does not interfere with other mod-loaders.

So, for example, Quilt goes into `quilt.mod.json` and calls the entry-point from there, but we’ve updated the packages so that it only calls Quilt entry-points and not other mod-loader entry-points, and JVM will never load other classes since Quilt would never call them.

### Usage and Documentation
> First apply the plugin in your **root** build.gradle

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

Remember to change `<version>` with the latest version! You can get the latest version from [Forgix Version](https://github.com/PacifistMC/Forgix/blob/main/version.md).

---
</details>

> Then configure it to work with your mod!  _This process is going to be automatic in the future but I haven’t gotten time to make that yet._
<details closed>
<summary>Configuring the plugin to make it work</summary>

---
This is the normal configuration that by default should work on almost all mods.

```groovy
forgix {
    group = "org.example.mod"
    mergedJarName = "example-mod"
}
```

The `group` is the common package name for your mod and the `mergedJarName` is going to be the name of the merged jar that it’s going to create, if the `mergedJarName` doesn’t have an extension then it’s going to give it the extension `jar` but keep in mind that sometimes the version number might be detected as an extension which at that point it won’t give it the extension `jar` and you’ll have to manually do that.

Running the task `mergeJars` (after running `build`) would create the merged jars in the `Merged` folder. _(In the future this might be in the `build/libs/merged` folder)_

If you don’t want to run `mergeJars` manually then you could add this. _(In the future this might be the default behavior)_

```groovy
subprojects {
    // ...
    build.finalizedBy(mergeJars)
    assemble.finalizedBy(mergeJars)
}
```
---
</details>

> Documentation for each Forgix configuration!
<details closed>
<summary>Click to view</summary>

---
#### Root container (“forgix”)
- `group` (String)
  - This is the common package name for your mod; it is usually the maven group.
  - A required value for now.
- `mergedJarName` (String)
  - This is the output jar’s name. If the name does not contain an extension, the extension `jar` is added; however, it sometimes identifies the version number as an extension and does not add it; in that case, you need to manually add the `jar` extension to the name.
  - A required value for now.
- `removeDuplicate` (String)
  - This removes a duplicate package from the merged jar. For example, if you have a core package that is replicated across all mod-loaders but doesn’t need to be then you might use this to remove the duplication.
  - This can be used more than once to remove multiple duplicates, but if there are a lot of them then it’s best to use ‘removeDuplicates’ which accepts a list.

##### Forge sub-container (“forge”)
- `projectName` (String)
  - This is the name of the Forge project. This is set to “forge” by default.
- `jarLocation` (String)
  - This is the location of the built Forge jar **from the project that’s specified in `projectName`**. By default, this retrieves the jar with the shortest name, which is quite scuffed but I don’t know how to retrieve the built jar without relying on loom or something similar, hopefully it’ll be better in the future though!
- `additionalRelocate` (String, String)
  - Simply put, this allows you to define more `group`s, which is useful for relocating libraries.
  - This can be used numerous times to specify multiple relocations.
- `mixin` (String)
  - This exists because Forge can be a real pain at times, and Forge sometimes does something strange where we can’t actually identify mixins the normal way. However, if we don’t automatically detect the mixins, then only this should be used to specify the mixins explicitly.
  - This can be used more than once to specify multiple mixins.

##### NeoForge sub-container (“neoforge”)
- `projectName` (String)
  - This is the name of the Forge project. This is set to “neoforge” by default.
- `jarLocation` (String)
  - This is the location of the built NeoForge jar **from the project that’s specified in `projectName`**. By default, this retrieves the jar with the shortest name, which is quite scuffed but I don’t know how to retrieve the built jar without relying on loom or something similar, hopefully it’ll be better in the future though!
- `additionalRelocate` (String, String)
  - Simply put, this allows you to define more `group`s, which is useful for relocating libraries.
  - This can be used numerous times to specify multiple relocations.
- `mixin` (String)
  - This exists because NeoForge can be a real pain at times, and NeoForge sometimes does something strange where we can’t actually identify mixins the normal way. However, if we don’t automatically detect the mixins, then only this should be used to specify the mixins explicitly.
  - This can be used more than once to specify multiple mixins.

##### Quilt sub-container (“quilt”)
- `projectName` (String)
  - This is the name of the Quilt project. This is set to “quilt” by default.
- `jarLocation` (String)
  - This is the location of the built Quilt jar **from the project that’s specified in `projectName`**. By default, this retrieves the jar with the shortest name, which is quite scuffed but I don’t know how to retrieve the built jar without relying on loom or something similar, hopefully it’ll be better in the future though!
- `additionalRelocate` (String, String)
  - Simply put, this allows you to define more `group`s, which is useful for relocating libraries.
  - This can be used more than once to specify multiple relocations.

##### Fabric sub-container (“fabric”)
- `projectName` (String)
  - This is the name of the Fabric project. This is set to “fabric” by default.
- `jarLocation` (String)
  - This is the location of the built Fabric jar **from the project that’s specified in `projectName`**. By default, this retrieves the jar with the shortest name, which is quite scuffed but I don’t know how to retrieve the built jar without relying on loom or something similar, hopefully it’ll be better in the future though!
- `additionalRelocate` (String, String)
  - Simply put, this allows you to define more `group`s, which is useful for relocating libraries.
  - This can be used more than once to specify multiple relocations.

##### Custom sub-container (“custom”)
Because I’m not going to develop a new container for each mod-loader, this is the one that handles everything else. This can't handle Forge-like modloaders though due to Forge being weird and cursed. This configuration can be used more than once to specify multiple loaders.
- `projectName` (String)
  - This is the name of the project.
  - This is a required value.
- `jarLocation` (String)
  - This is the location of the built jar **from the project that’s specified in `projectName`**. By default, this retrieves the jar with the shortest name, which is quite scuffed but I don’t know how to retrieve the built jar without relying on loom or something similar, hopefully it’ll be better in the future though!
- `additionalRelocate` (String, String)
  - Simply put, this allows you to define more `group`s, which is useful for relocating libraries.
  - This can be used more than once to specify multiple relocations.

An example of a complete Forgix configuration:

```groovy
forgix {
    group = "org.example.mod" // (Required Value)
    mergedJarName = "example-mod" // (Required Value)
    outputDir = "build/libs/merged"
    
    forge {
        projectName = "forge"
        jarLocation = "build/libs/example-mod.jar"

        additionalRelocate "org.my.lib" "forge.org.my.lib"
        additionalRelocate "org.my.lib.another" "forge.org.my.lib.another"
        
        mixin "forge.mixins.json"
        mixin "forge.mixins.another.json"
    }

    neoforge {
      projectName = "neoforge"
      jarLocation = "build/libs/example-mod.jar"
  
      additionalRelocate "org.my.lib" "neoforge.org.my.lib"
      additionalRelocate "org.my.lib.another" "neoforge.org.my.lib.another"
  
      mixin "neoforge.mixins.json"
      mixin "neoforge.mixins.another.json"
    }
    
    fabric {
        projectName = "fabric"
        jarLocation = "build/libs/example-mod.jar"
        
        additionalRelocate "org.my.lib" "fabric.org.my.lib"
        additionalRelocate "org.my.lib.another" "fabric.org.my.lib.another"
    }
    
    quilt {
        projectName = "quilt"
        jarLocation = "build/libs/example-mod.jar"
        
        additionalRelocate "org.my.lib" "quilt.org.my.lib"
        additionalRelocate "org.my.lib.another" "quilt.org.my.lib.another"
    }

    custom {
        projectName = "sponge" // (Required Value)
        jarLocation = "build/libs/example-mod.jar"
        
        additionalRelocate "org.my.lib" "sponge.org.my.lib"
        additionalRelocate "org.my.lib.another" "sponge.org.my.lib.another"
    }

    custom {
        projectName = "spigot" // (Required Value)
        jarLocation = "build/libs/example-mod.jar"

        additionalRelocate "org.my.lib" "spigot.org.my.lib"
        additionalRelocate "org.my.lib.another" "spigot.org.my.lib.another"
    }
    
    removeDuplicate "org.example.mod.core"
}
```
---
</details>

### This project feels dead
Depending on how far in the future you are, it very well could be. I am not going to update this every day; all future updates will be bug fixes for issues I haven’t found, quality of life improvements, or resolving that one Minecraft mod that won’t work due to how cursed its codebase is.
If it works, it works
