package io.cockroachdb.training;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class Chapter2Configuration {
    @Bean
    public PlatformTransactionManager transactionManager(@Autowired DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManager.setRollbackOnCommitFailure(false);
        return transactionManager;
    }
}
