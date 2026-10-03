package dev.varshit.proctor.persistence.config;

import dev.varshit.proctor.persistence.error.PersistenceExceptionHandler;
import dev.varshit.proctor.persistence.rls.RlsTransaction;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.search.SearchQueryParser;
import dev.varshit.proctor.persistence.sql.R2dbcSqlGateway;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

@AutoConfiguration(after = {R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class})
@Import(PersistenceExceptionHandler.class)
public class PersistenceAutoConfiguration {

    @Bean
    public DatabaseRoleGuard databaseRoleGuard(DatabaseClient client) {
        return new DatabaseRoleGuard(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlGateway sqlGateway(DatabaseClient client) {
        return new R2dbcSqlGateway(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecureTransaction secureTransaction(ReactiveTransactionManager manager, DatabaseClient client) {
        return new RlsTransaction(manager, client);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchQueryParser searchQueryParser() {
        return new SearchQueryParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public PagedQueryRunner pagedQueryRunner(SqlGateway gateway, SearchQueryParser parser) {
        return new PagedQueryRunner(gateway, parser);
    }
}
