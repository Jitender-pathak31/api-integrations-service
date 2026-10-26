package com.example.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

@SpringBootTest
class IntegrationControllerTest {

    @Autowired
    private IntegratedPostRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // Stub ExternalPostClient for fast, deterministic offline unit/integration testing
        ExternalPostClient stubClient = new ExternalPostClient(RestClient.builder(), "http://localhost:9999") {
            @Override
            public ExternalPostDto fetchPostById(Long externalId) {
                return new ExternalPostDto(
                        externalId,
                        42L,
                        "raw lowercase title from third party",
                        "First line of body\nSecond line with six words total"
                );
            }

            @Override
            public List<ExternalPostDto> fetchPostsBatch(int limit) {
                return List.of(fetchPostById(101L), fetchPostById(102L));
            }
        };

        IntegrationController controller = new IntegrationController(stubClient, repository);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldSyncTransformEditAndReExposeThirdPartyDataViaMockMvc() throws Exception {
        // 1. Sync (POST /api/integrations/posts/sync/10 -> 201 Created + Transform + Persist)
        mockMvc.perform(post("/api/integrations/posts/sync/{externalId}", 10L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.externalId").value(10))
                .andExpect(jsonPath("$.title").value("Raw lowercase title from third party"))
                .andExpect(jsonPath("$.body").value("First line of body Second line with six words total"))
                .andExpect(jsonPath("$.wordCount").value(10))
                .andExpect(jsonPath("$.approved").value(false));

        Long localId = repository.findAll().get(0).getId();

        // 2. Edit locally (PUT /api/integrations/posts/{id} -> 200 OK)
        String editJson = """
                {
                  "title": "Curated & Edited Headline",
                  "body": "Short edited body text",
                  "curatorNotes": "Verified and approved for public API",
                  "approved": true
                }
                """;

        mockMvc.perform(put("/api/integrations/posts/{id}", localId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Curated & Edited Headline"))
                .andExpect(jsonPath("$.wordCount").value(4))
                .andExpect(jsonPath("$.curatorNotes").value("Verified and approved for public API"))
                .andExpect(jsonPath("$.approved").value(true));

        // 3. Wieder bereitstellen (GET /api/integrations/posts?approvedOnly=true -> 200 OK)
        mockMvc.perform(get("/api/integrations/posts").param("approvedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Curated & Edited Headline"));
    }
}

