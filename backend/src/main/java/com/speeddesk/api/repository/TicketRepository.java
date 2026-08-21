package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOrderByDataCriacaoDesc();

    List<Ticket> findAllByCliente_IdOrderByDataCriacaoDesc(UUID clientId);

    boolean existsByCliente_Id(UUID clientId);

    boolean existsByTecnico_Id(UUID technicianId);
}
