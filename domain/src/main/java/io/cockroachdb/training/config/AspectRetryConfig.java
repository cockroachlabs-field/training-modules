package io.cockroachdb.training.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Role;

import io.cockroachdb.training.common.aspect.ExponentialBackoffRetryHandler;
import io.cockroachdb.training.common.aspect.RetryHandler;
import io.cockroachdb.training.common.aspect.TransactionRetryAspect;

/**
 * Alternative to spring-retry.
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Profile("aop-retry")
public class AspectRetryConfig {
    @Bean
    public TransactionRetryAspect transactionRetryAspect() {
        return new TransactionRetryAspect(transactionRetryHandler());
    }

    @Bean
    public RetryHandler transactionRetryHandler() {
        return new ExponentialBackoffRetryHandler();
    }
}
