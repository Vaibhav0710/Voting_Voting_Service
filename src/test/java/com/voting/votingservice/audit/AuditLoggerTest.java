package com.voting.votingservice.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.voting.votingservice.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLoggerTest {

    private final Logger auditLog = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
    private final AuditLogger auditLogger = new AuditLogger();

    @BeforeEach
    void attach() {
        captured.start();
        auditLog.addAppender(captured);
    }

    @AfterEach
    void detach() {
        auditLog.detachAppender(captured);
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordForCurrentUser_usesTheAuthenticatedUserIdAsActor() {
        UUID adminId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(adminId, "admin", "ROLE_ADMIN"), null, List.of()));

        auditLogger.recordForCurrentUser("CANDIDATE_DELETED", "candidate:7", "SUCCESS");

        assertThat(captured.list.get(0).getFormattedMessage())
                .isEqualTo("action=CANDIDATE_DELETED actor=user:" + adminId + " target=candidate:7 outcome=SUCCESS");
    }

    @Test
    void recordForCurrentUser_withoutAuthentication_isAnonymous() {
        auditLogger.recordForCurrentUser("CANDIDATE_CREATED", "candidate:7", "SUCCESS");

        assertThat(captured.list.get(0).getFormattedMessage()).contains("actor=anonymous");
    }
}
