plugins { java }

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    group = "com.bank.platform.mcp"
    version = "1.0.0-SNAPSHOT"

    repositories { mavenCentral() }

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
