package io.cleansaas.runner;

import io.cleansaas.config.CamundaProperties;
import io.cleansaas.model.ProcessDefinition;
import io.cleansaas.service.ProcessDefinitionService;
import io.cleansaas.service.ProcessInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CleanupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CleanupRunner.class);

    private final ProcessDefinitionService service;
    private final ProcessInstanceService instanceService;
    private final CamundaProperties properties;

    public CleanupRunner(ProcessDefinitionService service, ProcessInstanceService instanceService, CamundaProperties properties) {
        this.service = service;
        this.instanceService = instanceService;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        var ids = properties.effectiveProcessDefinitionIds();
        var cascade = properties.cascade();

        log.info("Starting process definition cleanup");
        log.info("  Target:  {}", ids.isEmpty() ? "<all process definitions>" : ids);
        log.info("  Cascade: {}", cascade);

        var definitions = service.findAll(ids);
        log.info("Found {} process definition(s) to delete", definitions.size());

        if (definitions.isEmpty()) {
            log.info("Nothing to delete.");
            return;
        }

        int deleted = 0;
        int failed = 0;

        for (ProcessDefinition def : definitions) {
            String label = def.name() != null ? def.name() : def.processDefinitionId();
            log.info("Deleting '{}' (key={}, version={})", label, def.processDefinitionKey(), def.version());
            try {
                if (cascade) {
                    int cancelled = instanceService.cancelAllActive(def.processDefinitionKey());
                    if (cancelled > 0) {
                        log.info("  -> Cancelled {} active instance(s).", cancelled);
                    }
                }
                service.delete(def.processDefinitionKey(), cascade);
                log.info("  -> Deleted.");
                deleted++;
            } catch (Exception e) {
                log.error("  -> Failed: {}", e.getMessage());
                failed++;
            }
        }

        log.info("Cleanup complete — deleted: {}, failed: {}", deleted, failed);
    }
}
