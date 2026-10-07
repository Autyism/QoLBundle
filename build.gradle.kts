plugins {
    // Applies fabric-loom-remap up to 1.21.11 and fabric-loom on 26.1+ (unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = project.property(name).toString()

val mc = sc.current.version
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base { archivesName.set(prop("mod.id")) }

repositories {
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

// The X-ray add-on is a second, separate mod built from src/xray: its own modid, its own jar
// (build/libs/qolbundle-xray-addon-<version>.jar). It is never part of the main jar.
val main: SourceSet = sourceSets["main"]
val xray: SourceSet = sourceSets.create("xray") {
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    // Mod Menu: optional at runtime for players. Compiled against for the "configure" button,
    // and loaded in the dev client so that button can be tested.
    modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain { languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(requiredJava.majorVersion.toInt())
    // Source files contain Chinese text (tests, comments); never depend on the system code page.
    options.encoding = "UTF-8"
    // Show every error when a version does not compile, not just the first 100
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000"))
}

val templateProps = mapOf(
    "version" to prop("mod.version"),
    "minecraft_version" to prop("mod.mc_compat"),
    "loader_compat" to prop("mod.loader_compat"),
    "java_version" to requiredJava.majorVersion,
    "mixin_java" to "JAVA_${requiredJava.majorVersion}",
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(templateProps)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(templateProps) }
}

tasks.named<ProcessResources>("processXrayResources") {
    inputs.properties(templateProps)
    filesMatching("fabric.mod.json") { expand(templateProps) }
}

tasks.named<Jar>("jar") {
    val baseName = prop("mod.id")
    from(rootProject.file("LICENSE")) { rename { "${it}_$baseName" } }
}

loom {
    // Tells the dev client which folders belong to which mod.
    mods {
        register("qolbundle") { sourceSet(main) }
        register("qolbundle_xray") { sourceSet(xray) }
    }
    runs {
        named("client") {
            // One dev game folder per Minecraft version (worlds saved by a newer version cannot be opened by an older one).
            runDirectory.set(rootProject.layout.projectDirectory.dir("run/$mc"))
            // The dev client includes the X-ray add-on; `-PnoAddon` runs the main mod alone.
            sourceSet.set(if (project.hasProperty("noAddon")) main.name else xray.name)
            // `-Pselftest` switches on the automated self-test; `-Pselftest=name1,name2` runs only those scenarios.
            if (project.hasProperty("selftest")) {
                jvmArguments.add("-Dqol.selftest=true")
                val only = project.property("selftest").toString()
                if (only.isNotEmpty()) {
                    jvmArguments.add("-Dqol.selftest.only=$only")
                }
            }
        }
    }
}

// The add-on's jar. Up to 1.21.11 it is first packed with the readable (dev) names and then translated
// to the names the real game uses, the same two steps Loom does for the main jar; 26.1+ needs no translation.
val xrayBaseName = "${prop("mod.id")}-xray-addon"
val xrayJar = tasks.register<Jar>("xrayJar") {
    from(xray.output)
    from(rootProject.file("LICENSE")) { rename { "${it}_qolbundle_xray" } }
    archiveBaseName.set(xrayBaseName)
    if (!loomx.isUnobfuscated) {
        archiveClassifier.set("dev")
        destinationDirectory.set(layout.buildDirectory.dir("devlibs"))
    }
}
val xrayModJar: TaskProvider<out org.gradle.jvm.tasks.Jar> = if (loomx.isUnobfuscated) xrayJar else
    tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapXrayJar") {
        inputFile.set(xrayJar.flatMap { it.archiveFile })
        archiveBaseName.set(xrayBaseName)
        addNestedDependencies.set(false)
        classpath.from(xray.compileClasspath)
    }

tasks.named("assemble") {
    dependsOn(xrayModJar)
}

// Collects the release jars of this version in build/libs/<mod version>/ at the root
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile }, xrayModJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}
