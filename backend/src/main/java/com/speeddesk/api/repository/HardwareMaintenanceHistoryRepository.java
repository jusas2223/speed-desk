package com.speeddesk.api.repository;

import com.speeddesk.api.entity.HardwareMaintenanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HardwareMaintenanceHistoryRepository
        extends JpaRepository<HardwareMaintenanceHistory, UUID> {

    List<HardwareMaintenanceHistory>
            findAllByTicket_IdOrderByCreatedAtAscSequenceNumberAsc(
            UUID ticketId
    );

    List<HardwareMaintenanceHistory>
            findAllByTicket_Asset_IdOrderByCreatedAtDescSequenceNumberDesc(
                    UUID assetId
            );
}
