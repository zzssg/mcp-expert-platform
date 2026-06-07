package com.bank.platform.mcp.svc.code;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The {@code expert-svc-code} MCP server (Part 2.2). Boots the Spring AI MCP server
 * autoconfiguration, which exposes the experts registered as a {@code
 * ToolCallbackProvider} (see {@code ExpertPlatformConfig}) over the MCP transport.
 */
@SpringBootApplication
public class ExpertSvcCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpertSvcCodeApplication.class, args);
    }
}
