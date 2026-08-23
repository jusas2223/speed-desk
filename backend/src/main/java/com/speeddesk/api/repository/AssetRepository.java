package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findAllByCliente_Id(UUID clienteId);

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findAllByCliente_IdOrderByCreatedAtDesc(UUID clienteId);

    @Query("""
            select distinct asset
              from Asset asset
              join Ticket ticket on ticket.asset = asset
             where ticket.tecnico.id = :technicianId
                or (
                    ticket.tecnico is null
                    and ticket.status in (
                        com.speeddesk.api.entity.TicketStatus.RECEBIDO,
                        com.speeddesk.api.entity.TicketStatus.EM_TRIAGEM
                    )
                )
             order by asset.createdAt desc
            """)
    List<Asset> findAllReadableByTechnician(
            @Param("technicianId") UUID technicianId
    );

    boolean existsByNumeroSerieIgnoreCase(String numeroSerie);

    boolean existsByNumeroSerieIgnoreCaseAndIdNot(String numeroSerie, UUID id);

    boolean existsByCliente_Id(UUID clienteId);
}
