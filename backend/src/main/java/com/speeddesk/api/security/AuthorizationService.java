package com.speeddesk.api.security;

import com.speeddesk.api.entity.Ticket;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthorizationService {

    private final CurrentUserProvider currentUserProvider;

    public AuthenticatedUser currentUser() {
        return currentUserProvider.get();
    }

    public UUID clientScope(UUID requestedClientId) {
        AuthenticatedUser currentUser = currentUser();

        if (currentUser.role() == UserRole.CLIENTE) {
            if (requestedClientId != null && !currentUser.id().equals(requestedClientId)) {
                throw new ForbiddenOperationException(
                        "Clientes só podem acessar os próprios recursos."
                );
            }
            return currentUser.id();
        }

        return requestedClientId;
    }

    public UUID clientTarget(UUID requestedClientId) {
        AuthenticatedUser currentUser = currentUser();

        if (currentUser.role() == UserRole.CLIENTE
                && !currentUser.id().equals(requestedClientId)) {
            throw new ForbiddenOperationException(
                    "Clientes só podem cadastrar recursos para si mesmos."
            );
        }

        return requestedClientId;
    }

    public void requireCanAssignTo(UUID technicianId) {
        AuthenticatedUser currentUser = currentUser();

        if (currentUser.role() == UserRole.GERENTE) {
            return;
        }

        if (currentUser.role() == UserRole.TECNICO
                && currentUser.id().equals(technicianId)) {
            return;
        }

        throw new ForbiddenOperationException(
                "Técnicos só podem assumir chamados usando o próprio usuário."
        );
    }

    public void requireCanResolve(Ticket ticket) {
        AuthenticatedUser currentUser = currentUser();

        if (currentUser.role() == UserRole.GERENTE) {
            return;
        }

        if (currentUser.role() == UserRole.TECNICO
                && ticket.getTecnico() != null
                && currentUser.id().equals(ticket.getTecnico().getId())) {
            return;
        }

        throw new ForbiddenOperationException(
                "Somente o técnico atribuído ou um gerente pode resolver o chamado."
        );
    }
}
