package com.voting.votingservice.repository;

import com.voting.votingservice.model.ElectionChainHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ElectionChainHeadRepository extends JpaRepository<ElectionChainHead, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ElectionChainHead h WHERE h.electionId = :electionId")
    Optional<ElectionChainHead> findForUpdate(@Param("electionId") UUID electionId);

    /** Creates the head if missing; a concurrent creator simply wins (no error). */
    @Modifying
    @Query(value = "INSERT INTO election_chain_heads (election_id, last_hash) VALUES (:electionId, :lastHash) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("electionId") UUID electionId, @Param("lastHash") String lastHash);
}
