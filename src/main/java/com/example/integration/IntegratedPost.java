package com.example.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * Our internal domain entity that stores transformed, enriched, and user-edited data
 * originally fetched from the 3rd-party API.
 */
@Entity
public class IntegratedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long externalId;

    private Long externalAuthorId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(length = 250)
    private String summary;

    private int wordCount;

    @Column(length = 500)
    private String curatorNotes;

    private boolean approved;

    private Instant lastSyncedAt;

    protected IntegratedPost() {
        // Required by JPA
    }

    /**
     * Factory method: transforms a raw 3rd-party DTO into our clean internal domain entity.
     */
    public static IntegratedPost fromExternal(ExternalPostDto dto) {
        IntegratedPost post = new IntegratedPost();
        post.updateFromExternal(dto);
        post.curatorNotes = "Imported from external API";
        post.approved = false;
        return post;
    }

    /**
     * Refreshes fields from the upstream 3rd-party DTO and recalculates derived fields.
     */
    public void updateFromExternal(ExternalPostDto dto) {
        this.externalId = dto.id();
        this.externalAuthorId = dto.userId();
        this.title = normalizeTitle(dto.title());
        this.body = dto.body() == null ? "" : dto.body().replace("\n", " ").trim();
        this.recalculateMetrics();
        this.lastSyncedAt = Instant.now();
    }

    /**
     * Applies local edits made by our API users and recalculates derived metrics (summary & wordCount).
     */
    public void applyEdit(PostEditRequest edit) {
        if (edit.title() != null && !edit.title().isBlank()) {
            this.title = normalizeTitle(edit.title());
        }
        if (edit.body() != null && !edit.body().isBlank()) {
            this.body = edit.body().trim();
        }
        if (edit.curatorNotes() != null) {
            this.curatorNotes = edit.curatorNotes().trim();
        }
        if (edit.approved() != null) {
            this.approved = edit.approved();
        }
        this.recalculateMetrics();
    }

    private void recalculateMetrics() {
        String cleanBody = this.body == null ? "" : this.body.trim();
        this.wordCount = cleanBody.isEmpty() ? 0 : cleanBody.split("\\s+").length;
        this.summary = cleanBody.length() <= 80 ? cleanBody : cleanBody.substring(0, 77) + "...";
    }

    private static String normalizeTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "Untitled";
        }
        String trimmed = rawTitle.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    public Long getId() {
        return id;
    }

    public Long getExternalId() {
        return externalId;
    }

    public Long getExternalAuthorId() {
        return externalAuthorId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getSummary() {
        return summary;
    }

    public int getWordCount() {
        return wordCount;
    }

    public String getCuratorNotes() {
        return curatorNotes;
    }

    public boolean isApproved() {
        return approved;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }
}

