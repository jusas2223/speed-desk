package com.speeddesk.api.service;

import com.speeddesk.api.config.AiProperties;
import com.speeddesk.api.dto.AiAssistantRequestDTO;
import com.speeddesk.api.dto.AiAssistantResponseDTO;
import com.speeddesk.api.dto.AiTriageRequestDTO;
import com.speeddesk.api.dto.AiTriageResponseDTO;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.TicketPriority;
import com.speeddesk.api.entity.TicketType;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.TicketNotFoundException;
import com.speeddesk.api.repository.TicketRepository;
import com.speeddesk.api.security.AuthenticatedUser;
import com.speeddesk.api.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Set<String> HARDWARE_TERMS = Set.of(
            "notebook", "computador", "monitor", "impressora", "teclado", "mouse",
            "switch", "roteador", "wifi", "rede", "cabo", "hd", "ssd", "memoria",
            "nao liga", "tela", "firmware", "equipamento"
    );
    private static final Set<String> SOFTWARE_TERMS = Set.of(
            "software", "sistema", "aplicativo", "app", "erro", "login", "senha",
            "licenca", "erp", "nfe", "windows", "linux", "browser", "navegador",
            "atualizacao", "instalacao", "banco de dados"
    );

    private final GeminiAiClient geminiAiClient;
    private final AiProperties properties;
    private final JsonMapper jsonMapper;
    private final TicketRepository ticketRepository;
    private final AuthorizationService authorizationService;

    public AiTriageResponseDTO triage(AiTriageRequestDTO request) {
        AiTriageResponseDTO local = localTriage(request);
        String prompt = """
                Analise este pedido de assistência técnica e devolva somente o JSON solicitado.
                Título informado: %s
                Descrição: %s
                Use apenas os tipos GERAL, HARDWARE ou SOFTWARE e as prioridades BAIXA, NORMAL, ALTA ou CRITICA.
                Não invente fatos. CRITICA exige interrupção ampla ou risco operacional explícito.
                """.formatted(safe(request.title()), request.description().trim());

        return geminiAiClient.generateStructured(
                        "Você faz triagem técnica segura para o Speed Desk em português do Brasil.",
                        prompt,
                        triageSchema()
                )
                .flatMap(this::parseRemoteTriage)
                .map(remote -> new AiTriageResponseDTO(
                        trim(remote.suggestedTitle(), 255),
                        remote.ticketType(),
                        remote.priority(),
                        trim(remote.summary(), 500),
                        trim(remote.reasoning(), 600),
                        sanitizeList(remote.suggestedQuestions(), 4, 300),
                        Math.clamp(remote.confidence(), 0.0, 1.0),
                        "GEMINI"
                ))
                .orElseGet(() -> withSource(
                        local,
                        properties.remoteAvailable() ? "LOCAL_FALLBACK" : "LOCAL"
                ));
    }

    public AiAssistantResponseDTO assist(AiAssistantRequestDTO request) {
        AuthenticatedUser user = authorizationService.currentUser();
        Ticket ticket = request.ticketId() == null
                ? null
                : ticketRepository.findById(request.ticketId())
                        .orElseThrow(() -> new TicketNotFoundException(request.ticketId()));
        if (ticket != null) authorizationService.requireCanRead(ticket);

        AiAssistantResponseDTO local = localAssistant(request, ticket, user);
        String context = ticket == null ? "Nenhum chamado foi vinculado." : """
                Chamado: %s
                Tipo: %s
                Prioridade: %s
                Status: %s
                Descrição: %s
                """.formatted(
                ticket.getTitulo(),
                ticket.getTicketType(),
                ticket.getPrioridade(),
                ticket.getStatus(),
                ticket.getDescricao()
        );
        String prompt = """
                Perfil de quem pergunta: %s
                %s
                Pergunta: %s
                Produza uma orientação curta, verificável e apropriada ao perfil. Não revele notas internas,
                credenciais ou dados ausentes. Não afirme que uma ação já foi executada.
                """.formatted(user.role(), context, request.message().trim());

        return geminiAiClient.generateStructured(
                        "Você é o assistente do Speed Desk. Responda em português do Brasil com segurança e objetividade.",
                        prompt,
                        assistantSchema()
                )
                .flatMap(this::parseRemoteAssistant)
                .map(remote -> new AiAssistantResponseDTO(
                        request.ticketId(),
                        trim(remote.answer(), 3000),
                        sanitizeList(remote.suggestedActions(), 5, 300),
                        "GEMINI",
                        "Sugestão gerada por IA. Revise antes de aplicar ou enviar."
                ))
                .orElseGet(() -> new AiAssistantResponseDTO(
                        local.ticketId(),
                        local.answer(),
                        local.suggestedActions(),
                        properties.remoteAvailable() ? "LOCAL_FALLBACK" : "LOCAL",
                        local.disclaimer()
                ));
    }

    private AiTriageResponseDTO localTriage(AiTriageRequestDTO request) {
        String text = normalize(safe(request.title()) + ' ' + request.description());
        long hardwareScore = HARDWARE_TERMS.stream().filter(text::contains).count();
        long softwareScore = SOFTWARE_TERMS.stream().filter(text::contains).count();
        TicketType type = hardwareScore > softwareScore
                ? TicketType.HARDWARE
                : softwareScore > hardwareScore ? TicketType.SOFTWARE : TicketType.GERAL;

        TicketPriority priority;
        if (containsAny(text, "todos parados", "servico indisponivel", "producao parada", "incidente critico")) {
            priority = TicketPriority.CRITICA;
        } else if (containsAny(text, "nao liga", "sem acesso", "queda", "bloqueado", "impacto alto")) {
            priority = TicketPriority.ALTA;
        } else if (containsAny(text, "duvida", "solicitacao", "melhoria", "quando possivel")) {
            priority = TicketPriority.BAIXA;
        } else {
            priority = TicketPriority.NORMAL;
        }

        String title = safe(request.title()).trim();
        if (title.isBlank()) title = firstSentence(request.description());
        List<String> questions = switch (type) {
            case HARDWARE -> List.of(
                    "Qual equipamento e número de série foram afetados?",
                    "Há luzes, sons ou mensagens visíveis no equipamento?",
                    "Quando o problema começou e o que mudou antes dele?"
            );
            case SOFTWARE -> List.of(
                    "Qual versão do software e sistema operacional estão em uso?",
                    "Quais passos reproduzem o erro?",
                    "Qual era o resultado esperado e qual foi o resultado atual?"
            );
            case GERAL -> List.of(
                    "Quantas pessoas são afetadas?",
                    "Quando o problema começou?",
                    "Existe alguma mensagem de erro ou evidência adicional?"
            );
        };
        double confidence = hardwareScore == softwareScore ? 0.55 : Math.min(0.9, 0.65 + 0.05 * Math.abs(hardwareScore - softwareScore));
        return new AiTriageResponseDTO(
                trim(title, 255),
                type,
                priority,
                trim(firstSentence(request.description()), 500),
                "Classificação local baseada nos sinais técnicos presentes na descrição.",
                questions,
                confidence,
                "LOCAL"
        );
    }

    private AiAssistantResponseDTO localAssistant(
            AiAssistantRequestDTO request,
            Ticket ticket,
            AuthenticatedUser user
    ) {
        List<String> actions = new ArrayList<>();
        String answer;
        if (ticket == null) {
            answer = "Descreva o sintoma, o impacto, quando começou e qualquer mensagem de erro. Com esses dados, posso ajudar a organizar a próxima ação ou preparar uma resposta para o chamado.";
            actions.add("Informe o equipamento ou software afetado.");
            actions.add("Registre a mensagem de erro exatamente como aparece.");
        } else if (user.role() == UserRole.CLIENTE) {
            answer = "O chamado \"%s\" está como %s. Antes de responder, confirme se o sintoma continua, registre o horário do último teste e informe qualquer mudança percebida. Não compartilhe senhas ou tokens.".formatted(
                    ticket.getTitulo(), ticket.getStatus()
            );
            actions.add("Confirmar se o problema ainda ocorre.");
            actions.add("Adicionar evidências sem dados sensíveis.");
        } else {
            answer = "Sugestão de resposta: Estamos analisando o chamado \"%s\" (%s, prioridade %s). Para avançar com segurança, confirme o cenário atual e registre o próximo teste técnico antes de alterar o status.".formatted(
                    ticket.getTitulo(), ticket.getTicketType(), ticket.getPrioridade()
            );
            actions.add("Validar impacto e possibilidade de reprodução.");
            actions.add("Registrar o resultado do teste no chamado.");
            actions.add("Atualizar o status somente após confirmar a etapa executada.");
        }
        return new AiAssistantResponseDTO(
                request.ticketId(),
                answer,
                actions,
                "LOCAL",
                "Sugestão automatizada. Revise antes de aplicar ou enviar."
        );
    }

    private java.util.Optional<RemoteTriage> parseRemoteTriage(String json) {
        try {
            RemoteTriage value = jsonMapper.readValue(json, RemoteTriage.class);
            if (value.suggestedTitle() == null || value.summary() == null
                    || value.reasoning() == null || value.ticketType() == null
                    || value.priority() == null) return java.util.Optional.empty();
            return java.util.Optional.of(value);
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<RemoteAssistant> parseRemoteAssistant(String json) {
        try {
            RemoteAssistant value = jsonMapper.readValue(json, RemoteAssistant.class);
            return value.answer() == null || value.answer().isBlank()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(value);
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private Map<String, Object> triageSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "suggestedTitle", Map.of("type", "string"),
                        "ticketType", Map.of("type", "string", "enum", List.of("GERAL", "HARDWARE", "SOFTWARE")),
                        "priority", Map.of("type", "string", "enum", List.of("BAIXA", "NORMAL", "ALTA", "CRITICA")),
                        "summary", Map.of("type", "string"),
                        "reasoning", Map.of("type", "string"),
                        "suggestedQuestions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "confidence", Map.of("type", "number")
                ),
                "required", List.of("suggestedTitle", "ticketType", "priority", "summary", "reasoning", "suggestedQuestions", "confidence")
        );
    }

    private Map<String, Object> assistantSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "suggestedActions", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("answer", "suggestedActions")
        );
    }

    private AiTriageResponseDTO withSource(AiTriageResponseDTO value, String source) {
        return new AiTriageResponseDTO(
                value.suggestedTitle(), value.ticketType(), value.priority(), value.summary(),
                value.reasoning(), value.suggestedQuestions(), value.confidence(), source
        );
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String firstSentence(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        int end = clean.indexOf('.');
        return trim(end > 0 ? clean.substring(0, end) : clean, 255);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value, int maxLength) {
        String clean = safe(value).trim().replaceAll("\\s+", " ");
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength).trim();
    }

    private List<String> sanitizeList(List<String> values, int maxItems, int maxLength) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> trim(value, maxLength))
                .limit(maxItems)
                .toList();
    }

    private record RemoteTriage(
            String suggestedTitle,
            TicketType ticketType,
            TicketPriority priority,
            String summary,
            String reasoning,
            List<String> suggestedQuestions,
            double confidence
    ) {
    }

    private record RemoteAssistant(String answer, List<String> suggestedActions) {
    }
}
