package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoucherOrderControllerTest {

    private MockMvc mockMvc;

    private IVoucherOrderService voucherOrderService;

    @BeforeEach
    void setUp() {
        voucherOrderService = mock(IVoucherOrderService.class);
        VoucherOrderController controller = new VoucherOrderController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "voucherOrderService", voucherOrderService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void seckillVoucherPlus_shouldDelegateVoucherIdToService() throws Exception {
        when(voucherOrderService.seckillVoucherPlus(7L)).thenReturn(Result.ok(101L));

        mockMvc.perform(post("/voucher-order/seckill-plus/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(101));

        verify(voucherOrderService).seckillVoucherPlus(7L);
    }
}
