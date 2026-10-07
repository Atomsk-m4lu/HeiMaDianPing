package com.hmdp.job.seckill;

import com.hmdp.service.impl.OrderCreateRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class OrderCreateRecoveryJob {

    @Resource
    private OrderCreateRecoveryService orderCreateRecoveryService;

    @Scheduled(fixedDelay = 5000)
    public void run() {
        if (orderCreateRecoveryService == null) {
            return;
        }
        try {
            orderCreateRecoveryService.processDueTasks();
        } catch (Exception e) {
            log.error("order create recovery job failed", e);
        }
    }
}
