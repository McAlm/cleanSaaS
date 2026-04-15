package io.cleansaas.model;

public record ProcessInstance(
        long processInstanceKey,
        long processDefinitionKey,
        String processDefinitionId,
        String state
) {}
