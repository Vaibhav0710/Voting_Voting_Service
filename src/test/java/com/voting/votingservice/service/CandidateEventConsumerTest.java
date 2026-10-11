package com.voting.votingservice.service;

import com.voting.votingservice.dto.CandidateEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CandidateEventConsumerTest {

    @Mock private CandidateStatusCache statusCache;
    @InjectMocks private CandidateEventConsumer consumer;

    private final UUID candidateId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();

    @Test
    void statusChanged_updatesCache() {
        consumer.onCandidateChanged(new CandidateEvent(candidateId, electionId, "DISQUALIFIED"));

        verify(statusCache).put(candidateId, electionId, "DISQUALIFIED");
    }

    @Test
    void deleted_marksCandidateDeleted() {
        consumer.onCandidateDeleted(new CandidateEvent(candidateId, electionId, "ACTIVE"));

        verify(statusCache).put(candidateId, electionId, "DELETED");
    }

    @Test
    void malformedEvent_isIgnored() {
        consumer.onCandidateChanged(new CandidateEvent(null, electionId, "ACTIVE"));

        verify(statusCache, never()).put(any(), any(), anyString());
    }
}
