plugins { application }
repositories { mavenCentral() }
dependencies { implementation("ac.pao:reports-java-engine:1.0.0"); implementation("org.springframework.boot:spring-boot-starter-web:3.4.3"); implementation("org.xerial:sqlite-jdbc:3.53.2.1") }
sourceSets.main { resources.srcDir("resources") }
application { mainClass.set("com.pao.reports.samples.SampleWebApplication") }
tasks.withType<JavaCompile>().configureEach { options.release.set(21);options.encoding="UTF-8";options.compilerArgs.add("-parameters") }
tasks.withType<JavaExec>().configureEach { workingDir=projectDir;systemProperty("reports.sample.db",file("../../common/database/sample.db").absolutePath) }
tasks.register<JavaExec>("apiCheck") { classpath=sourceSets.main.get().runtimeClasspath;mainClass.set("com.pao.reports.samples.WebApiCheck") }
