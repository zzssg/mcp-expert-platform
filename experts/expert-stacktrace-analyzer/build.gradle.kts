// stacktrace_analyzer — a thin profile over the shared engine (tenet T7). The heavy
// lifting (parse trace, resolve the true root frame, decode FACT signals) is the
// deterministic code-analysis-core; Gemini only narrates the root cause over it.
plugins { `java-library` }

dependencies {
    api(project(":mcp-contract"))
    api(project(":expert-engine"))
    implementation(project(":code-analysis-core"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(libs.bundles.test.unit)
}
