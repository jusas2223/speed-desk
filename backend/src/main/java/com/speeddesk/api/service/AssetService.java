package com.speeddesk.api.service;

import com.speeddesk.api.dto.AssetRequestDTO;
import com.speeddesk.api.dto.AssetResponseDTO;
import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.ClientNotFoundException;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.UserRepository;
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

    @Transactional
    public AssetResponseDTO create(AssetRequestDTO request) {
        User cliente = userRepository.findById(request.clienteId())
                .orElseThrow(() -> new ClientNotFoundException(request.clienteId()));

        Asset asset = Asset.builder()
                .nome(request.nome())
                .tipo(request.tipo())
                .numeroSerie(request.numeroSerie())
                .cliente(cliente)
                .build();

        return AssetResponseDTO.from(assetRepository.save(asset));
    }

    public List<AssetResponseDTO> listByClienteId(UUID clienteId) {
        return assetRepository.findAllByCliente_Id(clienteId)
                .stream()
                .map(AssetResponseDTO::from)
                .toList();
    }
}
