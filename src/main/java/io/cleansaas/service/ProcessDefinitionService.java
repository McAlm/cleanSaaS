package io.cleansaas.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cleansaas.config.CamundaProperties;
import io.cleansaas.model.ProcessDefinition;
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
public class ProcessDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ProcessDefinitionService.class);
    private static final int PAGE_SIZE = 50;

    private final CamundaProperties properties;
    private final OAuthTokenService tokenService;
    private final RestClient restClient;

    public ProcessDefinitionService(CamundaProperties properties, OAuthTokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restClient = RestClient.builder()
                .baseUrl(properties.restAddress())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Returns all process definitions matching the given BPMN process IDs.
     * Pass an empty list to fetch every process definition in the cluster.
     */
    public List<ProcessDefinition> findAll(List<String> processDefinitionIds) {
        if (processDefinitionIds.isEmpty()) {
            return fetchAllPages(null);
        }
        return processDefinitionIds.stream()
                .flatMap(id -> fetchAllPages(id).stream())
                .toList();
    }

    private List<ProcessDefinition> fetchAllPages(String processDefinitionId) {
        var result = new ArrayList<ProcessDefinition>();
        List<Object> searchAfter = null;

        do {
            var request = buildSearchRequest(processDefinitionId, searchAfter);
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

    private SearchRequest buildSearchRequest(String processDefinitionId, List<Object> searchAfter) {
        Map<String, Object> filter = processDefinitionId != null
                ? Map.of("processDefinitionId", processDefinitionId)
                : null;
        return new SearchRequest(
                filter,
                new SearchRequest.Page(PAGE_SIZE),
                List.of(new SearchRequest.Sort("processDefinitionKey", "ASC")),
                searchAfter
        );
    }

    private SearchResponse executeSearch(SearchRequest request) {
        var response = restClient.post()
                .uri("/v2/process-definitions/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken())
                .body(request)
                .retrieve()
                .body(SearchResponse.class);
        return Objects.requireNonNull(response, "Search response was null");
    }

    /**
     * Deletes a single process definition by its key.
     *
     * @param processDefinitionKey the unique key of the process definition version to delete
     * @param cascade              when true, also deletes all running and historic process instances
     *                             and passes {@code deleteHistory=true} in the request body
     */
    public void delete(long processDefinitionKey, boolean cascade) {
        var request = new DeleteRequest(cascade ? Boolean.TRUE : null);
        var response = restClient.post()
                .uri("/v2/resources/{key}/deletion", processDefinitionKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken())
                .body(request)
                .retrieve()
                .body(DeleteResourceResponse.class);
        Objects.requireNonNull(response, "Delete response was null");
        log.info("Deleted resource key={}", response.resourceKey());
        if (response.batchOperation() != null) {
            log.debug("  History deletion batch: key={}, type={}",
                    response.batchOperation().batchOperationKey(),
                    response.batchOperation().batchOperationType());
        }
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
            List<ProcessDefinition> items,
            PageInfo page
    ) {}

    private record PageInfo(
            long totalItems,
            List<Object> firstSortValues,
            List<Object> lastSortValues
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DeleteRequest(Boolean deleteHistory) {}

    private record DeleteResourceResponse(
            String resourceKey,
            BatchOperation batchOperation
    ) {}

    private record BatchOperation(
            String batchOperationKey,
            String batchOperationType
    ) {}
}
