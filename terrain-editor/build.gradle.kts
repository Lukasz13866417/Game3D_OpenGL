plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.graphics")
}

application {
    mainClass.set("com.example.game3d.terrain.editor.TerrainEditorApp")
}

dependencies {
    implementation(project(":terrain-io"))
    implementation(project(":terrain-authoring"))
    implementation(project(":game-core"))
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testRuntimeOnly("org.testfx:openjfx-monocle:21.0.2")
}

tasks.test {
    useJUnitPlatform()
    // Exercise real JavaFX controls in developer and CI builds without requiring an X server.
    systemProperty("testfx.robot", "glass")
    systemProperty("testfx.headless", "true")
    systemProperty("prism.order", "sw")
    systemProperty("prism.text", "t2k")
    systemProperty("java.awt.headless", "true")
    systemProperty("javafx.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
