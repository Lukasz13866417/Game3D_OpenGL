plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(project(":terrain-authoring"))
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    testLogging { events("failed", "skipped") }
}

val terrainContentRoot = rootProject.layout.projectDirectory.dir("terrain-content")
val terrainSourceCatalog = terrainContentRoot.file("catalog.terrain-catalog.json")
val terrainRuntimeCatalog = terrainContentRoot.file("published/terrain/runtime-catalog.json")

tasks.register<JavaExec>("publishTerrainContent") {
    group = "terrain content"
    description = "Validates source terrain documents and atomically publishes the runtime catalog."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.game3d.terrain.io.cli.TerrainContentCli")
    args(
        "publish",
        terrainSourceCatalog.asFile.absolutePath,
        terrainContentRoot.asFile.absolutePath,
        terrainRuntimeCatalog.asFile.absolutePath,
    )
    inputs.file(terrainSourceCatalog)
    inputs.dir(terrainContentRoot.dir("structures"))
    inputs.dir(terrainContentRoot.dir("levels"))
    outputs.file(terrainRuntimeCatalog)
}

tasks.register<JavaExec>("validateTerrainContent") {
    group = "verification"
    description = "Strictly validates the last published gameplay terrain catalog."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.game3d.terrain.io.cli.TerrainContentCli")
    args("validate", terrainRuntimeCatalog.asFile.absolutePath)
    inputs.file(terrainRuntimeCatalog)
}

// Validation normally checks the last-good artifact without publishing drafts. When a caller
// explicitly requests both tasks in one build, order their shared file access deterministically.
tasks.named("validateTerrainContent") {
    mustRunAfter(tasks.named("publishTerrainContent"))
}
