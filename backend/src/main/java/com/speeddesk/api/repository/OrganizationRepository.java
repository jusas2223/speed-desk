package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Organization> findByNameIgnoreCase(String name);

    List<Organization> findAllByOrderByNameAsc();
}
