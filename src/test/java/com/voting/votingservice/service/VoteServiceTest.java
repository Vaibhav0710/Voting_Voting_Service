package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.dto.VoteVerificationResponse;
import com.voting.votingservice.exception.DuplicateVoteException;
import com.voting.votingservice.exception.InvalidCandidateException;
import com.voting.votingservice.exception.VoteAccessDeniedException;
import com.voting.votingservice.mapper.VoteMapper;
import com.voting.votingservice.model.ElectionChainHead;
import com.voting.votingservice.model.Vote;
import com.voting.votingservice.model.enums.VoteStatus;
import com.voting.votingservice.repository.VoteRepository;
import com.voting.votingservice.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    private static final String IDEMPOTENCY_KEY = "key-123";

    @Mock private VoteRepository voteRepository;
    @Mock private CandidateValidator candidateValidator;
    @Mock private VoteRedisStore redisStore;
    @Mock private ChainHeadService chainHeadService;
    @Mock private VoteEventProducer voteEventProducer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VoteOutcomeRecorder outcomeRecorder;

    private final VoteHasher voteHasher = new VoteHasher();
    private final UUID userId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();
    private final UUID electionId = UUID.randomUUID();
    private VoteRequest request;

    @BeforeEach
    void setUp() {
        request = VoteRequest.builder()
                .candidateId(candidateId)
                .electionId(electionId)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private VoteService service(boolean chainEnabled) {
        VotingProperties properties = new VotingProperties(
                new VotingProperties.Redis("vot:", 60, 60),
                new VotingProperties.Chain(chainEnabled));
        return new VoteService(voteRepository, voteHasher, candidateValidator, redisStore, chainHeadService,
                new VoteMapper(), voteEventProducer, eventPublisher, outcomeRecorder, properties);
    }

    private Vote storedVote(UUID owner, String prevHash) {
        Vote vote = Vote.builder()
                .id(1L)
                .externalId(UUID.randomUUID())
                .userId(owner)
                .candidateId(candidateId)
                .electionId(electionId)
                .prevHash(prevHash)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .timestamp(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS))
                .status(VoteStatus.CAST)
                .build();
        vote.setVoteHash(voteHasher.computeHash(vote));
        return vote;
    }

    @Nested
    class CastVote {

        @Test
        void newVote_savesHashedVoteAndPublishesRecordedEvent() {
            when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(i -> i.getArgument(0));

            VoteResponse response = service(false).castVote(userId, request);

            ArgumentCaptor<Vote> saved = ArgumentCaptor.forClass(Vote.class);
            verify(voteRepository).saveAndFlush(saved.capture());
            Vote vote = saved.getValue();
            assertThat(vote.getVoteHash()).isEqualTo(voteHasher.computeHash(vote)).hasSize(64);
            assertThat(vote.getStatus()).isEqualTo(VoteStatus.CAST);
            assertThat(response.getVoteHash()).isEqualTo(vote.getVoteHash());
            assertThat(response.isReplay()).isFalse();

            ArgumentCaptor<VoteRecordedEvent> event = ArgumentCaptor.forClass(VoteRecordedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
            assertThat(event.getValue().voteCast().getVoteId()).isEqualTo(vote.getExternalId());
        }

        @Test
        void sameIdempotencyKeyAfterSuccess_returnsOriginalReceipt() {
            Vote original = storedVote(userId, null);
            when(redisStore.findIdempotentVoteId(userId, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(original.getExternalId()));
            when(voteRepository.findByExternalId(original.getExternalId())).thenReturn(Optional.of(original));

            VoteResponse response = service(false).castVote(userId, request);

            assertThat(response.getVoteId()).isEqualTo(original.getExternalId());
            assertThat(response.getVoteHash()).isEqualTo(original.getVoteHash());
            assertThat(response.isReplay()).isTrue();
            verify(voteRepository, never()).saveAndFlush(any());
            verifyNoInteractions(candidateValidator);
            verify(outcomeRecorder).replayed(userId, original.getExternalId());
        }

        @Test
        void idempotencyKeyFoundOnlyInDatabase_returnsOriginalReceipt() {
            Vote original = storedVote(userId, null);
            when(redisStore.findIdempotentVoteId(userId, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(voteRepository.findByIdempotencyKeyAndUserId(IDEMPOTENCY_KEY, userId))
                    .thenReturn(Optional.of(original));

            VoteResponse response = service(false).castVote(userId, request);

            assertThat(response.getVoteId()).isEqualTo(original.getExternalId());
            assertThat(response.isReplay()).isTrue();
        }

        @Test
        void alreadyVoted_throwsDuplicate() {
            when(redisStore.hasVoted(userId, electionId)).thenReturn(true);

            assertThatThrownBy(() -> service(false).castVote(userId, request))
                    .isInstanceOf(DuplicateVoteException.class);
            verifyNoInteractions(candidateValidator);
            verify(outcomeRecorder).rejected(userId, electionId, VoteOutcomeRecorder.Outcome.DUPLICATE);
        }

        @Test
        void dbUniqueViolation_throwsDuplicate() {
            when(voteRepository.saveAndFlush(any(Vote.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_user_election"));

            assertThatThrownBy(() -> service(false).castVote(userId, request))
                    .isInstanceOf(DuplicateVoteException.class);
            verifyNoInteractions(eventPublisher);
            verify(outcomeRecorder).rejected(userId, electionId, VoteOutcomeRecorder.Outcome.DUPLICATE);
        }

        @Test
        void invalidCandidate_isRejectedBeforeSaving() {
            doThrow(new InvalidCandidateException("Candidate is DISQUALIFIED"))
                    .when(candidateValidator).ensureCandidateValid(candidateId, electionId);

            assertThatThrownBy(() -> service(false).castVote(userId, request))
                    .isInstanceOf(InvalidCandidateException.class)
                    .hasMessage("Candidate is DISQUALIFIED");
            verify(voteRepository, never()).saveAndFlush(any());
            verify(outcomeRecorder).rejected(userId, electionId, VoteOutcomeRecorder.Outcome.INVALID_CANDIDATE);
        }

        @Test
        void chainEnabled_usesHeadHashAndAdvancesHead() {
            ElectionChainHead head = new ElectionChainHead(electionId, "abc123");
            when(chainHeadService.lockHead(electionId)).thenReturn(head);
            when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(i -> i.getArgument(0));

            service(true).castVote(userId, request);

            ArgumentCaptor<Vote> saved = ArgumentCaptor.forClass(Vote.class);
            verify(voteRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getPrevHash()).isEqualTo("abc123");
            assertThat(head.getLastHash()).isEqualTo(saved.getValue().getVoteHash());
        }

        @Test
        void chainDisabled_prevHashNullAndNoLock() {
            when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(i -> i.getArgument(0));

            service(false).castVote(userId, request);

            ArgumentCaptor<Vote> saved = ArgumentCaptor.forClass(Vote.class);
            verify(voteRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getPrevHash()).isNull();
            verifyNoInteractions(chainHeadService);
        }
    }

    @Nested
    class VerifyVote {

        @Test
        void ownerWithValidHash_marksVerifiedAndPublishes() {
            Vote vote = storedVote(userId, "GENESIS");
            when(voteRepository.findByExternalId(vote.getExternalId())).thenReturn(Optional.of(vote));

            VoteVerificationResponse response = service(true)
                    .verifyVote(vote.getExternalId(), new JwtPrincipal(userId, "voter", "ROLE_VOTER"));

            assertThat(response.isHashValid()).isTrue();
            assertThat(vote.getStatus()).isEqualTo(VoteStatus.VERIFIED);
            verify(voteEventProducer).publishVoteVerified(response);
            verify(outcomeRecorder).verified(vote.getExternalId(), true);
        }

        @Test
        void tamperedHash_marksDisputed() {
            Vote vote = storedVote(userId, "GENESIS");
            vote.setCandidateId(UUID.randomUUID());
            when(voteRepository.findByExternalId(vote.getExternalId())).thenReturn(Optional.of(vote));

            VoteVerificationResponse response = service(true)
                    .verifyVote(vote.getExternalId(), new JwtPrincipal(UUID.randomUUID(), "admin", "ROLE_ADMIN"));

            assertThat(response.isHashValid()).isFalse();
            assertThat(vote.getStatus()).isEqualTo(VoteStatus.DISPUTED);
            verifyNoInteractions(voteEventProducer);
        }

        @Test
        void notOwnerNotAdmin_throwsAccessDenied() {
            Vote vote = storedVote(userId, null);
            when(voteRepository.findByExternalId(vote.getExternalId())).thenReturn(Optional.of(vote));

            assertThatThrownBy(() -> service(true)
                    .verifyVote(vote.getExternalId(), new JwtPrincipal(UUID.randomUUID(), "x", "ROLE_VOTER")))
                    .isInstanceOf(VoteAccessDeniedException.class);
        }
    }

    @Nested
    class ValidateChain {

        @Test
        void intactChain_isValid() {
            Vote first = storedVote(UUID.randomUUID(), "GENESIS");
            Vote second = storedVote(UUID.randomUUID(), first.getVoteHash());
            when(voteRepository.findByElectionIdOrderByIdAsc(electionId)).thenReturn(List.of(first, second));

            ChainValidationResponse response = service(true).validateChain(electionId);

            assertThat(response.isChainValid()).isTrue();
            assertThat(response.getTotalVotesChecked()).isEqualTo(2);
        }

        @Test
        void tamperedHash_countsBrokenLink() {
            Vote first = storedVote(UUID.randomUUID(), "GENESIS");
            Vote second = storedVote(UUID.randomUUID(), first.getVoteHash());
            first.setVoteHash("0".repeat(64));
            when(voteRepository.findByElectionIdOrderByIdAsc(electionId)).thenReturn(List.of(first, second));

            ChainValidationResponse response = service(true).validateChain(electionId);

            assertThat(response.isChainValid()).isFalse();
            assertThat(response.getBrokenLinks()).isEqualTo(2);
            verify(outcomeRecorder).chainValidated(electionId, false);
        }

        @Test
        void unchainedVotes_onlyHashesAreChecked() {
            Vote first = storedVote(UUID.randomUUID(), null);
            Vote second = storedVote(UUID.randomUUID(), null);
            when(voteRepository.findByElectionIdOrderByIdAsc(electionId)).thenReturn(List.of(first, second));

            ChainValidationResponse response = service(false).validateChain(electionId);

            assertThat(response.isChainValid()).isTrue();
            assertThat(response.getBrokenLinks()).isZero();
        }
    }
}
