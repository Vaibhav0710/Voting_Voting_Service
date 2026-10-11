package com.voting.votingservice.service;

import com.voting.votingservice.config.VotingProperties;
import com.voting.votingservice.dto.ChainValidationResponse;
import com.voting.votingservice.dto.VoteCastEvent;
import com.voting.votingservice.dto.VoteCountResponse;
import com.voting.votingservice.dto.VoteReceiptResponse;
import com.voting.votingservice.dto.VoteRequest;
import com.voting.votingservice.dto.VoteResponse;
import com.voting.votingservice.dto.VoteVerificationResponse;
import com.voting.votingservice.exception.DuplicateVoteException;
import com.voting.votingservice.exception.VoteAccessDeniedException;
import com.voting.votingservice.exception.VoteNotFoundException;
import com.voting.votingservice.mapper.VoteMapper;
import com.voting.votingservice.model.ElectionChainHead;
import com.voting.votingservice.model.Vote;
import com.voting.votingservice.model.enums.VoteStatus;
import com.voting.votingservice.repository.VoteRepository;
import com.voting.votingservice.security.JwtPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Vote casting, receipts, verification and chain validation.
 * <p>
 * Double-vote defence: idempotent replay → Redis voted flag → DB unique (user_id, election_id).
 * Redis and Kafka side effects run after commit ({@link VoteCastSideEffects}).
 */
@Slf4j
@Service
public class VoteService implements VoteServiceInterface {

    private static final String ALREADY_VOTED = "User has already voted in this election.";

    private final VoteRepository voteRepository;
    private final VoteHasher voteHasher;
    private final CandidateValidator candidateValidator;
    private final VoteRedisStore redisStore;
    private final ChainHeadService chainHeadService;
    private final VoteMapper voteMapper;
    private final VoteEventProducer voteEventProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean chainEnabled;

    public VoteService(VoteRepository voteRepository, VoteHasher voteHasher, CandidateValidator candidateValidator,
                       VoteRedisStore redisStore, ChainHeadService chainHeadService, VoteMapper voteMapper,
                       VoteEventProducer voteEventProducer, ApplicationEventPublisher eventPublisher,
                       VotingProperties properties) {
        this.voteRepository = voteRepository;
        this.voteHasher = voteHasher;
        this.candidateValidator = candidateValidator;
        this.redisStore = redisStore;
        this.chainHeadService = chainHeadService;
        this.voteMapper = voteMapper;
        this.voteEventProducer = voteEventProducer;
        this.eventPublisher = eventPublisher;
        this.chainEnabled = properties.chain().enabled();
    }

