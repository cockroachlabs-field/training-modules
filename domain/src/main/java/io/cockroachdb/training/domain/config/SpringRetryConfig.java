package io.cockroachdb.training.domain.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Role;
import org.springframework.retry.annotation.EnableRetry;

import io.cockroachdb.training.common.aspect.AdvisorOrder;
import io.cockroachdb.training.common.retry.TransientExceptionClassifier;
import io.cockroachdb.training.common.retry.TransientExceptionRetryListener;

@Configuration
@EnableRetry(proxyTargetClass = true, order = AdvisorOrder.TRANSACTION_RETRY_ADVISOR)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Profile("!aop-retry")
public class SpringRetryConfig {
    @Bean
    public TransientExceptionClassifier exceptionClassifier() {
        return new TransientExceptionClassifier();
    }

    @Bean
    public TransientExceptionRetryListener transientExceptionRetryListener() {
        return new TransientExceptionRetryListener();
    }
}
