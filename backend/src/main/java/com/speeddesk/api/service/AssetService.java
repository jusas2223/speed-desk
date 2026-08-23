package com.speeddesk.api.service;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.dto.AssetUpdateRequestDTO;
import com.speeddesk.api.dto.TicketResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.AssetStatus;
import com.speeddesk.api.entity.AssetType;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.entity.WarrantyState;
import com.speeddesk.api.exception.AssetNotFoundException;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.DuplicateAssetSerialException;
import com.speeddesk.api.exception.ForbiddenOperationException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.InactiveUserException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    @Transactional
    public AssetResponseDTO create(AssetRequestDTO request) {
        requireCanCreate();
        UUID clientId = authorizationService.clientTarget(request.clienteId());
        User cliente = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        requireClientRole(cliente);
        requireActive(cliente);
        validateDates(request.purchaseDate(), request.warrantyEndDate());

        String serial = normalizeSerial(request.serial());
        requireUniqueSerial(serial, null);
        AssetType type = parseType(request.tipo());
        AssetStatus status = request.status() == null ? AssetStatus.ATIVO : request.status();
        OffsetDateTime now = now();

        Asset asset = Asset.builder()
                .nome(request.modelo().trim())
                .fabricante(trimToNull(request.fabricante()))
                .tipo(type)
                .status(status)
                .numeroSerie(serial)
                .purchaseDate(request.purchaseDate())
                .warrantyEndDate(request.warrantyEndDate())
                .warrantyProvider(trimToNull(request.warrantyProvider()))
                .cliente(cliente)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return response(assetRepository.save(asset));
    }

    public List<AssetResponseDTO> listByClienteId(UUID clienteId) {
        return listAll(clienteId, null, null, null, null);
    }

    public List<AssetResponseDTO> listAll(
            UUID clienteId,
            String tipo,
            String status,
            String warrantyState,
            String query
    ) {
        UUID effectiveClientId = authorizationService.clientScope(clienteId);
        AssetType typeFilter = parseNullableType(tipo);
        AssetStatus statusFilter = parseNullableStatus(status);
        WarrantyState warrantyFilter = parseNullableWarrantyState(warrantyState);
        String normalizedQuery = normalizeQuery(query);

        AuthenticatedUser currentUser = authorizationService.currentUser();
        List<Asset> assets = currentUser.role() == UserRole.TECNICO
                ? assetRepository.findAllReadableByTechnician(currentUser.id())
                : assetRepository.findAllByCliente_IdOrderByCreatedAtDesc(
                        effectiveClientId
                );

        return assets.stream()
                .filter(asset -> effectiveClientId == null
                        || asset.getCliente().getId().equals(effectiveClientId))
                .filter(asset -> typeFilter == null || asset.getTipo() == typeFilter)
                .filter(asset -> statusFilter == null || effectiveStatus(asset) == statusFilter)
                .filter(asset -> warrantyFilter == null
                        || AssetResponseDTO.projectWarranty(
                                asset,
                                effectiveClock()
                        ).state() == warrantyFilter)
                .filter(asset -> matchesQuery(asset, normalizedQuery))
                .map(this::response)
                .toList();
    }

    public AssetResponseDTO findById(UUID assetId) {
        Asset asset = findAsset(assetId);
        requireCanRead(asset);
        return response(asset);
    }

    @Transactional
    public AssetResponseDTO update(UUID assetId, AssetUpdateRequestDTO request) {
        Asset asset = findAsset(assetId);
        requireCanEdit(asset);

        if (request.clienteId() != null
                && !request.clienteId().equals(asset.getCliente().getId())) {
            throw new ForbiddenOperationException(
                    "O proprietário de um ativo não pode ser alterado."
            );
        }

        validateDates(request.purchaseDate(), request.warrantyEndDate());
        String serial = normalizeSerial(request.serial());
        requireUniqueSerial(serial, assetId);

        asset.setModelo(request.modelo().trim());
        asset.setFabricante(trimToNull(request.fabricante()));
        asset.setTipo(parseType(request.tipo()));
        asset.setStatus(request.status());
        asset.setNumeroSerie(serial);
        asset.setPurchaseDate(request.purchaseDate());
        asset.setWarrantyEndDate(request.warrantyEndDate());
        asset.setWarrantyProvider(trimToNull(request.warrantyProvider()));
        asset.setUpdatedAt(now());

        return response(assetRepository.save(asset));
    }

    public List<TicketResponseDTO> listTickets(UUID assetId) {
        Asset asset = findAsset(assetId);
        requireCanRead(asset);
        return ticketRepository.findAllByAsset_IdOrderByDataCriacaoDesc(assetId)
                .stream()
                .filter(authorizationService::canRead)
                .map(this::ticketResponse)
                .toList();
    }

    public List<AssetResponseDTO> listWarrantyAlerts(UUID clienteId) {
        return listAll(
                clienteId,
                null,
                null,
                WarrantyState.EXPIRA_EM_BREVE.name(),
                null
        );
    }

    private Asset findAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private void requireCanCreate() {
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if (currentUser != null && currentUser.role() == UserRole.TECNICO) {
            throw new ForbiddenOperationException(
                    "Técnicos possuem acesso somente de leitura aos ativos."
            );
        }
    }

    private void requireCanRead(Asset asset) {
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if (currentUser.role() == UserRole.CLIENTE
                && currentUser.id().equals(asset.getCliente().getId())) {
            return;
        }
        if (currentUser.role() == UserRole.TECNICO
                && ticketRepository.existsReadableByAssetIdAndTechnicianId(
                        asset.getId(),
                        currentUser.id()
                )) {
            return;
        }
        throw new ForbiddenOperationException(
                "O ativo não está disponível no contexto dos seus chamados."
        );
    }

    private void requireCanEdit(Asset asset) {
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if (currentUser.role() == UserRole.CLIENTE
                && currentUser.id().equals(asset.getCliente().getId())) {
            return;
        }
        throw new ForbiddenOperationException(
                "Somente o cliente proprietário pode editar o ativo."
        );
    }

    private TicketResponseDTO ticketResponse(Ticket ticket) {
        AuthenticatedUser currentUser = authorizationService.currentUser();
        boolean clientOwner = currentUser.role() == UserRole.CLIENTE
                && currentUser.id().equals(ticket.getCliente().getId());
        boolean assignedTechnician = currentUser.role() == UserRole.TECNICO
                && ticket.getTecnico() != null
                && currentUser.id().equals(ticket.getTecnico().getId());
        if (!clientOwner && !assignedTechnician) {
            return TicketResponseDTO.forMarketplaceQueue(
                    ticket,
                    effectiveClock()
            );
        }
        return TicketResponseDTO.from(
                ticket,
                effectiveClock(),
                ticket.getCliente().getPhone()
        );
    }

    private void requireUniqueSerial(String serial, UUID currentAssetId) {
        boolean duplicate = currentAssetId == null
                ? assetRepository.existsByNumeroSerieIgnoreCase(serial)
                : assetRepository.existsByNumeroSerieIgnoreCaseAndIdNot(
                        serial,
                        currentAssetId
                );
        if (duplicate) {
            throw new DuplicateAssetSerialException(serial);
        }
    }

    private void validateDates(LocalDate purchaseDate, LocalDate warrantyEndDate) {
        if (purchaseDate != null
                && warrantyEndDate != null
                && warrantyEndDate.isBefore(purchaseDate)) {
            throw new InvalidRequestException(
                    "A data final da garantia não pode ser anterior à data de compra."
            );
        }
    }

    private AssetType parseType(String value) {
        try {
            AssetType type = AssetType.from(value);
            if (type == null) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("O tipo de ativo informado é inválido.");
        }
    }

    private AssetType parseNullableType(String value) {
        return value == null || value.isBlank() ? null : parseType(value);
    }

    private AssetStatus parseNullableStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AssetStatus.from(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("O status de ativo informado é inválido.");
        }
    }

    private WarrantyState parseNullableWarrantyState(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WarrantyState.from(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("O estado de garantia informado é inválido.");
        }
    }

    private AssetStatus effectiveStatus(Asset asset) {
        return asset.getStatus() == null ? AssetStatus.ATIVO : asset.getStatus();
    }

    private boolean matchesQuery(Asset asset, String query) {
        if (query == null) {
            return true;
        }
        return contains(asset.getId(), query)
                || contains(asset.getModelo(), query)
                || contains(asset.getFabricante(), query)
                || contains(asset.getTipo(), query)
                || contains(asset.getNumeroSerie(), query)
                || contains(asset.getCliente().getName(), query)
                || contains(asset.getCliente().getEmail(), query);
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank()
                ? null
                : normalizeSearchText(query.trim());
    }

    private boolean contains(Object value, String query) {
        return value != null && normalizeSearchText(value.toString()).contains(query);
    }

    private String normalizeSearchText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeSerial(String serial) {
        return serial.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(effectiveClock()).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private AssetResponseDTO response(Asset asset) {
        return AssetResponseDTO.from(asset, effectiveClock());
    }

    private Clock effectiveClock() {
        return clock == null ? Clock.systemUTC() : clock;
    }

    private void requireClientRole(User user) {
        if (user.getRole() != UserRole.CLIENTE) {
            throw new InvalidUserRoleException(user.getId(), UserRole.CLIENTE);
        }
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new InactiveUserException(user.getId());
        }
    }
}
