package com.bank.platform.mcp.svc.support;

import com.bank.platform.mcp.svc.support.metrics.UsageMetrics;
import com.bank.platform.mcp.svc.support.web.UsageController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures token-usage metering for any expert service that depends on this
 * module: the shared {@link UsageMetrics} registry (always) and the read-only
 * {@link UsageController} REST endpoint (only in a web app). The {@code MeteredExpert}
 * wrapper that feeds the registry is wired per service around its experts.
 */
@AutoConfiguration
public class ExpertUsageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UsageMetrics usageMetrics() {
        return new UsageMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    public UsageController usageController(UsageMetrics usageMetrics) {
        return new UsageController(usageMetrics);
    }
}
