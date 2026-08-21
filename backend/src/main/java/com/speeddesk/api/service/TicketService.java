package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketStatus;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidTicketStatusTransitionException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.InactiveTicketCategoryException;
import com.speeddesk.api.exception.TechnicianNotFoundException;
import com.speeddesk.api.exception.TicketCategoryNotFoundException;
import com.speeddesk.api.exception.TicketCategoryTypeMismatchException;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketCategoryRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional
    public TicketResponseDTO create(TicketRequestDTO request) {
        UUID clientId = authorizationService.clientTarget(request.clienteId());
        User cliente = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        requireRole(cliente, UserRole.CLIENTE);

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

        long slaHours = switch (request.prioridade()) {
            case CRITICA -> 4;
            case ALTA -> 24;
            case NORMAL -> 48;
            case BAIXA -> 72;
        };

        Ticket ticket = Ticket.builder()
                .titulo(request.titulo().trim())
                .descricao(request.descricao().trim())
                .prioridade(request.prioridade())
                .status(TicketStatus.RECEBIDO)
                .ticketType(ticketType)
                .category(category)
                .cliente(cliente)
                .asset(asset)
                .dataVencimento(OffsetDateTime.now(clock).plusHours(slaHours))
                .build();

        return TicketResponseDTO.from(ticketRepository.save(ticket));
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
                .map(TicketResponseDTO::from)
                .toList();
    }

    public TicketResponseDTO findById(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireCanRead(ticket);
        return TicketResponseDTO.from(ticket);
    }

    @Transactional
    public TicketResponseDTO assumirTicket(UUID ticketId, UUID tecnicoId) {
        authorizationService.requireCanAssignTo(tecnicoId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (ticket.getStatus() != TicketStatus.RECEBIDO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.RECEBIDO,
                    TicketStatus.EM_ATENDIMENTO
            );
        }

        User tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new TechnicianNotFoundException(tecnicoId));
        requireRole(tecnico, UserRole.TECNICO);

        ticket.setTecnico(tecnico);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);

        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponseDTO resolverTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireCanResolve(ticket);

        if (ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new InvalidTicketStatusTransitionException(
                    ticket.getStatus(),
                    TicketStatus.EM_ATENDIMENTO,
                    TicketStatus.RESOLVIDO
            );
        }

        ticket.setStatus(TicketStatus.RESOLVIDO);
        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }

    private void requireRole(User user, UserRole expectedRole) {
        if (user.getRole() != expectedRole) {
            throw new InvalidUserRoleException(user.getId(), expectedRole);
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
