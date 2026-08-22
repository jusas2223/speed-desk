package com.speeddesk.api.repository;

import com.speeddesk.api.entity.TicketSlaPause;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketSlaPauseRepository extends JpaRepository<TicketSlaPause, UUID> {

    Optional<TicketSlaPause> findFirstByTicket_IdAndResumedAtIsNullOrderByPausedAtDesc(
            UUID ticketId
    );
}
