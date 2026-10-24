plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("dev.agam.learn.content.compiler.ContentCompilerKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.snakeyaml.engine)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("compileContent") {
    group = "build"
    description = "Compiles YAML course files into JSON bundles in app-android assets"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.agam.learn.content.compiler.ContentCompilerKt")
    args = listOf(
        rootProject.file("content").absolutePath,
        rootProject.file("app-android/src/main/assets/courses").absolutePath
    )
}

val compilerServerUrl = providers.gradleProperty("compilerServerUrl").orElse("http://127.0.0.1:8080")

tasks.register<JavaExec>("verifyContent") {
    group = "verification"
    description = "Verifies YAML course content against the running kotlin-compiler-server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.agam.learn.content.compiler.ContentVerifierKt")
    args(
        rootProject.file("content").absolutePath,
        compilerServerUrl.get(),
        rootProject.file("docs/content-verification.md").absolutePath
    )
}
