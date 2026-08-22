package com.speeddesk.api.repository;

import com.speeddesk.api.entity.SoftwareTechnicalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SoftwareTechnicalLogRepository
        extends JpaRepository<SoftwareTechnicalLog, UUID> {

    List<SoftwareTechnicalLog> findAllByTicket_IdOrderByOccurredAtDescIdDesc(UUID ticketId);
}
