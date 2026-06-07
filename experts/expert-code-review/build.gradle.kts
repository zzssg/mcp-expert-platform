// code_review_expert — a thin profile over the shared engine (tenet T7).
// Per-expert code is: a typed task, a pre-pass that scopes evidence to the diff,
// the prompt assets, and the descriptor. No model/transport code lives here.
plugins { `java-library` }

dependencies {
    api(project(":mcp-contract"))
    api(project(":expert-engine"))
    // Used directly by the deterministic pre-pass (diff parse + citation index).
    implementation(project(":code-analysis-core"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(libs.bundles.test.unit)
}
