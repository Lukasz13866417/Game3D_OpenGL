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
    testImplementation(rootProject.libs.junit)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
