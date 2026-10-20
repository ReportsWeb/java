plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories { mavenCentral() }

javafx { version = "21.0.6"; modules("javafx.controls", "javafx.swing") }

dependencies {
    implementation("ac.pao:reports-java-engine:1.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
}

sourceSets.main { resources.srcDir("resources") }
application { mainClass.set("com.pao.reports.samples.SampleLauncher") }

tasks.withType<JavaCompile>().configureEach { options.release.set(21); options.encoding = "UTF-8" }
tasks.named<JavaExec>("run") {
    workingDir = projectDir
    systemProperty("reports.sample.db", file("../common/database/sample.db").absolutePath)
}

tasks.register<JavaExec>("catalogCheck") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.pao.reports.samples.CatalogCheck")
    workingDir = projectDir
    systemProperty("reports.sample.db", file("../common/database/sample.db").absolutePath)
}
