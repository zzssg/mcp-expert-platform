// expert-svc-incident — the deployable Spring Boot MCP server bundling the incident-
// group experts (Part 2.2: stacktrace_analyzer now; log_analyzer + sql_expert later).
// These are bursty, large-input, incident-time tools, deployed separately from the
// IDE-latency code tools so they can scale independently. Model egress + engine bean
// come from expert-service-support; this module declares its experts and MCP tools.
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.depmgmt)
}

dependencies {
    implementation(project(":services:expert-service-support"))
    implementation(project(":experts:expert-stacktrace-analyzer"))

    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.mcp.server.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
