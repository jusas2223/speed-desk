package com.speeddesk.api.service;

import com.speeddesk.api.dto.OrganizationCreateRequestDTO;
import com.speeddesk.api.dto.OrganizationResponseDTO;
import com.speeddesk.api.entity.Organization;
import com.speeddesk.api.exception.DuplicateOrganizationException;
import com.speeddesk.api.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public List<OrganizationResponseDTO> listAll() {
        return organizationRepository.findAllByOrderByNameAsc().stream()
                .map(OrganizationResponseDTO::from)
                .toList();
    }

    @Transactional
    public OrganizationResponseDTO create(OrganizationCreateRequestDTO request) {
        String name = request.name().trim();
        if (organizationRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateOrganizationException(name);
        }

        Organization organization = Organization.builder()
                .name(name)
                .active(true)
                .build();
        return OrganizationResponseDTO.from(organizationRepository.save(organization));
    }
}
