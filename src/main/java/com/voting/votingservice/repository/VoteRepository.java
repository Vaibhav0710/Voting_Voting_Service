package com.voting.votingservice.repository;

import com.voting.votingservice.model.Vote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByExternalId(UUID externalId);

    boolean existsByUserIdAndElectionId(UUID userId, UUID electionId);

    Page<Vote> findByElectionId(UUID electionId, Pageable pageable);

    Optional<Vote> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    Optional<Vote> findTopByElectionIdOrderByIdDesc(UUID electionId);

    @Query("SELECT v.candidateId AS candidateId, COUNT(v) AS voteCount " +
           "FROM Vote v WHERE v.electionId = :electionId " +
           "GROUP BY v.candidateId")
    List<VoteCountProjection> countVotesByElectionGroupedByCandidate(@Param("electionId") UUID electionId);

    long countByElectionId(UUID electionId);

    /** Insertion order, which is chain order because chained inserts are serialised per election. */
    List<Vote> findByElectionIdOrderByIdAsc(UUID electionId);

    /**
     * Projection interface for vote count aggregation queries.
     */
    interface VoteCountProjection {
        UUID getCandidateId();

        Long getVoteCount();
    }
}
