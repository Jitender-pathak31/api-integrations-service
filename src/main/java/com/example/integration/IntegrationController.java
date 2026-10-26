package com.example.integration;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST Controller that demonstrates the full API Integration lifecycle:
 * 1. CALL (Fetch from 3rd-party API via ExternalPostClient)
 * 2. TRANSFORM & EDIT (Normalize, enrich with wordCount/summary, and allow curator edits)
 * 3. WIEDER BEREITSTELLEN (Re-expose curated data via clean REST endpoints)
 */
@RestController
@RequestMapping("/api/integrations/posts")
public class IntegrationController {

    private final ExternalPostClient externalClient;
    private final IntegratedPostRepository repository;

    public IntegrationController(ExternalPostClient externalClient, IntegratedPostRepository repository) {
        this.externalClient = externalClient;
        this.repository = repository;
    }

    /**
     * 1A. LIVE PREVIEW (Stateless Call + Transform + Re-expose without saving to DB).
     * Calls the 3rd-party API for a single post, transforms it in memory, and returns it.
     */
    @GetMapping("/live-preview/{externalId}")
    public IntegratedPost previewExternalPost(@PathVariable Long externalId) {
        ExternalPostDto rawDto = externalClient.fetchPostById(externalId);
        return IntegratedPost.fromExternal(rawDto);
    }

    /**
     * 1B. SYNC SINGLE RECORD (Call 3rd-party API -> Transform -> Save/Upsert locally).
     * If the externalId was already imported, refreshes its upstream text while preserving its local ID.
     */
    @PostMapping("/sync/{externalId}")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegratedPost syncSinglePost(@PathVariable Long externalId) {
        ExternalPostDto rawDto = externalClient.fetchPostById(externalId);
        return upsertFromExternal(rawDto);
    }

    /**
     * 1C. SYNC BATCH (Call 3rd-party API for top N posts -> Transform -> Save/Upsert locally).
     */
    @PostMapping("/sync-batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<IntegratedPost> syncBatch(@RequestParam(defaultValue = "5") int limit) {
        List<ExternalPostDto> rawDtos = externalClient.fetchPostsBatch(limit);
        return rawDtos.stream()
                .map(this::upsertFromExternal)
                .toList();
    }

    /**
     * 2. EDIT / CURATE LOCALLY (Modify title, body, curatorNotes, or approval flag).
     * Automatically recalculates summary and wordCount.
     */
    @PutMapping("/{id}")
    public IntegratedPost editPost(@PathVariable Long id, @RequestBody PostEditRequest editRequest) {
        IntegratedPost post = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Integrated post not found with local id: " + id));
        post.applyEdit(editRequest);
        return repository.save(post);
    }

    /**
     * 3A. WIEDER BEREITSTELLEN (Re-expose all locally stored & edited posts).
     * Downstream consumers can pass ?approvedOnly=true to fetch only curated & approved items.
     */
    @GetMapping
    public List<IntegratedPost> getAllIntegratedPosts(
            @RequestParam(defaultValue = "false") boolean approvedOnly) {
        if (approvedOnly) {
            return repository.findByApprovedTrue();
        }
        return repository.findAll();
    }

    /**
     * 3B. WIEDER BEREITSTELLEN (Re-expose a single curated post by local ID).
     */
    @GetMapping("/{id}")
    public IntegratedPost getIntegratedPostById(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Integrated post not found with local id: " + id));
    }

    /**
     * Delete a locally stored post.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIntegratedPost(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Integrated post not found with local id: " + id);
        }
        repository.deleteById(id);
    }

    private IntegratedPost upsertFromExternal(ExternalPostDto dto) {
        return repository.findByExternalId(dto.id())
                .map(existing -> {
                    existing.updateFromExternal(dto);
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(IntegratedPost.fromExternal(dto)));
    }
}

