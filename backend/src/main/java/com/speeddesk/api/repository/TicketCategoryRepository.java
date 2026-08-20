package com.speeddesk.api.repository;

import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    boolean existsByNameIgnoreCaseAndTicketType(String name, TicketType ticketType);

    Optional<TicketCategory> findByNameIgnoreCaseAndTicketType(
            String name,
            TicketType ticketType
    );

    List<TicketCategory> findAllByActiveTrueOrderByNameAsc();
}
