// Shared Spring glue for the expert services (Part 2.2). It owns the single, governed
// model-egress wiring (Vertex REST proxy / ChatModel selection + resilience) and the
// engine bean, exposed as a Spring Boot auto-configuration so each deployable service
// only declares its own experts + MCP tools. No MCP-server transport here — that stays
// per service. Keeps the security-sensitive egress logic in exactly one place.
plugins { `java-library` }

dependencies {
    api(project(":expert-engine"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.ai.model)
    implementation("org.springframework:spring-web") // @RestController for the usage endpoint

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(libs.bundles.test.unit)
}
