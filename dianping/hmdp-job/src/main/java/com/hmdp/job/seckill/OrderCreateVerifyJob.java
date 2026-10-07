package com.hmdp.job.seckill;

import com.hmdp.service.impl.OrderCreateVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class OrderCreateVerifyJob {

    @Resource
    private OrderCreateVerificationService orderCreateVerificationService;

    @Scheduled(fixedDelay = 5000)
    public void verify() {
        if (orderCreateVerificationService == null) {
            return;
        }
        try {
            orderCreateVerificationService.verifyDueTasks();
        } catch (Exception e) {
            log.error("order create verification job failed", e);
        }
    }
}
