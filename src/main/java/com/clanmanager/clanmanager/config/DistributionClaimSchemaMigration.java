package com.clanmanager.clanmanager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DistributionClaimSchemaMigration {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void allowMultipleCustomClaims() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
            if (normalized.contains("postgresql")) {
                jdbcTemplate.execute("alter table distribution_claim_requests alter column source_transaction_id drop not null");
            } else if (normalized.contains("mysql") || normalized.contains("mariadb")) {
                jdbcTemplate.execute("alter table distribution_claim_requests modify source_transaction_id bigint null");
            }
        }
    }
}
