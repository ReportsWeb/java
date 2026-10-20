plugins { application;id("org.openjfx.javafxplugin") version "0.1.0" }
repositories { mavenCentral() }
javafx { version="21.0.6";modules("javafx.controls","javafx.swing") }
dependencies { implementation("ac.pao:reports-java-engine:1.1.0"); implementation("org.springframework.boot:spring-boot-starter-web:3.4.3"); implementation("org.xerial:sqlite-jdbc:3.53.2.1") }
application { mainClass.set("com.pao.reports.samples.RestClientApp") }
tasks.withType<JavaCompile>().configureEach { options.release.set(21);options.encoding="UTF-8";options.compilerArgs.add("-parameters") }
tasks.named<JavaExec>("run") { workingDir=projectDir }
