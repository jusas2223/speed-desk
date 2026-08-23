package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOrderByDataCriacaoDesc();

    List<Ticket> findAllByCliente_IdOrderByDataCriacaoDesc(UUID clientId);

    List<Ticket> findAllByAsset_IdOrderByDataCriacaoDesc(UUID assetId);

    boolean existsByCliente_Id(UUID clientId);

    boolean existsByTecnico_Id(UUID technicianId);

    @Query("""
            select case when count(ticket) > 0 then true else false end
              from Ticket ticket
             where ticket.asset.id = :assetId
               and (
                    ticket.tecnico.id = :technicianId
                    or (
                        ticket.tecnico is null
                        and ticket.status in (
                            com.speeddesk.api.entity.TicketStatus.RECEBIDO,
                            com.speeddesk.api.entity.TicketStatus.EM_TRIAGEM
                        )
                    )
               )
            """)
    boolean existsReadableByAssetIdAndTechnicianId(
            @Param("assetId") UUID assetId,
            @Param("technicianId") UUID technicianId
    );

    @Query("""
            select case when count(ticket) > 0 then true else false end
              from Ticket ticket
             where ticket.cliente.id = :clientId
               and (
                    ticket.status = com.speeddesk.api.entity.TicketStatus.AGUARDANDO_PAGAMENTO
                    or (
                        ticket.valorFinal is not null
                        and ticket.pagamentoRealizado = false
                    )
               )
            """)
    boolean existsPendingPaymentByClientId(@Param("clientId") UUID clientId);
}
