package com.bank.platform.mcp.svc.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The {@code expert-svc-incident} MCP server (Part 2.2) — the incident-time deployable.
 * Boots the Spring AI MCP server, which exposes the incident-group experts registered
 * as a {@code ToolCallbackProvider} (see {@code IncidentExpertConfig}). The model
 * egress and engine bean are supplied by {@code expert-service-support}.
 */
@SpringBootApplication
public class ExpertSvcIncidentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpertSvcIncidentApplication.class, args);
    }
}
