package com.speeddesk.api.service;

import com.speeddesk.api.dto.TicketCategoryCreateRequestDTO;
import com.speeddesk.api.dto.TicketCategoryResponseDTO;
import com.speeddesk.api.entity.TicketCategory;
import com.speeddesk.api.exception.DuplicateTicketCategoryException;
import com.speeddesk.api.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;

    public List<TicketCategoryResponseDTO> listActive() {
        return ticketCategoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(TicketCategoryResponseDTO::from)
                .toList();
    }

    @Transactional
    public TicketCategoryResponseDTO create(TicketCategoryCreateRequestDTO request) {
        String name = request.name().trim();
        if (ticketCategoryRepository.existsByNameIgnoreCaseAndTicketType(
                name,
                request.ticketType()
        )) {
            throw new DuplicateTicketCategoryException(name, request.ticketType());
        }

        TicketCategory category = TicketCategory.builder()
                .name(name)
                .ticketType(request.ticketType())
                .active(true)
                .build();
        return TicketCategoryResponseDTO.from(ticketCategoryRepository.save(category));
    }
}
