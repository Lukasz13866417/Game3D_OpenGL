// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.register("publishTerrainContent") {
    group = "terrain content"
    description = "Publishes validated terrain source content for Android and the simulator."
    dependsOn(":terrain-io:publishTerrainContent")
}

tasks.register("validateTerrainContent") {
    group = "verification"
    description = "Validates the last good published terrain runtime artifact."
    dependsOn(":terrain-io:validateTerrainContent")
}
