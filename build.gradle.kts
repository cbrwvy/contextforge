plugins {
    `java-library`
}

group = "io.github.cbrwvy"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "ContextForge",
            "Implementation-Version" to project.version.toString(),
            "Automatic-Module-Name" to "io.github.cbrwvy.contextforge",
        )
    }
}

tasks.register("verifyRuntimeClasspath") {
    group = "verification"
    description = "Fails if the core library declares a third-party runtime dependency."
    val runtimeFiles = configurations.named("runtimeClasspath").map { it.files }
    doLast {
        val files = runtimeFiles.get()
        if (files.isNotEmpty()) {
            throw GradleException(
                "Core runtime classpath must be empty, found: ${files.joinToString { it.name }}",
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyRuntimeClasspath")
}
