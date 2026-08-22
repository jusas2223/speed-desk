package com.speeddesk.api.controller;

import com.speeddesk.api.dto.AiAssistantRequestDTO;
import com.speeddesk.api.dto.AiAssistantResponseDTO;
import com.speeddesk.api.dto.AiTriageRequestDTO;
import com.speeddesk.api.dto.AiTriageResponseDTO;
import com.speeddesk.api.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/triage")
    public ResponseEntity<AiTriageResponseDTO> triage(
            @Valid @RequestBody AiTriageRequestDTO request
    ) {
        return ResponseEntity.ok(aiService.triage(request));
    }

    @PostMapping("/assistant")
    public ResponseEntity<AiAssistantResponseDTO> assist(
            @Valid @RequestBody AiAssistantRequestDTO request
    ) {
        return ResponseEntity.ok(aiService.assist(request));
    }
}
