@file:Suppress("AvoidApplyPluginMethod","UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    java
    alias(libs.plugins.architectury)
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.spotless)
}

val modId = project.property("modId") as String
val modVersion = project.property("modVersion") as String
val mavenGroup = project.property("mavenGroup") as String
val enabledPlatforms = project.property("enabledPlatforms") as String
val minecraftVersion: String = libs.versions.minecraft.get()

subprojects {
    apply(plugin = "java")
    apply(plugin = "pmd")
    apply(plugin = rootProject.libs.plugins.architectury.get().pluginId)
    apply(plugin = rootProject.libs.plugins.loom.get().pluginId)

    version = "${modVersion}+${gitRef()}"
    group = mavenGroup
    base.archivesName.set("${modId}-MC${minecraftVersion}-${project.name}")

    architectury {
        minecraft = minecraftVersion
    }

    configure<LoomGradleExtensionAPI> {
        silentMojangMappingsLicense()
    }

    repositories {
        exclusiveContent {
            forRepository { maven("https://maven.parchmentmc.org") }
            filter { includeGroupByRegex("org\\.parchmentmc.*") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.blamejared.com") }
            filter { includeGroup("mezz.jei") }
        }
        exclusiveContent {
            forRepository { maven("https://fnuecke.github.io/maven") }
            filter {
                includeModule("li.cil.sedna", "sedna-buildroot")
                includeModule("li.cil.sedna", "sedna-cpm")
                includeModule("li.cil.vox2mc", "vox2mc")
                includeGroup("li.cil.markdown_manual")
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "Modrinth"
                    url = uri("https://api.modrinth.com/maven")
                }
            }
            filter { includeGroup("maven.modrinth") }
        }
        mavenCentral()
    }

    dependencies {
        "minecraft"(rootProject.libs.minecraft)
        val loom = project.extensions.getByName<LoomGradleExtensionAPI>("loom")
        "mappings"(loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-${rootProject.libs.versions.parchment.minecraft.get()}:${rootProject.libs.versions.parchment.mappings.get()}@zip")
        })
        "compileOnly"("com.google.code.findbugs:jsr305:3.0.2")
    }

    configureJava()
    configurePmd("**/jcodec/**")
    embedLicenses("LICENSE-JCODEC")
    configureIdeaExcludes()
}

val projectConfigurations = mapOf(
    "fabric" to "Fabric",
    "neoforge" to "NeoForge"
)

for (platform in enabledPlatforms.split(',')) {
    project(":$platform") {
        apply(plugin = rootProject.libs.plugins.shadow.get().pluginId)

        architectury {
            platformSetupLoomIde()
            loader(platform)
        }

        val common = configurations.create("common")
        val shadowBundle = configurations.create("shadowBundle")

        configurations {
            common.isCanBeResolved = true
            common.isCanBeConsumed = false

            compileClasspath.get().extendsFrom(common)
            runtimeClasspath.get().extendsFrom(common)
            getByName("development${projectConfigurations[platform]}").extendsFrom(common)

            shadowBundle.isCanBeResolved = true
            shadowBundle.isCanBeConsumed = false
            shadowBundle.isTransitive = false
        }

        dependencies {
            common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
            shadowBundle(
                project(
                    path = ":common",
                    configuration = "transformProduction${projectConfigurations[platform]}"
                )
            ) { isTransitive = false }
        }

        tasks {
            withType<ShadowJar> {
                exclude("architectury.common.json")
                configurations = listOf(shadowBundle)
                archiveClassifier.set("dev-shadow")
            }

            withType<RemapJarTask> {
                val shadowJarTask = getByName<ShadowJar>("shadowJar")
                inputFile.set(shadowJarTask.archiveFile)
                dependsOn(shadowJarTask)
                archiveClassifier.set(null as String?)
            }

            jar {
                archiveClassifier.set("dev")
            }
        }

        (components["java"] as AdhocComponentWithVariants)
            .withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
                skip()
            }
    }
}

