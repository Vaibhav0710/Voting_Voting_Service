package com.voting.votingservice.model;

import com.voting.votingservice.model.enums.VoteStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core voting entity — each row represents a single cast vote.
 * <p>
 * Security layers:
 * <ul>
 *   <li><b>Unique constraint:</b> (user_id, election_id) — absolute DB-level double-vote prevention</li>
 *   <li><b>vote_hash:</b> SHA-256 hash for tamper detection</li>
 *   <li><b>prev_hash:</b> Reserved for optional chain linking (disabled by default)</li>
 *   <li><b>idempotency_key:</b> Client-generated UUID for safe retries</li>
 * </ul>
 */
@Entity
@Table(name = "votes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_election",
                        columnNames = {"user_id", "election_id"}),
                @UniqueConstraint(name = "uq_idempotency_key",
                        columnNames = {"idempotency_key"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true, nullable = false, updatable = false)
    private UUID externalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "election_id", nullable = false, updatable = false)
    private UUID electionId;

    @Column(name = "vote_hash", nullable = false, length = 64, updatable = false)
    private String voteHash;

    @Column(name = "prev_hash", length = 64, updatable = false)
    private String prevHash;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VoteStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void ensureExternalId() {
        if (externalId == null) {
            this.externalId = UUID.randomUUID();
        }
        if (status == null) {
            this.status = VoteStatus.CAST;
        }
    }
}
