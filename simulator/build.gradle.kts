plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass.set("com.example.game3d.simulator.SimulatorMain")
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":terrain-authoring"))
    implementation(project(":terrain-io"))
    testImplementation(rootProject.libs.junit)
}

sourceSets {
    named("main") {
        // Package the exact last-good runtime artifact that Android receives as an asset.
        // An explicit -Dgame3d.terrainCatalog path can still override it for validation.
        resources.srcDir(rootProject.layout.projectDirectory.dir("terrain-content/published"))
    }
}

tasks.named("processResources") {
    dependsOn(rootProject.tasks.named("validateTerrainContent"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
