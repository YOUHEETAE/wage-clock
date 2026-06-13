package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.TestSecurityConfig;
import com.wageclock.wageclock.domain.ewa.EwaSettlementService;
import com.wageclock.wageclock.domain.settlement.BulkSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = PortOneWebhookController.class)
@Import(TestSecurityConfig.class)
public class PortOneWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    EwaSettlementService ewaSettlementService;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private BulkSettlementService bulkSettlementService;

    @Test
    void webhook_PAID_수신_성공()  throws Exception {
        String payload = """
              {
                "type": "Transaction.Paid",
                "timestamp": "2024-04-25T10:00:00.000Z",
                "data": {
                  "storeId": "test-store",
                  "paymentId": "EWA-test-payment-id",
                  "transactionId": "test-transaction-id"
                }
              }
              """;
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
        verify(ewaSettlementService).approveEwa("EWA-test-payment-id");
    }
    @Test
    void webhook_CANCELLED_수신_성공()  throws Exception {
        String payload = """
              {
                "type": "Transaction.Cancelled",
                "timestamp": "2024-04-25T10:00:00.000Z",
                "data": {
                  "storeId": "test-store",
                  "paymentId": "EWA-test-payment-id",
                  "transactionId": "test-transaction-id"
                }
              }
              """;
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        verify(ewaSettlementService).failEwa("EWA-test-payment-id");
    }
    @Test
    void webhook_FAILED_수신_성공()  throws Exception {
        String payload = """
              {
                "type": "Transaction.Failed",
                "timestamp": "2024-04-25T10:00:00.000Z",
                "data": {
                  "storeId": "test-store",
                  "paymentId": "EWA-test-payment-id",
                  "transactionId": "test-transaction-id"
                }
              }
              """;
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        verify(ewaSettlementService).failEwa("EWA-test-payment-id");
    }
    @Test
    void non_Transaction_Paid_타입_무시() throws Exception {
        String payload = """
              {
                "type": "Transaction.None",
                "timestamp": "2024-04-25T10:00:00.000Z",
                "data": {
                  "storeId": "test-store",
                  "paymentId": "test-payment-id",
                  "transactionId": "test-transaction-id"
                }
              }
              """;
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }
}
