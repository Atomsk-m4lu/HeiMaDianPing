package com.hmdp.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// Disabled for stress test - using real RocketMQ broker
// @Configuration
public class MockRocketMQConfig {

//    @Bean
//    @Primary
//    public RocketMQTemplate rocketMQTemplate() {
//        return new RocketMQTemplate();
//    }
}
