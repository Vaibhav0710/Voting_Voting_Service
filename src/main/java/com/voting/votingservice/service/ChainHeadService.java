package com.voting.votingservice.service;

import com.voting.votingservice.model.ElectionChainHead;
import com.voting.votingservice.model.Vote;
import com.voting.votingservice.repository.ElectionChainHeadRepository;
import com.voting.votingservice.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hands out the locked chain head of an election inside the caller's transaction.
 * <p>
 * A missing head is created from the election's newest existing vote (so chains started before
 * this table existed continue unbroken), or {@code GENESIS} for a new election.
 */
@Service
@RequiredArgsConstructor
public class ChainHeadService {

    private final ElectionChainHeadRepository chainHeadRepository;
    private final VoteRepository voteRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public ElectionChainHead lockHead(UUID electionId) {
        return chainHeadRepository.findForUpdate(electionId)
                .orElseGet(() -> createAndLock(electionId));
    }

    private ElectionChainHead createAndLock(UUID electionId) {
        String lastHash = voteRepository.findTopByElectionIdOrderByIdDesc(electionId)
                .map(Vote::getVoteHash)
                .orElse(ElectionChainHead.GENESIS);
        chainHeadRepository.insertIfAbsent(electionId, lastHash);
        return chainHeadRepository.findForUpdate(electionId)
                .orElseThrow(() -> new IllegalStateException("Chain head missing after insert for " + electionId));
    }
}
