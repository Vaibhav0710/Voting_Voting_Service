package com.voting.votingservice.service;

import com.voting.votingservice.client.CandidateClient;
import com.voting.votingservice.dto.*;
import com.voting.votingservice.exception.AccessDeniedException;
import com.voting.votingservice.exception.DuplicateVoteException;
import com.voting.votingservice.exception.InvalidCandidateException;
import com.voting.votingservice.exception.VoteNotFoundException;
import com.voting.votingservice.mapper.VoteMapper;
import com.voting.votingservice.model.Vote;
import com.voting.votingservice.model.enums.VoteStatus;
import com.voting.votingservice.repository.VoteRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Core business logic for casting votes safely with high concurrency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService implements VoteServiceInterface {

    private final VoteRepository voteRepository;
    private final HashingService hashingService;
    private final CandidateClient candidateClient;
    private final StringRedisTemplate redisTemplate;
    private final VoteMapper voteMapper;
    private final VoteEventProducer voteEventProducer;

    @Value("${voting.redis.key-prefix:vot:}")
    private String redisPrefix;

    @Value("${voting.redis.voted-ttl:86400}")
    private long votedTtl;

    @Value("${voting.redis.idempotency-ttl:86400}")
    private long idempotencyTtl;

    @Override
    @Transactional
    public VoteResponse castVote(UUID userId, VoteRequest request) {
        String votedKey = redisPrefix + "voted:" + userId + ":" + request.getElectionId();
        String idempotencyKey = redisPrefix + "idempotency:" + request.getIdempotencyKey();

        // 1. Double Vote Check via Redis
        if (Boolean.TRUE.equals(redisTemplate.hasKey(votedKey))) {
            throw new DuplicateVoteException("User has already voted in this election.");
        }

        // 2. Candidate Validation
        try {
            candidateClient.validateCandidate(request.getCandidateId(), request.getElectionId());
        } catch (FeignException e) {
            log.error("Candidate validation failed: {}", e.getMessage());
            throw new InvalidCandidateException("Candidate is invalid or not active for this election.");
        }

        // 3. Idempotency Check
        String cachedHash = redisTemplate.opsForValue().get(idempotencyKey);
        if (cachedHash != null) {
            log.info("Returning cached response for idempotency key: {}", request.getIdempotencyKey());
            return VoteResponse.builder()
                    .voteHash(cachedHash)
                    .message("Vote captured previously (Idempotent response)")
                    .build();
        }

        // 4. Hash Generation and DB entity building
        LocalDateTime now = LocalDateTime.now();
        long timestampSeconds = now.toEpochSecond(ZoneOffset.UTC);
        
        Optional<Vote> lastVoteOpt = voteRepository.findTopByElectionIdOrderByTimestampDesc(request.getElectionId());
        String prevHash = lastVoteOpt.map(Vote::getVoteHash).orElse("GENESIS");

        String dataToHash = userId.toString() + request.getCandidateId().toString() + 
                            request.getElectionId().toString() + timestampSeconds + prevHash;
        String newHash = hashingService.generateHash(dataToHash);

        Vote vote = Vote.builder()
                .userId(userId)
                .candidateId(request.getCandidateId())
                .electionId(request.getElectionId())
                .voteHash(newHash)
                .prevHash(prevHash)
                .idempotencyKey(request.getIdempotencyKey())
                .timestamp(now)
                .status(VoteStatus.CAST)
                .build();

        // 5. DB Persistence with Race Condition Safety
        try {
            vote = voteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Double vote detected at DB constraint level for user {} in election {}", userId, request.getElectionId());
            throw new DuplicateVoteException("User has already voted in this election.");
        }

        // 6. Redis State Updates
        redisTemplate.opsForValue().set(votedKey, "1", votedTtl, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(idempotencyKey, newHash, idempotencyTtl, TimeUnit.SECONDS);

        // 7. Kafka Event
        VoteResponse response = voteMapper.toVoteResponse(vote);
        voteEventProducer.publishVoteCast(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public VoteReceiptResponse getVoteReceipt(UUID voteExternalId, UUID requestingUserId, String role) {
        Vote vote = voteRepository.findByExternalId(voteExternalId)
                .orElseThrow(() -> new VoteNotFoundException("Vote not found with ID: " + voteExternalId));
                
        if (!"ROLE_ADMIN".equals(role) && !vote.getUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("You are not authorized to view this vote receipt.");
        }
        
        return voteMapper.toReceiptResponse(vote);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserVoted(UUID userId, UUID electionId) {
        String votedKey = redisPrefix + "voted:" + userId + ":" + electionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(votedKey))) {
            return true;
        }
        return voteRepository.existsByUserIdAndElectionId(userId, electionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoteReceiptResponse> getElectionVotes(UUID electionId, Pageable pageable) {
        return voteRepository.findByElectionId(electionId, pageable)
                .map(voteMapper::toReceiptResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteCountResponse getVoteCounts(UUID electionId) {
        List<VoteRepository.VoteCountProjection> projections = voteRepository.countVotesByElectionGroupedByCandidate(electionId);
        long totalVotes = voteRepository.countByElectionId(electionId);
        
        List<VoteCountResponse.CandidateVoteCount> candidateCounts = projections.stream()
                .map(p -> VoteCountResponse.CandidateVoteCount.builder()
                        .candidateId(p.getCandidateId())
                        .voteCount(p.getVoteCount())
                        .build())
                .collect(Collectors.toList());
                
        return VoteCountResponse.builder()
                .electionId(electionId)
                .totalVotes(totalVotes)
                .candidateVoteCounts(candidateCounts)
                .build();
    }

    @Override
    @Transactional
    public VoteVerificationResponse verifyVote(UUID voteExternalId) {
        Vote vote = voteRepository.findByExternalId(voteExternalId)
                .orElseThrow(() -> new VoteNotFoundException("Vote not found with ID: " + voteExternalId));
                
        long timestampSeconds = vote.getTimestamp().toEpochSecond(ZoneOffset.UTC);
        String dataToHash = vote.getUserId().toString() + vote.getCandidateId().toString() + 
                            vote.getElectionId().toString() + timestampSeconds + vote.getPrevHash();
        
        String computedHash = hashingService.generateHash(dataToHash);
        boolean isValid = computedHash.equals(vote.getVoteHash());
        
        if (isValid && vote.getStatus() != VoteStatus.VERIFIED) {
            vote.setStatus(VoteStatus.VERIFIED);
            voteRepository.save(vote);
        } else if (!isValid && vote.getStatus() != VoteStatus.DISPUTED) {
            vote.setStatus(VoteStatus.DISPUTED);
            voteRepository.save(vote);
        }
        
        VoteVerificationResponse response = VoteVerificationResponse.builder()
                .voteId(voteExternalId)
                .hashValid(isValid)
                .status(vote.getStatus())
                .storedHash(vote.getVoteHash())
                .computedHash(computedHash)
                .message(isValid ? "Vote hash is valid and untampered." : "Vote hash MISMATCH - potential tampering detected.")
                .build();
                
        if (isValid) {
            voteEventProducer.publishVoteVerified(response);
        }
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ChainValidationResponse validateChain(UUID electionId) {
        List<Vote> votes = voteRepository.findByElectionIdOrderByTimestampAsc(electionId);
        
        if (votes.isEmpty()) {
            return ChainValidationResponse.builder()
                    .electionId(electionId)
                    .chainValid(true)
                    .totalVotesChecked(0)
                    .brokenLinks(0)
                    .message("No votes in this election yet.")
                    .build();
        }
        
        int brokenLinks = 0;
        String expectedPrevHash = "GENESIS";
        
        for (Vote vote : votes) {
            // 1. Verify prevHash matches the actual expected prevHash
            if (!expectedPrevHash.equals(vote.getPrevHash())) {
                brokenLinks++;
            }
            
            // 2. Verify vote's own hash hasn't been tampered with
            long timestampSeconds = vote.getTimestamp().toEpochSecond(ZoneOffset.UTC);
            String dataToHash = vote.getUserId().toString() + vote.getCandidateId().toString() + 
                                vote.getElectionId().toString() + timestampSeconds + vote.getPrevHash();
            String computedHash = hashingService.generateHash(dataToHash);
            
            if (!computedHash.equals(vote.getVoteHash())) {
                brokenLinks++;
            }
            
            // Next vote should point to this vote's hash
            expectedPrevHash = vote.getVoteHash();
        }
        
        boolean isValid = brokenLinks == 0;
        
        return ChainValidationResponse.builder()
                .electionId(electionId)
                .chainValid(isValid)
                .totalVotesChecked(votes.size())
                .brokenLinks(brokenLinks)
                .message(isValid ? "Chain is fully valid." : "Chain is broken. Found " + brokenLinks + " invalid links.")
                .build();
    }
}
