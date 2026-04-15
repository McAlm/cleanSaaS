package io.cleansaas.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cleansaas.config.CamundaProperties;
import io.cleansaas.model.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProcessInstanceService {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstanceService.class);
    private static final int PAGE_SIZE = 50;

    private final OAuthTokenService tokenService;
    private final RestClient restClient;

    public ProcessInstanceService(CamundaProperties properties, OAuthTokenService tokenService) {
        this.tokenService = tokenService;
        this.restClient = RestClient.builder()
                .baseUrl(properties.restAddress())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Cancels all ACTIVE process instances for the given process definition key.
     *
     * @return the number of instances cancelled
     */
    public int cancelAllActive(long processDefinitionKey) {
        var instances = findAllActive(processDefinitionKey);
        if (instances.isEmpty()) {
            return 0;
        }
        log.info("  Found {} active instance(s) to cancel for processDefinitionKey={}", instances.size(), processDefinitionKey);
        int cancelled = 0;
        for (ProcessInstance instance : instances) {
            log.info("  Cancelling process instance key={}", instance.processInstanceKey());
            cancel(instance.processInstanceKey());
            cancelled++;
        }
        return cancelled;
    }

    private List<ProcessInstance> findAllActive(long processDefinitionKey) {
        var result = new ArrayList<ProcessInstance>();
        List<Object> searchAfter = null;

        do {
            var request = buildSearchRequest(processDefinitionKey, searchAfter);
            var response = executeSearch(request);
            var page = response.items();
            result.addAll(page);

            boolean hasMore = page.size() == PAGE_SIZE
                    && response.page() != null
                    && response.page().lastSortValues() != null;
            searchAfter = hasMore ? response.page().lastSortValues() : null;
        } while (searchAfter != null);

        return result;
    }

    private SearchRequest buildSearchRequest(long processDefinitionKey, List<Object> searchAfter) {
        return new SearchRequest(
                Map.of("processDefinitionKey", Long.toString(processDefinitionKey), "state", "ACTIVE"),
                new SearchRequest.Page(PAGE_SIZE),
                List.of(new SearchRequest.Sort("processInstanceKey", "ASC")),
                searchAfter
        );
    }

    private SearchResponse executeSearch(SearchRequest request) {
        var response = restClient.post()
                .uri("/v2/process-instances/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken())
                .body(request)
                .retrieve()
                .body(SearchResponse.class);
        return Objects.requireNonNull(response, "Process instance search response was null");
    }

    private void cancel(long processInstanceKey) {
        restClient.post()
                .uri("/v2/process-instances/{key}/cancellation", processInstanceKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken())
                .retrieve()
                .toBodilessEntity();
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SearchRequest(
            Map<String, Object> filter,
            Page page,
            List<Sort> sort,
            List<Object> searchAfter
    ) {
        record Page(int limit) {}
        record Sort(String field, String order) {}
    }

    private record SearchResponse(
            List<ProcessInstance> items,
            PageInfo page
    ) {}

    private record PageInfo(
            long totalItems,
            List<Object> firstSortValues,
            List<Object> lastSortValues
    ) {}
}
