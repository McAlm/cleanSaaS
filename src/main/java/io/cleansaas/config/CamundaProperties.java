package io.cleansaas.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "camunda.client")
@Validated
public record CamundaProperties(

        /**
         * Full REST base URL of the Orchestration Cluster.
         * Copied from Camunda Console → Cluster → API → REST API URL.
         * Example: https://bru-2.zeebe.camunda.io/12345678-abcd-…
         */
        @NotBlank String restAddress,

        @Valid Auth auth,

        Cleanup cleanup

) {

    public record Auth(
            @NotBlank String clientId,
            @NotBlank String clientSecret,

            /** Defaults to the Camunda SaaS token endpoint when omitted. */
            String tokenUrl,

            /** Defaults to {@code zeebe.camunda.io} when omitted. */
            String audience
    ) {
        public String effectiveTokenUrl() {
            return tokenUrl != null ? tokenUrl : "https://login.cloud.camunda.io/oauth/token";
        }

        public String effectiveAudience() {
            return audience != null ? audience : "zeebe.camunda.io";
        }
    }

    public record Cleanup(
            /** BPMN process IDs to target. Empty list means ALL process definitions. */
            List<String> processDefinitionIds,

            /** When true, also deletes all running and historic process instances. */
            boolean cascade
    ) {}

    public List<String> effectiveProcessDefinitionIds() {
        return (cleanup != null && cleanup.processDefinitionIds() != null)
                ? cleanup.processDefinitionIds()
                : List.of();
    }

    public boolean cascade() {
        return cleanup != null && cleanup.cascade();
    }
}
