plugins { application }
repositories { mavenCentral() }
dependencies { implementation("ac.pao:reports-java-engine:1.1.0"); implementation("org.springframework.boot:spring-boot-starter-web:3.4.3"); implementation("org.xerial:sqlite-jdbc:3.53.2.1") }
sourceSets.main { resources.srcDir("resources") }
application { mainClass.set("com.pao.reports.samples.SampleWebApplication") }
tasks.withType<JavaCompile>().configureEach { options.release.set(21); options.encoding="UTF-8"; options.compilerArgs.add("-parameters") }
tasks.withType<JavaExec>().configureEach { workingDir=projectDir; systemProperty("reports.sample.db",file("../common/database/sample.db").absolutePath) }
tasks.register<JavaExec>("apiCheck") { classpath=sourceSets.main.get().runtimeClasspath; mainClass.set("com.pao.reports.samples.WebApiCheck") }
// gradlew run で起こすサーバーのヒープは 1GiB まで（2026-10-14 コーディネーターの決定）。上限近く 64MiB の印刷データを 4 本同時に送ると、
// 既定のヒープでは作業メモリーが 5302MiB まで伸び、-Xmx1g では 1331MiB で全部成功した。Docker（installDist の起動スクリプト）は変えない。
tasks.named<JavaExec>("run") { jvmArgs("-Xmx1g") }
