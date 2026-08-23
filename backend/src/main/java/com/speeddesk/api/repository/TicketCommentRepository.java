package com.speeddesk.api.repository;

import com.speeddesk.api.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findAllByTicket_IdOrderByCreatedAtAscSequenceNumberAsc(
            UUID ticketId
    );

    List<TicketComment> findAllByTicket_IdAndInternalFalseOrderByCreatedAtAscSequenceNumberAsc(
            UUID ticketId
    );
}
