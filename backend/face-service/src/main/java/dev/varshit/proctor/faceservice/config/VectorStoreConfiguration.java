package dev.varshit.proctor.faceservice.config;

import dev.varshit.proctor.faceservice.embedding.PassthroughEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class VectorStoreConfiguration {

    @Bean
    public EmbeddingModel embeddingModel(@Value("${spring.ai.vectorstore.pgvector.dimensions:512}") int dimensions) {
        return new PassthroughEmbeddingModel(dimensions);
    }

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
