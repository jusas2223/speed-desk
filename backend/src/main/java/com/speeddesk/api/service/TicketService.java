package com.speeddesk.api.service;

import com.speeddesk.api.dto.PaymentPendingResponseDTO;
import com.speeddesk.api.dto.TicketFinalizeRequestDTO;
import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.NotificationType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketSlaPause;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidSlaOperationException;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.InactiveTicketCategoryException;
import com.speeddesk.api.exception.InactiveUserException;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketCategoryNotFoundException;
import com.speeddesk.api.exception.TicketCategoryTypeMismatchException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.TicketSlaPauseRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS =
            allowedTransitions();

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketSlaPauseRepository ticketSlaPauseRepository;
    private final SlaPolicyService slaPolicyService;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final RealtimeService realtimeService;
    private final Clock clock;

    @Transactional
    public TicketResponseDTO create(TicketRequestDTO request) {
        if (authorizationService.currentUser().role() != UserRole.CLIENTE) {
            throw new ForbiddenOperationException(
                    "Somente clientes podem abrir novos chamados."
            );
        }
        UUID clientId = authorizationService.clientTarget(request.clienteId());
        User cliente = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        requireRole(cliente, UserRole.CLIENTE);
        requireActive(cliente);
        requireNoPendingPayment(clientId);

        Asset asset = request.assetId() == null
                ? null
                : assetRepository.findById(request.assetId())
                        .orElseThrow(() -> new AssetNotFoundException(request.assetId()));
        if (asset != null && !asset.getCliente().getId().equals(clientId)) {
            throw new ForbiddenOperationException(
                    "O ativo informado não pertence ao cliente do chamado."
            );
        }

        TicketType ticketType = request.ticketType() == null
                ? TicketType.GERAL
                : request.ticketType();
        TicketCategory category = resolveCategory(request.categoryId(), ticketType);

        SlaPolicyService.SlaPolicySnapshot sla =
                slaPolicyService.snapshot(request.prioridade());
        OffsetDateTime now = OffsetDateTime.now(clock);

        Ticket ticket = Ticket.builder()
                .titulo(request.titulo().trim())
                .descricao(request.descricao().trim())
                .prioridade(request.prioridade())
                .status(TicketStatus.RECEBIDO)
                .ticketType(ticketType)
                .category(category)
                .cliente(cliente)
                .asset(asset)
                .dataVencimento(now.plusMinutes(sla.durationMinutes()))
                .slaDurationMinutes(sla.durationMinutes())
                .slaWarningMinutes(sla.warningMinutes())
                .build();

        TicketResponseDTO response = saveAndRespond(ticket);
        UUID actorId = authorizationService.currentUser().id();
        notificationService.notifyRole(
                UserRole.TECNICO,
                actorId,
                NotificationType.TICKET_CREATED,
                "Novo chamado recebido",
                ticket.getTitulo(),
                "TICKET",
                ticket.getId()
        );
        notificationService.notifyUsers(
                Set.of(cliente.getId()),
                actorId,
                NotificationType.TICKET_CREATED,
                "Chamado criado",
                ticket.getTitulo(),
                "TICKET",
                ticket.getId()
        );
        return response;
    }

    public PaymentPendingResponseDTO paymentPending() {
        if (authorizationService.currentUser().role() != UserRole.CLIENTE) {
            throw new ForbiddenOperationException(
                    "A consulta de pendências é exclusiva do cliente."
            );
        }
        return new PaymentPendingResponseDTO(ticketRepository
                .existsPendingPaymentByClientId(
                        authorizationService.currentUser().id()
                ));
    }

    private TicketCategory resolveCategory(UUID categoryId, TicketType ticketType) {
        if (categoryId == null) {
            return null;
        }

        TicketCategory category = ticketCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new TicketCategoryNotFoundException(categoryId));
        if (!category.isActive()) {
            throw new InactiveTicketCategoryException(categoryId);
        }
        if (category.getTicketType() != ticketType) {
            throw new TicketCategoryTypeMismatchException(
                    categoryId,
                    category.getTicketType(),
                    ticketType
            );
        }
        return category;
    }

    public List<TicketResponseDTO> listAll(UUID clienteId) {
        return listAll(clienteId, null, null, null, null, null, null, null);
    }

    public List<TicketResponseDTO> listAll(
            UUID clienteId,
            TicketStatus status,
            TicketPriority prioridade,
            TicketType ticketType,
            UUID categoryId,
            UUID tecnicoId,
            String query
    ) {
        return listAll(
                clienteId,
                status,
                prioridade,
                ticketType,
                categoryId,
                tecnicoId,
                null,
                query
        );
    }

    public List<TicketResponseDTO> listAll(
            UUID clienteId,
            TicketStatus status,
            TicketPriority prioridade,
            TicketType ticketType,
            UUID categoryId,
            UUID tecnicoId,
            Boolean semTecnico,
            String query
    ) {
        UUID effectiveClientId = authorizationService.clientScope(clienteId);
        List<Ticket> tickets = effectiveClientId == null
                ? ticketRepository.findAllByOrderByDataCriacaoDesc()
                : ticketRepository.findAllByCliente_IdOrderByDataCriacaoDesc(effectiveClientId);
        String normalizedQuery = normalizeQuery(query);

        return tickets.stream()
                .filter(authorizationService::canRead)
                .filter(ticket -> status == null || ticket.getStatus() == status)
                .filter(ticket -> prioridade == null || ticket.getPrioridade() == prioridade)
                .filter(ticket -> ticketType == null
                        || effectiveTicketType(ticket) == ticketType)
                .filter(ticket -> categoryId == null
                        || (ticket.getCategory() != null
                        && categoryId.equals(ticket.getCategory().getId())))
                .filter(ticket -> tecnicoId == null
                        || (ticket.getTecnico() != null
                        && tecnicoId.equals(ticket.getTecnico().getId())))
                .filter(ticket -> !Boolean.TRUE.equals(semTecnico)
                        || ticket.getTecnico() == null)
                .filter(ticket -> matchesQuery(ticket, normalizedQuery))
                .map(this::response)
                .toList();
    }

    public TicketResponseDTO findById(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireCanRead(ticket);
        return response(ticket);
    }

    @Transactional
    public TicketResponseDTO assumirTicket(UUID ticketId, UUID tecnicoId) {
        authorizationService.requireCanAssignTo(tecnicoId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (ticket.getStatus() != TicketStatus.RECEBIDO
                && ticket.getStatus() != TicketStatus.EM_TRIAGEM) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.RECEBIDO,
                    TicketStatus.EM_ATENDIMENTO
            );
        }

        User tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new TechnicianNotFoundException(tecnicoId));
        requireRole(tecnico, UserRole.TECNICO);
        requireActive(tecnico);

        ticket.setTecnico(tecnico);
        applyTransition(ticket, TicketStatus.EM_ATENDIMENTO, OffsetDateTime.now(clock));

        TicketResponseDTO response = saveAndRespond(ticket);
        notificationService.notifyUsers(
                Set.of(tecnico.getId()),
                authorizationService.currentUser().id(),
                NotificationType.TICKET_ASSIGNED,
                "Chamado atribuído a você",
                ticket.getTitulo(),
                "TICKET",
                ticket.getId()
        );
        notifyTicketParticipants(ticket, "Chamado em atendimento");
        return response;
    }

    @Transactional
    public TicketResponseDTO updateStatus(UUID ticketId, TicketStatus targetStatus) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        applyTransition(ticket, targetStatus, OffsetDateTime.now(clock));
        TicketResponseDTO response = saveAndRespond(ticket);
        notifyTicketParticipants(ticket, "Status do chamado atualizado");
        return response;
    }

    @Transactional
    public TicketResponseDTO finalizeService(
            UUID ticketId,
            TicketFinalizeRequestDTO request
    ) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        if (ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.AGUARDANDO_PAGAMENTO
            );
        }
        if (ticket.isSlaPaused()) {
            throw new InvalidSlaOperationException(
                    "Retome o SLA antes de finalizar o serviço."
            );
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        ticket.setValorFinal(request.valorFinal());
        ticket.setPagamentoRealizado(false);
        ticket.setStatus(TicketStatus.AGUARDANDO_PAGAMENTO);
        ticket.setResolvedAt(now);
        ticket.setClosedAt(null);
        TicketResponseDTO response = saveAndRespond(ticket);
        notifyTicketParticipants(ticket, "Serviço finalizado; pagamento pendente");
        return response;
    }

    @Transactional
    public TicketResponseDTO confirmPayment(UUID ticketId) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        if (ticket.getStatus() != TicketStatus.AGUARDANDO_PAGAMENTO
                || ticket.getValorFinal() == null
                || ticket.isPagamentoRealizado()) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.RESOLVIDO
            );
        }

        ticket.setPagamentoRealizado(true);
        ticket.setStatus(TicketStatus.RESOLVIDO);
        if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(OffsetDateTime.now(clock));
        }
        TicketResponseDTO response = saveAndRespond(ticket);
        notifyTicketParticipants(ticket, "Pagamento confirmado; chamado resolvido");
        return response;
    }

    @Transactional
    public TicketResponseDTO close(UUID ticketId) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanCloseOrReopen(ticket);
        if (ticket.getStatus() != TicketStatus.RESOLVIDO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.FECHADO
            );
        }
        if (!ticket.isPagamentoRealizado()) {
            throw new ForbiddenOperationException(
                    "O chamado só pode ser fechado após a confirmação do pagamento."
            );
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        ticket.setStatus(TicketStatus.FECHADO);
        if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(now);
        }
        ticket.setClosedAt(now);
        TicketResponseDTO response = saveAndRespond(ticket);
        notifyTicketParticipants(ticket, "Chamado fechado");
        return response;
    }

    @Transactional
    public TicketResponseDTO reopen(UUID ticketId) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanCloseOrReopen(ticket);
        if (ticket.getStatus() != TicketStatus.RESOLVIDO
                && ticket.getStatus() != TicketStatus.FECHADO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.EM_ATENDIMENTO
            );
        }

        ensureSlaSnapshot(ticket);
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean assignedToActiveTechnician = ticket.getTecnico() != null
                && ticket.getTecnico().isActive();
        if (!assignedToActiveTechnician) {
            ticket.setTecnico(null);
        }
        ticket.setStatus(assignedToActiveTechnician
                ? TicketStatus.EM_ATENDIMENTO
                : TicketStatus.RECEBIDO);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        ticket.setValorFinal(null);
        ticket.setPagamentoRealizado(false);
        ticket.setSlaPaused(false);
        ticket.setSlaPausedAt(null);
        ticket.setDataVencimento(now.plusMinutes(ticket.getSlaDurationMinutes()));
        TicketResponseDTO response = saveAndRespond(ticket);
        notifyTicketParticipants(ticket, "Chamado reaberto");
        return response;
    }

    @Transactional
    public TicketResponseDTO pauseSla(UUID ticketId, String reason) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        if (ticket.getStatus() == TicketStatus.RESOLVIDO
                || ticket.getStatus() == TicketStatus.FECHADO
                || ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO) {
            throw new InvalidSlaOperationException(
                    "O SLA de um chamado concluido nao pode ser pausado."
            );
        }
        if (ticket.isSlaPaused()) {
            throw new InvalidSlaOperationException("O SLA do chamado ja esta pausado.");
        }

        ensureSlaSnapshot(ticket);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.getDataVencimento() == null) {
            ticket.setDataVencimento(now.plusMinutes(ticket.getSlaDurationMinutes()));
        }
        ticket.setSlaPaused(true);
        ticket.setSlaPausedAt(now);

        User actor = userRepository.getReferenceById(
                authorizationService.currentUser().id()
        );
        ticketSlaPauseRepository.save(TicketSlaPause.builder()
                .ticket(ticket)
                .pausedBy(actor)
                .reason(reason.trim())
                .pausedAt(now)
                .build());
        return saveAndRespond(ticket);
    }

    @Transactional
    public TicketResponseDTO resumeSla(UUID ticketId) {
        Ticket ticket = findTicket(ticketId);
        authorizationService.requireCanOperate(ticket);
        if (!ticket.isSlaPaused() || ticket.getSlaPausedAt() == null) {
            throw new InvalidSlaOperationException("O SLA do chamado nao esta pausado.");
        }

        TicketSlaPause pause = ticketSlaPauseRepository
                .findFirstByTicket_IdAndResumedAtIsNullOrderByPausedAtDesc(ticketId)
                .orElseThrow(() -> new InvalidSlaOperationException(
                        "O registro da pausa ativa do SLA nao foi encontrado."
                ));
        OffsetDateTime now = OffsetDateTime.now(clock);
        Duration pausedDuration = Duration.between(ticket.getSlaPausedAt(), now);
        if (pausedDuration.isNegative()) {
            throw new InvalidSlaOperationException("O periodo de pausa do SLA e invalido.");
        }

        ensureSlaSnapshot(ticket);
        OffsetDateTime deadline = ticket.getDataVencimento() == null
                ? now.plusMinutes(ticket.getSlaDurationMinutes())
                : ticket.getDataVencimento().plus(pausedDuration);
        ticket.setDataVencimento(deadline);
        ticket.setSlaPaused(false);
        ticket.setSlaPausedAt(null);

        pause.setResumedAt(now);
        pause.setResumedBy(userRepository.getReferenceById(
                authorizationService.currentUser().id()
        ));
        ticketSlaPauseRepository.save(pause);
        return saveAndRespond(ticket);
    }

    private Ticket findTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private void applyTransition(
            Ticket ticket,
            TicketStatus targetStatus,
            OffsetDateTime now
    ) {
        if (ticket.isSlaPaused()) {
            throw new InvalidSlaOperationException(
                    "Retome o SLA antes de alterar o status do chamado."
            );
        }
        if (targetStatus == TicketStatus.FECHADO
                || targetStatus == TicketStatus.AGUARDANDO_PAGAMENTO
                || targetStatus == TicketStatus.RESOLVIDO
                || ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO
                || ticket.getStatus() == TicketStatus.RESOLVIDO
                || ticket.getStatus() == TicketStatus.FECHADO
                || !ALLOWED_TRANSITIONS
                        .getOrDefault(ticket.getStatus(), Set.of())
                        .contains(targetStatus)) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    targetStatus
            );
        }
        if (targetStatus == TicketStatus.EM_ATENDIMENTO
                && ticket.getTecnico() == null) {
            throw new InvalidSlaOperationException(
                    "A entrada em atendimento exige um tecnico atribuido."
            );
        }

        ticket.setStatus(targetStatus);
    }

    private void ensureSlaSnapshot(Ticket ticket) {
        if (ticket.getSlaDurationMinutes() != null
                && ticket.getSlaWarningMinutes() != null) {
            return;
        }

        TicketPriority priority = ticket.getPrioridade() == null
                ? TicketPriority.NORMAL
                : ticket.getPrioridade();
        SlaPolicyService.SlaPolicySnapshot defaults =
                slaPolicyService.snapshot(priority);
        if (ticket.getSlaDurationMinutes() == null) {
            ticket.setSlaDurationMinutes(defaults.durationMinutes());
        }
        if (ticket.getSlaWarningMinutes() == null) {
            ticket.setSlaWarningMinutes(defaults.warningMinutes());
        }
    }

    private TicketResponseDTO response(Ticket ticket) {
        if (!canSeeClientContact(ticket)) {
            return TicketResponseDTO.forMarketplaceQueue(ticket, clock);
        }
        return TicketResponseDTO.from(ticket, clock, ticket.getCliente().getPhone());
    }

    private TicketResponseDTO saveAndRespond(Ticket ticket) {
        Ticket saved = ticketRepository.save(ticket);
        ticketRepository.flush();
        TicketResponseDTO response = response(saved);
        realtimeService.publishAfterCommit(
                saved.getCliente().getId(),
                "ticket-changed",
                response
        );
        if (saved.getTecnico() != null) {
            realtimeService.publishAfterCommit(
                    saved.getTecnico().getId(),
                    "ticket-changed",
                    response
            );
        }
        if (saved.getTecnico() == null) {
            realtimeService.publishToRoleAfterCommit(
                    UserRole.TECNICO,
                    null,
                    "ticket-changed",
                    TicketResponseDTO.forMarketplaceQueue(saved, clock)
            );
        }
        return response;
    }

    private boolean canSeeClientContact(Ticket ticket) {
        var currentUser = authorizationService.currentUser();
        if (currentUser.role() == UserRole.CLIENTE) {
            return ticket.getCliente() != null
                    && currentUser.id().equals(ticket.getCliente().getId());
        }
        return currentUser.role() == UserRole.TECNICO
                && ticket.getTecnico() != null
                && currentUser.id().equals(ticket.getTecnico().getId());
    }

    private void requireNoPendingPayment(UUID clientId) {
        if (ticketRepository.existsPendingPaymentByClientId(clientId)) {
            throw new ForbiddenOperationException(
                    "Você possui pagamentos pendentes. Acerte com o técnico para liberar novos chamados."
            );
        }
    }

    private void notifyTicketParticipants(Ticket ticket, String title) {
        Set<UUID> recipients = new java.util.LinkedHashSet<>();
        recipients.add(ticket.getCliente().getId());
        if (ticket.getTecnico() != null) recipients.add(ticket.getTecnico().getId());
        notificationService.notifyUsers(
                recipients,
                authorizationService.currentUser().id(),
                NotificationType.TICKET_STATUS_CHANGED,
                title,
                ticket.getTitulo(),
                "TICKET",
                ticket.getId()
        );
    }

    private static Map<TicketStatus, Set<TicketStatus>> allowedTransitions() {
        Map<TicketStatus, Set<TicketStatus>> transitions =
                new EnumMap<>(TicketStatus.class);
        transitions.put(
                TicketStatus.RECEBIDO,
                Set.of(TicketStatus.EM_TRIAGEM, TicketStatus.EM_ATENDIMENTO)
        );
        transitions.put(
                TicketStatus.EM_TRIAGEM,
                Set.of(TicketStatus.EM_ATENDIMENTO, TicketStatus.AGUARDANDO_CLIENTE)
        );
        transitions.put(
                TicketStatus.EM_ATENDIMENTO,
                Set.of(
                        TicketStatus.AGUARDANDO_CLIENTE,
                        TicketStatus.AGUARDANDO_PECA
                )
        );
        transitions.put(
                TicketStatus.AGUARDANDO_CLIENTE,
                Set.of(TicketStatus.EM_ATENDIMENTO)
        );
        transitions.put(
                TicketStatus.AGUARDANDO_PECA,
                Set.of(TicketStatus.EM_ATENDIMENTO)
        );
        return Map.copyOf(transitions);
    }

    private void requireRole(User user, UserRole expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new InvalidUserRoleException(user.getId(), expectedRole);
        }
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new InactiveUserException(user.getId());
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return normalizeSearchText(query.trim());
    }

    private boolean matchesQuery(Ticket ticket, String query) {
        if (query == null) {
            return true;
        }

        return contains(ticket.getId(), query)
                || contains(displayCode(ticket), query)
                || contains(ticket.getTitulo(), query)
                || contains(ticket.getDescricao(), query)
                || contains(ticket.getCliente().getName(), query)
                || contains(ticket.getCliente().getEmail(), query)
                || (ticket.getCliente().getOrganization() != null
                && contains(ticket.getCliente().getOrganization().getName(), query))
                || (ticket.getCategory() != null
                && contains(ticket.getCategory().getName(), query))
                || contains(effectiveTicketType(ticket), query)
                || (ticket.getAsset() != null
                && (contains(ticket.getAsset().getId(), query)
                        || contains(ticket.getAsset().getNome(), query)
                        || contains(ticket.getAsset().getTipo(), query)
                        || contains(ticket.getAsset().getNumeroSerie(), query)));
    }

    private TicketType effectiveTicketType(Ticket ticket) {
        return ticket.getTicketType() == null ? TicketType.GERAL : ticket.getTicketType();
    }

    private String displayCode(Ticket ticket) {
        String compactId = ticket.getId().toString().replace("-", "");
        return "SPD-" + compactId.substring(0, Math.min(6, compactId.length()));
    }

    private boolean contains(Object value, String query) {
        return value != null
                && normalizeSearchText(value.toString()).contains(query);
    }

    private String normalizeSearchText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
