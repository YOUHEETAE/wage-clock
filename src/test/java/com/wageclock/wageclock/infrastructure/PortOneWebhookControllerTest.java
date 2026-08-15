package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.TestSecurityConfig;
import com.wageclock.wageclock.domain.settlement.BulkSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = PortOneWebhookController.class)
@Import(TestSecurityConfig.class)
public class PortOneWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private BulkSettlementService bulkSettlementService;

    private String payload(String type) {
        return """
              {
                "type": "%s",
                "timestamp": "2024-04-25T10:00:00.000Z",
                "data": {
                  "storeId": "test-store",
                  "paymentId": "test-payment-id",
                  "transactionId": "test-transaction-id"
                }
              }
              """.formatted(type);
    }

    @Test
    void 웹훅_수신_시_결제상태_재조회를_트리거한다() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("Transaction.Paid")))
                .andExpect(status().isOk());

        verify(bulkSettlementService).syncPaymentStatus("test-payment-id");
    }

    @Test
    void 페이로드_타입은_판단에_쓰지_않는다() throws Exception {
        // 위조 가능한 값이므로 신뢰하지 않는다. 어떤 타입이든 재조회로 판단한다.
        for (String type : List.of("Transaction.Cancelled", "Transaction.Failed", "Transaction.None")) {
            mockMvc.perform(post("/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload(type)))
                    .andExpect(status().isOk());
        }

        verify(bulkSettlementService, times(3)).syncPaymentStatus("test-payment-id");
        verify(bulkSettlementService, never()).initiateBulkSettlement(any());
        verify(bulkSettlementService, never()).failedPayment(any());
    }
}
