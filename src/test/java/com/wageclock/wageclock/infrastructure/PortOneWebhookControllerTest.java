package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.TestSecurityConfig;
import com.wageclock.wageclock.domain.ewa.PortOneWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = PortOneWebhookController.class)
@Import(TestSecurityConfig.class)
public class PortOneWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    PortOneWebhookService portOneWebhookService;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void webhook_수신_성공()  throws Exception {
        String payload = """
              {
                "type": "Transaction.Paid",
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