    @Override
    @Transactional
    public VoteResponse castVote(UUID userId, VoteRequest request) {
        Optional<Vote> previous = findIdempotentVote(userId, request.getIdempotencyKey());
        if (previous.isPresent()) {
            log.info("Idempotent replay for user {} key {}", userId, request.getIdempotencyKey());
            return voteMapper.toReplayResponse(previous.get());
        }
        if (redisStore.hasVoted(userId, request.getElectionId())) {
            throw new DuplicateVoteException(ALREADY_VOTED);
        }
        candidateValidator.ensureCandidateValid(request.getCandidateId(), request.getElectionId());

        Vote saved = persist(buildVote(userId, request));
        eventPublisher.publishEvent(new VoteRecordedEvent(toCastEvent(saved), request.getIdempotencyKey()));
        return voteMapper.toVoteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteReceiptResponse getVoteReceipt(UUID voteExternalId, JwtPrincipal requester) {
        return voteMapper.toReceiptResponse(findOwnedVote(voteExternalId, requester));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserVoted(UUID userId, UUID electionId) {
        return redisStore.hasVoted(userId, electionId)
                || voteRepository.existsByUserIdAndElectionId(userId, electionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoteReceiptResponse> getElectionVotes(UUID electionId, Pageable pageable) {
        return voteRepository.findByElectionId(electionId, pageable).map(voteMapper::toReceiptResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteCountResponse getVoteCounts(UUID electionId) {
        List<VoteCountResponse.CandidateVoteCount> counts = voteRepository
                .countVotesByElectionGroupedByCandidate(electionId).stream()
                .map(p -> new VoteCountResponse.CandidateVoteCount(p.getCandidateId(), p.getVoteCount()))
                .toList();
        return VoteCountResponse.builder()
                .electionId(electionId)
                .totalVotes(voteRepository.countByElectionId(electionId))
                .candidateVoteCounts(counts)
                .build();
    }

    @Override
    @Transactional
    public VoteVerificationResponse verifyVote(UUID voteExternalId, JwtPrincipal requester) {
        Vote vote = findOwnedVote(voteExternalId, requester);
        String computedHash = voteHasher.computeHash(vote);
        boolean valid = computedHash.equals(vote.getVoteHash());
        vote.setStatus(valid ? VoteStatus.VERIFIED : VoteStatus.DISPUTED);

        VoteVerificationResponse response = VoteVerificationResponse.builder()
                .voteId(voteExternalId)
                .hashValid(valid)
                .status(vote.getStatus())
                .storedHash(vote.getVoteHash())
                .computedHash(computedHash)
                .message(valid ? "Vote hash is valid and untampered." : "Vote hash MISMATCH - potential tampering detected.")
                .build();
        if (valid) {
            voteEventProducer.publishVoteVerified(response);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ChainValidationResponse validateChain(UUID electionId) {
        List<Vote> votes = voteRepository.findByElectionIdOrderByIdAsc(electionId);
        int brokenLinks = 0;
        String expectedPrevHash = ElectionChainHead.GENESIS;
        for (Vote vote : votes) {
            // 1. A chained vote must point at the hash of the previous chained vote
            if (vote.getPrevHash() != null && !expectedPrevHash.equals(vote.getPrevHash())) {
                brokenLinks++;
            }
            // 2. Every vote's own hash must still match its data
            if (!voteHasher.computeHash(vote).equals(vote.getVoteHash())) {
                brokenLinks++;
            }
            // 3. Unchained votes (chaining off) do not move the expected link
            if (vote.getPrevHash() != null) {
                expectedPrevHash = vote.getVoteHash();
            }
        }
        boolean valid = brokenLinks == 0;
        return ChainValidationResponse.builder()
                .electionId(electionId)
                .chainValid(valid)
                .totalVotesChecked(votes.size())
                .brokenLinks(brokenLinks)
                .message(chainMessage(votes.isEmpty(), valid, brokenLinks))
                .build();
    }

    private Optional<Vote> findIdempotentVote(UUID userId, String idempotencyKey) {
        return redisStore.findIdempotentVoteId(userId, idempotencyKey)
                .flatMap(voteRepository::findByExternalId)
                .or(() -> voteRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId));
    }

    private Vote buildVote(UUID userId, VoteRequest request) {
        ElectionChainHead head = chainEnabled ? chainHeadService.lockHead(request.getElectionId()) : null;
        Vote vote = Vote.builder()
                .externalId(UUID.randomUUID())
                .userId(userId)
                .candidateId(request.getCandidateId())
                .electionId(request.getElectionId())
                .prevHash(head == null ? null : head.getLastHash())
                .idempotencyKey(request.getIdempotencyKey())
                .timestamp(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS))
                .status(VoteStatus.CAST)
                .build();
        vote.setVoteHash(voteHasher.computeHash(vote));
        if (head != null) {
            head.setLastHash(vote.getVoteHash());
        }
        return vote;
    }

    private Vote persist(Vote vote) {
        try {
            return voteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException e) {
            log.warn("Unique constraint rejected vote for user {} in election {}", vote.getUserId(), vote.getElectionId());
            throw new DuplicateVoteException(ALREADY_VOTED);
        }
    }

    private Vote findOwnedVote(UUID voteExternalId, JwtPrincipal requester) {
        Vote vote = voteRepository.findByExternalId(voteExternalId)
                .orElseThrow(() -> new VoteNotFoundException("Vote not found with ID: " + voteExternalId));
        if (!requester.isAdmin() && !vote.getUserId().equals(requester.userId())) {
            throw new VoteAccessDeniedException("You are not authorized to access this vote.");
        }
        return vote;
    }

    private static VoteCastEvent toCastEvent(Vote vote) {
        return VoteCastEvent.builder()
                .voteId(vote.getExternalId())
                .userId(vote.getUserId())
                .candidateId(vote.getCandidateId())
                .electionId(vote.getElectionId())
                .voteHash(vote.getVoteHash())
                .timestamp(vote.getTimestamp())
                .build();
    }

    private static String chainMessage(boolean empty, boolean valid, int brokenLinks) {
        if (empty) {
            return "No votes in this election yet.";
        }
        return valid ? "Chain is fully valid." : "Chain is broken. Found " + brokenLinks + " invalid links.";
    }
}
