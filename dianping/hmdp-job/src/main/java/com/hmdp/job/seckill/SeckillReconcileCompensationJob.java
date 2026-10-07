package com.hmdp.job.seckill;

import com.hmdp.service.impl.SeckillReconcileCompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillReconcileCompensationJob {

    @Resource
    private SeckillReconcileCompensationService seckillReconcileCompensationService;

    @Value("${seckill.reconcile.enabled:true}")
    private boolean reconcileEnabled;

    @Scheduled(fixedDelay = 15000)
    public void compensate() {
        if (!reconcileEnabled || seckillReconcileCompensationService == null) {
            return;
        }
        try {
            seckillReconcileCompensationService.processDueTasks();
        } catch (Exception e) {
            log.error("seckill reconcile compensation job failed", e);
        }
    }
}
