package com.speeddesk.api.service;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.exception.InvalidUserRoleException;
import com.speeddesk.api.exception.InactiveUserException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    @Transactional
    public AssetResponseDTO create(AssetRequestDTO request) {
        UUID clientId = authorizationService.clientTarget(request.clienteId());
        User cliente = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        requireClientRole(cliente);
        requireActive(cliente);

        Asset asset = Asset.builder()
                .nome(request.nome().trim())
                .tipo(request.tipo().trim())
                .numeroSerie(request.numeroSerie().trim())
                .cliente(cliente)
                .build();

        return AssetResponseDTO.from(assetRepository.save(asset));
    }

    public List<AssetResponseDTO> listByClienteId(UUID clienteId) {
        UUID effectiveClientId = authorizationService.clientScope(clienteId);
        return assetRepository.findAllByCliente_Id(effectiveClientId)
                .stream()
                .map(AssetResponseDTO::from)
                .toList();
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
