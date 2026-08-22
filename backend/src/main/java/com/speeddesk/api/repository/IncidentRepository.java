package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Incident;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @EntityGraph(attributePaths = {"createdBy", "tickets"})
    List<Incident> findAllByOrderByStartedAtDesc();

    @Override
    @EntityGraph(attributePaths = {"createdBy", "tickets"})
    Optional<Incident> findById(UUID id);
}
