plugins {
    application
}

repositories { mavenCentral() }

dependencies {
    // Reports.Web Pure Java engine (trial) from Maven Central
    implementation("ac.pao:reports-java-engine:1.1.0")
}

application {
    mainClass.set("quickreport.QuickReport")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
    args(projectDir.absolutePath)
}
