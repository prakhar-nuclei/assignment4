package com.nuclei.userservice.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(
            final ApplicationContext context) {

        applicationContext = context;
    }

    public static <T> T getBean(final Class<T> beanClass) {

        if (applicationContext == null) {
            throw new IllegalStateException(
                    "Spring application context is not initialized"
            );
        }

        return applicationContext.getBean(beanClass);
    }
}