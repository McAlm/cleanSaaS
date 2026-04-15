package io.cleansaas.model;

public record ProcessDefinition(
        long processDefinitionKey,
        String processDefinitionId,
        String name,
        int version,
        String resourceName,
        String tenantId
) {}
