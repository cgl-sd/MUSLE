plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

layout.buildDirectory.set(layout.projectDirectory.dir("tmp/build/root"))

subprojects {
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("tmp/build/${project.name}"))
}
