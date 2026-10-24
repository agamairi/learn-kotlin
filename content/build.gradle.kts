plugins {
    alias(libs.plugins.kotlin.jvm)
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
