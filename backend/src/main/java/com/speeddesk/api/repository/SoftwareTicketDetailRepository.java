package com.speeddesk.api.repository;

import com.speeddesk.api.entity.SoftwareTicketDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SoftwareTicketDetailRepository
        extends JpaRepository<SoftwareTicketDetail, UUID> {

    Optional<SoftwareTicketDetail> findByTicket_Id(UUID ticketId);
}
