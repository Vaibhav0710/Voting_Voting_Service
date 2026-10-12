package com.voting.votingservice.audit;

import com.voting.votingservice.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Writes security-relevant events to the dedicated {@code AUDIT} logger as
 * {@code action=… actor=… target=… outcome=…}. The fields are structured arguments, so with JSON logging they
 * become searchable fields in ELK.
 * <p>
 * Never pass passwords, tokens or other secrets here.
 */
@Component
public class AuditLogger {

    public static final String LOGGER_NAME = "AUDIT";
    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final String ANONYMOUS = "anonymous";

    /** Records an event performed by the caller of the current request (actor = {@code user:<id>}). */
    public void recordForCurrentUser(String action, String target, String outcome) {
        record(action, currentActor(), target, outcome);
    }

    public void record(String action, String actor, String target, String outcome) {
        AUDIT.info("{} {} {} {}", kv("action", action), kv("actor", actor), kv("target", target), kv("outcome", outcome));
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return "user:" + principal.userId();
        }
        return ANONYMOUS;
    }
}
