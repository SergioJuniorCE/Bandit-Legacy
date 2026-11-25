import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.gtnewhorizons.gtnhconvention")
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        // Forgelin (the Kotlin runtime in GTNH) bundles Kotlin 2.1, so stay on the 2.1 language level
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}
