// The shared Gemini framework (Part 5) — the most reused, cost- and
// security-critical module. Every expert is a thin profile over it.
// RAG is out of scope: there is no RetrievalService dependency here.
plugins { `java-library` }

dependencies {
    api(project(":mcp-contract"))
    api(project(":code-analysis-core"))
    implementation(libs.bundles.jackson)
    implementation(libs.json.schema.validator)
    implementation(libs.resilience4j.all)
    implementation(libs.slf4j.api)
    implementation(libs.caffeine)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.wiremock)
}
