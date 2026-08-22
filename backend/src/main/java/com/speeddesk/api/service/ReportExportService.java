package com.speeddesk.api.service;

import com.speeddesk.api.entity.Asset;
import com.speeddesk.api.entity.Incident;
import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.repository.AssetRepository;
import com.speeddesk.api.repository.IncidentRepository;
import com.speeddesk.api.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportService {

    private static final String UTF_8_BOM = "\uFEFF";

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final IncidentRepository incidentRepository;

    public byte[] ticketsCsv() {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of(
                "ID", "Título", "Tipo", "Categoria", "Prioridade", "Status",
                "Cliente", "Organização", "Técnico", "Ativo", "Criado em",
                "Vencimento", "Resolvido em"
        ));
        for (Ticket ticket : ticketRepository.findAllByOrderByDataCriacaoDesc()) {
            rows.add(row(
                    ticket.getId(), ticket.getTitulo(), ticket.getTicketType(),
                    value(ticket.getCategory(), category -> category.getName()),
                    ticket.getPrioridade(), ticket.getStatus(), ticket.getCliente().getName(),
                    value(ticket.getCliente().getOrganization(), organization -> organization.getName()),
                    value(ticket.getTecnico(), technician -> technician.getName()),
                    value(ticket.getAsset(), asset -> asset.getNome()),
                    ticket.getDataCriacao(), ticket.getDataVencimento(), ticket.getResolvedAt()
            ));
        }
        return csv(rows);
    }

    public byte[] assetsCsv() {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of(
                "ID", "Nome", "Serial", "Tipo", "Status", "Fabricante", "Modelo",
                "Responsável", "Compra", "Fim da garantia", "Criado em"
        ));
        for (Asset asset : assetRepository.findAllByOrderByCreatedAtDesc()) {
            rows.add(row(
                    asset.getId(), asset.getNome(), asset.getNumeroSerie(), asset.getTipo(),
                    asset.getStatus(), asset.getFabricante(), asset.getNome(),
                    asset.getCliente().getName(), asset.getPurchaseDate(), asset.getWarrantyEndDate(),
                    asset.getCreatedAt()
            ));
        }
        return csv(rows);
    }

    public byte[] incidentsCsv() {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of(
                "ID", "Título", "Serviço afetado", "Severidade", "Status",
                "Início", "Resolução", "Criado por", "Chamados vinculados"
        ));
        for (Incident incident : incidentRepository.findAllByOrderByStartedAtDesc()) {
            rows.add(row(
                    incident.getId(), incident.getTitle(), incident.getAffectedService(),
                    incident.getSeverity(), incident.getStatus(), incident.getStartedAt(),
                    incident.getResolvedAt(), incident.getCreatedBy().getName(),
                    incident.getTickets().size()
            ));
        }
        return csv(rows);
    }

    private byte[] csv(List<List<?>> rows) {
        StringBuilder output = new StringBuilder(UTF_8_BOM);
        rows.forEach(row -> {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) output.append(';');
                output.append(escape(row.get(index)));
            }
            output.append("\r\n");
        });
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<?> row(Object... values) {
        return java.util.Arrays.asList(values);
    }

    private String escape(Object value) {
        if (value == null) return "";
        String text = value instanceof OffsetDateTime dateTime
                ? dateTime.toString()
                : String.valueOf(value);
        if (text.contains(";") || text.contains("\"")
                || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private <T> Object value(T source, java.util.function.Function<T, Object> mapper) {
        return source == null ? "" : mapper.apply(source);
    }
}
