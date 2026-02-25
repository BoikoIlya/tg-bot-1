import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	kotlin("jvm") version "2.3.0"
	alias(libs.plugins.com.google.devtools.ksp)
	application
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
	mainClass.set("com.kamancho.bot.app.MyAppKt")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

ksp {
	arg("KOIN_DEFAULT_MODULE", "true")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

	implementation("io.github.dehuckakpyt.telegrambot:telegram-bot-core:1.1.0")
	implementation("io.github.dehuckakpyt.telegrambot:telegram-bot-ktor:1.1.0")
	implementation("io.ktor:ktor-server-netty:3.4.0")

	implementation(libs.ktor.server.core.jvm)
	implementation(libs.ktor.server.netty.jvm)
	compileOnly(libs.koin.annotations)
	ksp(libs.koin.ksp.compiler)

	implementation("org.bytedeco:ffmpeg:6.0-1.5.9")
	implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")
////
//	implementation("de.jarnbjo:j-ogg-all:1.0.0")

	implementation("io.ktor:ktor-client-core:3.4.0")
	implementation("io.ktor:ktor-client-cio:3.4.0")
	implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
	implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

	// Mixpanel analytics
	implementation("com.mixpanel:mixpanel-java:1.7.0")

	// Ktor server logging
	implementation("io.ktor:ktor-server-call-logging-jvm:3.4.0")

	// Database - Exposed ORM with SQLite
	implementation("org.jetbrains.exposed:exposed-core:1.0.0")
	implementation("org.jetbrains.exposed:exposed-dao:1.0.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0")
//	implementation("org.xerial:sqlite-jdbc:3.45.1.0")
	implementation("org.postgresql:postgresql:42.7.10")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

sourceSets.main {
	java.srcDirs("build/generated/ksp/main/kotlin")
}

tasks.withType<ShadowJar> {
	archiveBaseName.set("bot")
	archiveClassifier.set("")
	archiveVersion.set("")

	manifest {
		attributes(
			"Main-Class" to "com.kamancho.bot.app.MyAppKt"
		)
	}

	// Важно для корректной работы конфигурации
	mergeServiceFiles()
	exclude("META-INF/*.SF")
	exclude("META-INF/*.DSA")
	exclude("META-INF/*.RSA")
}

tasks.jar {
	enabled = false // Отключаем создание стандартного пустого JAR
}

tasks.distZip {
	dependsOn(tasks.shadowJar)
	enabled = false // Отключаем, если не нужны zip-дистрибутивы
}

tasks.distTar {
	dependsOn(tasks.shadowJar)
	enabled = false // Отключаем, если не нужны tar-дистрибутивы
}

tasks.startScripts {
	dependsOn(tasks.shadowJar)
	enabled = false // Отключаем создание стартовых скриптов
}