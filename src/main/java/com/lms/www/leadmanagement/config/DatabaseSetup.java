package com.lms.www.leadmanagement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSetup implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Starting automated database schema check...");

        // ── Colleges table ────────────────────────────────────────────────────
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS colleges (" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "  college_name VARCHAR(255) NOT NULL," +
                "  status BOOLEAN NOT NULL DEFAULT TRUE," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")"
            );
            log.info("Database schema: 'colleges' table verified/created.");
        } catch (Exception e) {
            log.warn("Could not create 'colleges' table: {}", e.getMessage());
        }

        // ── Existing patches ──────────────────────────────────────────────────
        try {
            // Force MySQL to update the column to TEXT
            jdbcTemplate.execute("ALTER TABLE certificate MODIFY error_message TEXT");
            
            // Alter attendance status columns to VARCHAR to prevent Enum truncation issues
            jdbcTemplate.execute("ALTER TABLE attendance_sessions MODIFY status VARCHAR(50)");
            jdbcTemplate.execute("ALTER TABLE attendance_daily MODIFY status VARCHAR(50)");
            
            log.info("Database schema updated: 'error_message' column is now TEXT, and attendance statuses are VARCHAR.");
        } catch (Exception e) {
            // If it fails (e.g. column already TEXT or DB locked), we log and continue
            log.warn("Database schema update skipped or failed: {}", e.getMessage());
        }
    }
}
