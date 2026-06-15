package com.wageclock.wageclock.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("external")
public class PortOneServiceIntegrationTest {
    private final PortOneService portOneService;

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    public PortOneServiceIntegrationTest(PortOneService portOneService){
        this.portOneService = portOneService;
    }

    @Test
    void createPortOneAccount_success(){
        String portOnePaymentId = UUID.randomUUID().toString();
        PortOneVirtualAccountResponse account = portOneService.createVirtualAccount(portOnePaymentId,
                10000L, "EWA-1", "홍길동");
        assertNotNull(account);
        assertNotNull(account.PaymentId());
        System.out.println(account);
    }
    @Test
    void getVirtualAccountInfo_success(){
        String portOnePaymentId = UUID.randomUUID().toString();
        PortOneVirtualAccountResponse accountResponse = portOneService.createVirtualAccount(portOnePaymentId,
                10000L, "EWA-1", "홍길동");
        String paymentId = accountResponse.PaymentId();
        PortOneVirtualAccountInfoResponse info = portOneService.getVirtualAccountInfo(paymentId);
        assertNotNull(info);
        System.out.println(info);
    }
}
