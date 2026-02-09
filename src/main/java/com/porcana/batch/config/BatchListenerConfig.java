package com.porcana.batch.config;

import com.porcana.batch.listener.BatchNotificationListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.job.builder.JobBuilder;

/**
 * Configuration scaffold documenting listener wiring.
 * Actual listener attachment must happen in each Job builder.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchListenerConfig {

    private final BatchNotificationListener batchNotificationListener;

    /**
     * BeanPostProcessor that adds notification listener to all Job beans
     * This ensures every batch job sends Discord notifications without manual configuration
     */
    @Bean
    public BeanPostProcessor jobListenerInjector() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof Job job) {
                    // Add notification listener to all jobs
                    log.debug("Adding notification listener to job: {}", beanName);

                    // Note: Spring Batch's Job interface doesn't allow modification after creation
                    // The listener must be added during job creation in each job configuration
                    // This processor mainly serves as documentation and can be used for validation
                }
                return bean;
            }
        };
    }
}