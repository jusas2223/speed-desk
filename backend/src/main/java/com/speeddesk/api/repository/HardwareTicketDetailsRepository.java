package com.speeddesk.api.repository;

import com.speeddesk.api.entity.HardwareTicketDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HardwareTicketDetailsRepository
        extends JpaRepository<HardwareTicketDetails, UUID> {

    Optional<HardwareTicketDetails> findByTicket_Id(UUID ticketId);
}
