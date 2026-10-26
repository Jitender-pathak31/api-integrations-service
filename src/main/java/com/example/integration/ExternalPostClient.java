package com.example.integration;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dedicated HTTP client wrapper using Spring's modern synchronous RestClient.
 * Isolates all 3rd-party API communication and network error handling in one place.
 */
@Component
public class ExternalPostClient {

    private final RestClient restClient;

    public ExternalPostClient(
            RestClient.Builder builder,
            @Value("${integration.external-api.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ExternalPostDto fetchPostById(Long externalId) {
        try {
            ExternalPostDto dto = restClient.get()
                    .uri("/posts/{id}", externalId)
                    .retrieve()
                    .body(ExternalPostDto.class);

            if (dto == null || dto.id() == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "External post not found: " + externalId);
            }
            return dto;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "External post not found on 3rd-party API: " + externalId);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to reach 3rd-party API: " + ex.getMessage());
        }
    }

    public List<ExternalPostDto> fetchPostsBatch(int limit) {
        try {
            List<ExternalPostDto> allPosts = restClient.get()
                    .uri("/posts")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ExternalPostDto>>() {});

            if (allPosts == null) {
                return List.of();
            }
            int safeLimit = Math.max(1, Math.min(limit, 20));
            return allPosts.stream().limit(safeLimit).toList();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to fetch posts from 3rd-party API: " + ex.getMessage());
        }
    }
}

