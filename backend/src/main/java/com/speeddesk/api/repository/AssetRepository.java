package com.speeddesk.api.repository;

import com.speeddesk.api.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findAllByCliente_Id(UUID clienteId);
}
