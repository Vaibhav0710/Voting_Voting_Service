package com.voting.votingservice.service;

import com.voting.votingservice.dto.VoteCastEvent;
import com.voting.votingservice.dto.VoteVerificationResponse;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteEventProducerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private VoteEventProducer producer;

    private final VoteCastEvent event = VoteCastEvent.builder()
            .voteId(UUID.randomUUID())
            .electionId(UUID.randomUUID())
            .candidateId(UUID.randomUUID())
            .build();

    @Test
    void voteCast_isKeyedByElection() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        producer.publishVoteCast(event);

        verify(kafkaTemplate).send("vote.cast", event.getElectionId().toString(), event);
    }

    @Test
    void brokerUnavailableOnSend_doesNotFailTheCaller() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new KafkaException("Send failed", new TimeoutException("metadata")));

        assertThatCode(() -> producer.publishVoteCast(event)).doesNotThrowAnyException();
    }

    @Test
    void asyncSendFailure_doesNotFailTheCaller() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")));

        assertThatCode(() -> producer.publishVoteCast(event)).doesNotThrowAnyException();
    }

    @Test
    void voteVerified_isKeyedByVoteId() {
        UUID voteId = UUID.randomUUID();
        VoteVerificationResponse verification = VoteVerificationResponse.builder().voteId(voteId).build();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        producer.publishVoteVerified(verification);

        verify(kafkaTemplate).send(eq("vote.verified"), eq(voteId.toString()), eq(verification));
    }
}
