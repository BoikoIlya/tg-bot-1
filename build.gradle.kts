plugins {
	kotlin("jvm") version "2.2.21"
}

group = "com.kamancho"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

	implementation("io.github.dehuckakpyt.telegrambot:telegram-bot-core:1.0.1")
	implementation("io.github.dehuckakpyt.telegrambot:telegram-bot-ktor:1.0.1")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