for (extraModule in listOf("instrumentation", "gametest")) {
    for (platform in enabledPlatforms.split(',')) {
        project(":$extraModule-$platform") {
            architectury {
                platformSetupLoomIde()
                loader(platform)
            }

            val common = configurations.create("common")
            val bundle = configurations.create("bundle")

            configurations {
                common.isCanBeResolved = true
                common.isCanBeConsumed = false

                compileClasspath.get().extendsFrom(common)
                runtimeClasspath.get().extendsFrom(common)
                getByName("development${projectConfigurations[platform]}").extendsFrom(common)

                bundle.isCanBeResolved = true
                bundle.isCanBeConsumed = false
            }

            dependencies {
                common(project(path = ":$extraModule-common", configuration = "namedElements")) { isTransitive = false }
                bundle(
                    project(
                        path = ":$extraModule-common",
                        configuration = "transformProduction${projectConfigurations[platform]}"
                    )
                ) { isTransitive = false }
            }

            tasks.jar {
                val bundleFiles = configurations["bundle"]
                dependsOn(bundleFiles)
                from(bundleFiles.elements.map { files -> files.map { zipTree(it) } }) {
                    exclude("architectury.common.json", "META-INF/MANIFEST.MF")
                }
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }
}

val cmakeExecutable = providers.gradleProperty("cmakeExecutable").orNull
    ?: listOf("/opt/homebrew/bin/cmake", "/usr/local/bin/cmake", "cmake")
        .first { it == "cmake" || file(it).canExecute() }
val jdkHome = System.getProperty("java.home")
val nativeArchitecture = when (System.getProperty("os.arch").lowercase()) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> error("Unsupported native-library architecture: ${System.getProperty("os.arch")}")
}
val (nativePlatform, nativeLibraryFileName) = when (System.getProperty("os.name").lowercase()) {
    "mac os x", "macos" -> "macos-$nativeArchitecture" to "liboc2slirp.dylib"
    else -> when {
        System.getProperty("os.name").lowercase().contains("win") ->
            "windows-$nativeArchitecture" to "oc2slirp.dll"
        System.getProperty("os.name").lowercase().contains("linux") ->
            "linux-$nativeArchitecture" to "liboc2slirp.so"
        else -> error("Unsupported native-library operating system: ${System.getProperty("os.name")}")
    }
}

val nativeLibConfigure = tasks.register<Exec>("nativeLibConfigure") {
    group = "native"
    description = "Configures the native libslirp CMake build."

    workingDir(rootProject.projectDir)
    environment("JAVA_HOME", jdkHome)

    commandLine(
        cmakeExecutable,
        "-S", "native/libslirp",
        "-B", "build/native",
        "-DCMAKE_BUILD_TYPE=Release"
    )
}

val nativeLibCompile = tasks.register<Exec>("nativeLibCompile") {
    group = "native"
    description = "Compile the native libslirp CMake build."

    dependsOn(nativeLibConfigure)
    workingDir(rootProject.projectDir)
    environment("JAVA_HOME", jdkHome)

    commandLine(
        cmakeExecutable,
        "--build", "build/native"
    )
}

val nativeLibPackage = tasks.register<Copy>("nativeLibPackage") {
    group = "native"
    description = "Packages the local native bridge for the current platform."

    dependsOn(nativeLibCompile)
    from(layout.buildDirectory.file("native/$nativeLibraryFileName"))
    into(layout.buildDirectory.dir("native-package/$nativePlatform"))
}

tasks.named("build") {
    if (!providers.gradleProperty("nativeLibDir").isPresent) {
        dependsOn(nativeLibPackage)
    }
    dependsOn("apiJar", "apiSourcesJar")
}

spotless {
    java {
        target("**/src/*/java/li/cil/**/*.java")
        targetExclude("**/src/*/java/li/cil/oc2/jcodec/**/*.java")

        licenseHeader("/* SPDX-License-Identifier: MIT */\n\n")

        endWithNewline()
        trimTrailingWhitespace()
        removeUnusedImports()
        indentWithSpaces()
        importOrder("", "javax|java", "\\#")
    }
}

serializeArchitecturyTransforms()
registerGameTestTask()
registerLintTask()
registerApiJarTask(minecraftVersion)
configureMavenPublishing(minecraftVersion, "https://github.com/fnuecke/oc2")
