# Forgix
A Gradle plugin/an Architectury addon to merge Fabric (also Quilt) &amp; Forge jars into one! 𝘸𝘢𝘩𝘩 𝘵𝘦𝘤𝘩𝘯𝘰𝘭𝘰𝘨𝘺

### Usage:
_This would probably be moved to wikis and will have a proper usage documentation after gradle accepted the plugin but for now here's a quick documentation on how to use this plugin_
####
By default just running the task "mergeJars" should work. Though don't forget to build the jars first!
```groovy
plugins {
    id 'io.github.pacifistmc.forgix' // Note: You still cannot use the plugin since gradle didn't accept the plugin yet :(
}

forgix {
    group = "org.example.mod" // This is the common group of the mod which by default in Architectury Template it's defined as "maven_group" in your gradle.properties. If this property is not defined then by default it'll fetch the group from the maven_group property in your gradle.properties
    mergedJarName = "example-mod" // This is the name of the merged jar. If this property is not defined then by default it'll fetch the "archives_base_name" property with the "mod_version" property in your gradle.properties.
    
    forge {
        projectName = "forge" // This is the name of the forge project. If this property is not defined then by default it'll set to "forge" since that's the name the Architectury Template uses.
        jarLocation = "build/libs/example-mod.jar" // This is the location of the forge jar from the forge project. If this property is not defined then by default it fetches the jar with the shortest name.

        additionalRelocate "org.my.lib" "forge.org.my.lib" // This is an important one to know. This is how you can remap additional packages such as libraries and stuff.
        additionalRelocate "org.my.lib.another" "forge.org.my.lib.another"
        
        mixin "forge.mixins.json" // This is in case if we didn't auto detect the forge mixins.
        mixin "forge.mixins.another.json"
    }
    
    fabric {
        projectName = "fabric" // This is the name of the fabric project. If this property is not defined then by default it'll set to "fabric" since that's the name the Architectury Template uses.
        jarLocation = "build/libs/example-mod.jar" // This is the location of the fabric jar from the fabric project. If this property is not defined then by default it fetches the jar with the shortest name.
        
        additionalRelocate "org.my.lib" "fabric.org.my.lib" // This is an important one to know. This is how you can remap additional packages such as libraries and stuff.
        additionalRelocate "org.my.lib.another" "fabric.org.my.lib.another"
    }
    
    quilt {
        projectName = "quilt" // This is the name of the quilt project. If this property is not defined then by default it'll set to "quilt" since that's the name the Architectury Template uses.
        jarLocation = "build/libs/example-mod.jar" // This is the location of the quilt jar from the quilt project. If this property is not defined then by default it fetches the jar with the shortest name.
        
        additionalRelocate "org.my.lib" "quilt.org.my.lib" // This is an important one to know. This is how you can remap additional packages such as libraries and stuff.
        additionalRelocate "org.my.lib.another" "quilt.org.my.lib.another"
    }
}
```
Note: You'll have to do the configuration in the root build.gradle
#
#### How did I come up with the name "Forgix"?
Well an AI generated it for me
_and also gave me the logo for it_
