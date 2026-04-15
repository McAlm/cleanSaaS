package io.cleansaas.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.cleansaas.config.CamundaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Objects;

@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    private final CamundaProperties properties;
    private final RestClient tokenClient;

    private volatile String accessToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public OAuthTokenService(CamundaProperties properties) {
        this.properties = properties;
        this.tokenClient = RestClient.create(properties.auth().effectiveTokenUrl());
    }

    /** Returns a valid bearer token, fetching a new one when the current token is about to expire. */
    public synchronized String getAccessToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(30))) {
            refresh();
        }
        return accessToken;
    }

    private void refresh() {
        log.debug("Fetching new OAuth access token from {}", properties.auth().effectiveTokenUrl());

        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.auth().clientId());
        body.add("client_secret", properties.auth().clientSecret());
        body.add("audience", properties.auth().effectiveAudience());

        var response = tokenClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);

        Objects.requireNonNull(response, "Received null token response");
        accessToken = response.accessToken();
        tokenExpiry = Instant.now().plusSeconds(response.expiresIn());
        log.debug("Access token obtained, valid for {}s", response.expiresIn());
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
