package com.hmdp;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HmDianPingApplicationMapperScanTest {

    @Test
    void shouldScanReconciliationMapperPackage() {
        MapperScan mapperScan = HmDianPingApplication.class.getAnnotation(MapperScan.class);
        assertTrue(mapperScan != null
                        && Arrays.asList(mapperScan.value())
                        .contains("com.hmdp.shared.infrastructure.reconciliation.persistence.mapper"),
                "HmDianPingApplication must scan reconciliation mapper package");
    }
}
