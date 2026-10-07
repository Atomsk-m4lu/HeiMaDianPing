package com.hmdp.job.seckill;

import com.hmdp.service.impl.SeckillReconcileScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillReconcileScanJob {

    @Resource
    private SeckillReconcileScanService seckillReconcileScanService;

    @Value("${seckill.reconcile.enabled:true}")
    private boolean reconcileEnabled;

    @Scheduled(fixedDelay = 15000)
    public void scan() {
        if (!reconcileEnabled || seckillReconcileScanService == null) {
            return;
        }
        try {
            seckillReconcileScanService.scanRecentRecords();
        } catch (Exception e) {
            log.error("seckill reconcile scan job failed", e);
        }
    }
}
