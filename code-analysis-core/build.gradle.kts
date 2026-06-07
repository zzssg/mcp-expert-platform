// Deterministic analysis cores (Part 3.5, Parts 7–8). NO LLM, NO Spring.
// Pure Java so it can be unit-tested in isolation and reused by every expert.
// JavaParser/JGraphT are declared for the graph/AST analyzers that the
// architecture_inspector and dependency_analyzer experts will build on; the
// diff/stacktrace/signal cores below are dependency-free by design.
plugins { `java-library` }

dependencies {
    api(project(":mcp-contract"))
    // implementation(libs.javaparser.symbol.solver) // for AST/symbol analyzers (Part 7)
    // implementation(libs.jgrapht.core)             // for dependency-graph algorithms (Part 7)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(libs.bundles.test.unit)
}
