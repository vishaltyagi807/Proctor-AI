package dev.varshit.proctor.customfields;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CustomFieldsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CustomFieldValidator customFieldValidator() {
        return new CustomFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomFieldDefinitionReader customFieldDefinitionReader(SqlGateway gateway) {
        return new SqlCustomFieldDefinitionReader(gateway);
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomFieldValueStore customFieldValueStore(SqlGateway gateway, ObjectMapper mapper) {
        return new SqlCustomFieldValueStore(gateway, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomFieldContextLoader customFieldContextLoader(SqlGateway gateway) {
        return new SqlCustomFieldContextLoader(gateway);
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomFieldService customFieldService(CustomFieldDefinitionReader reader, CustomFieldValueStore store,
                                                 CustomFieldValidator validator) {
        return new DefaultCustomFieldService(reader, store, validator);
    }
}
