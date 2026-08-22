package com.speeddesk.api.repository;

import com.speeddesk.api.entity.HardwarePostRepairChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HardwarePostRepairChecklistRepository
        extends JpaRepository<HardwarePostRepairChecklist, UUID> {

    Optional<HardwarePostRepairChecklist> findByTicket_Id(UUID ticketId);
}
