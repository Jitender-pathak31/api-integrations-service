package com.example.integration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegratedPostRepository extends JpaRepository<IntegratedPost, Long> {

    Optional<IntegratedPost> findByExternalId(Long externalId);

    List<IntegratedPost> findByApprovedTrue();
}

