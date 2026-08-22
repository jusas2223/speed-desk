package com.speeddesk.api.repository;

import com.speeddesk.api.entity.SlaPolicy;
import com.speeddesk.api.entity.TicketPriority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, TicketPriority> {
}
