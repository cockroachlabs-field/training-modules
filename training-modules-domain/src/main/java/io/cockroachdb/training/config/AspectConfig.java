package io.cockroachdb.training.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;

import io.cockroachdb.training.common.aspect.TransactionDecoratorAspect;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class AspectConfig {
    @Bean
    public TransactionDecoratorAspect transactionDecoratorAspect(DataSource dataSource) {
        return new TransactionDecoratorAspect(dataSource);
    }
}
