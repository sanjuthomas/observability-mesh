package com.observabilitymesh.sloauthor.repository;

import com.observabilitymesh.sloauthor.model.OpenSloDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpenSloDocumentRepository extends JpaRepository<OpenSloDocument, String> {

    Optional<OpenSloDocument> findByLogicalKeyAndStaleFalse(String logicalKey);

    List<OpenSloDocument> findByStaleFalseOrderByKindAscNameAsc();

    List<OpenSloDocument> findByLogicalKeyOrderByVersionDesc(String logicalKey);

    boolean existsByLogicalKeyAndStaleFalse(String logicalKey);
}
