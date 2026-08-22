package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketCommentRequestDTO;
import com.speeddesk.api.dto.TicketCommentResponseDTO;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketComment;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.exception.UserNotFoundException;
import com.speeddesk.api.repository.TicketCommentRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCommentService {

    private final TicketCommentRepository ticketCommentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TicketCommentResponseDTO> list(UUID ticketId) {
        Ticket ticket = requireTicket(ticketId);
        authorizationService.requireCanRead(ticket);

        AuthenticatedUser currentUser = authorizationService.currentUser();
        List<TicketComment> comments = currentUser.role() == UserRole.CLIENTE
                ? ticketCommentRepository
                        .findAllByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(ticketId)
                : ticketCommentRepository
                        .findAllByTicket_IdOrderByCreatedAtAscIdAsc(ticketId);

        return comments.stream()
                .map(TicketCommentResponseDTO::from)
                .toList();
    }

    @Transactional
    public TicketCommentResponseDTO create(
            UUID ticketId,
            TicketCommentRequestDTO request
    ) {
        Ticket ticket = requireTicket(ticketId);
        authorizationService.requireCanRead(ticket);

        AuthenticatedUser currentUser = authorizationService.currentUser();
        if (currentUser.role() == UserRole.CLIENTE && request.internal()) {
            throw new ForbiddenOperationException(
                    "Clientes não podem criar notas internas."
            );
        }

        User author = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new UserNotFoundException(currentUser.id()));

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .content(request.content().trim())
                .internal(request.internal())
                .createdAt(OffsetDateTime.now(clock))
                .build();

        return TicketCommentResponseDTO.from(ticketCommentRepository.saveAndFlush(comment));
    }

    private Ticket requireTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }
}
