// Root build file – shared configuration only.
// Individual module concerns live in their own build.gradle.kts files.

plugins {
    // Kotlin JVM plugin applied to subprojects that need it via subprojects block below.
    // No application plugin at the root level.
}

val kotlinVersion by extra("2.0.21")
val springBootVersion by extra("3.3.5")
val composeVersion by extra("1.7.3")

subprojects {
    group = "com.stackpilot"
    version = "0.1.0-SNAPSHOT"
}
