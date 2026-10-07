package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
@MapperScan({
        "com.hmdp.mapper",
        "com.hmdp.shopdetail.infrastructure.persistence.mapper",
        "com.hmdp.shared.infrastructure.persistence.mapper",
        "com.hmdp.shared.infrastructure.reconciliation.persistence.mapper"
})
@SpringBootApplication
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }
}
