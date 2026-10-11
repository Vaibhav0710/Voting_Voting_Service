package com.voting.votingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Latest vote hash of one election's chain.
 * <p>
 * Casting a chained vote locks this row ({@code SELECT … FOR UPDATE}), so votes in the same
 * election link one after another while different elections never wait on each other.
 */
@Entity
@Table(name = "election_chain_heads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ElectionChainHead {

    public static final String GENESIS = "GENESIS";

    @Id
    @Column(name = "election_id", nullable = false, updatable = false)
    private UUID electionId;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;
}
