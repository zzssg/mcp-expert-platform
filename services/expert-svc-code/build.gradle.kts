// expert-svc-code — the deployable Spring Boot MCP server bundling the code-group
// experts (Part 2.2). It terminates MCP over HTTP/SSE and routes tools/call to the
// shared engine. The model-egress wiring + engine bean come from expert-service-support
// (one governed egress); this module only declares its experts and MCP tools.
// NOTE: this build environment has the Spring AI *WebMVC* MCP server starter cached.
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.depmgmt)
}

dependencies {
    implementation(project(":services:expert-service-support"))
    implementation(project(":experts:expert-code-review"))

    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.mcp.server.webmvc)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
