package com.voting.votingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Automates data migrations that Hibernate's ddl-auto: update doesn't handle,
 * such as backfilling new columns for existing data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Running database migration checks...");
        
        try {
            // Backfill the newly added 'status' column for any existing votes
            int updatedRows = jdbcTemplate.update(
                    "UPDATE votes SET status = 'CAST' WHERE status IS NULL"
            );
            
            if (updatedRows > 0) {
                log.info("Successfully backfilled {} existing votes with CAST status.", updatedRows);
            }
        } catch (Exception e) {
            log.warn("Failed to run status backfill migration. This is normal if the table doesn't exist yet. Error: {}", e.getMessage());
        }
    }
}
