package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Service
public class PortOneService {
    private final RestClient restClient;
    private final PortOneProperties portOneProperties;
    private static final ZoneOffset KST = ZoneOffset.of("+09:00");
    private final StringRedisTemplate stringRedisTemplate;
    public PortOneService(RestClient.Builder builder, PortOneProperties portOneProperties,
                          StringRedisTemplate stringRedisTemplate) {
        this.restClient = builder
                .baseUrl("https://api.portone.io")
                .build();
        this.portOneProperties = portOneProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private PortOneTokenResponseToken getAccessToken() {
        String cachedAccessToken = stringRedisTemplate.opsForValue().get("portone:access-token");
        if (cachedAccessToken != null) return new PortOneTokenResponseToken(cachedAccessToken);
        String apiSecret = portOneProperties.getApiSecret();
        try {
            PortOneTokenResponseToken token = restClient.post()
                    .uri("/login/api-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PortOneTokenRequestToken(apiSecret))
                    .retrieve()
                    .body(PortOneTokenResponseToken.class);
            if (token == null) {
                throw new ExternalApiException("PortOne token response is null");
            }
            stringRedisTemplate.opsForValue().set("portone:access-token", token.accessToken(),
                    29 ,TimeUnit.MINUTES);
            return token;
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("Could not get PortOne token response[" + e.getStatusCode() + "] : "
                    + e.getResponseBodyAsString());
        }
    }

    public PortOneVirtualAccountResponse createVirtualAccount(String portOnePaymentId, long totalAmount, Long ewaRequestId, String employerName) {
        PortOneTokenResponseToken token = getAccessToken();
        String channelKey = portOneProperties.getChannelKey();
        String storeId = portOneProperties.getStoreId();
        try {
            PortOneVirtualAccountResponse.InstancePaymentSummary account = restClient.post()
                    .uri("/payments/{paymentId}/instant", portOnePaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token.accessToken())
                    .body(new PortOneVirtualAccountRequest(storeId, channelKey, "EWA-" + ewaRequestId,
                            new PortOneVirtualAccountRequest.Amount(totalAmount), "KRW",
                            new PortOneMethod(new PortOneMethod.
                                    VirtualAccount("SHINHAN",
                                    new PortOneMethod.VirtualAccount.Expiry(OffsetDateTime.now(KST).plusHours(24).toString()),
                                    new PortOneMethod.VirtualAccount.Option("NORMAL"))),
                            new PortOneCustomer(new PortOneCustomer.Name(employerName)))
                   )
                    .retrieve()
                    .body(PortOneVirtualAccountResponse.InstancePaymentSummary.class);
            if (account == null) {
                throw new ExternalApiException("PortOne virtual account response is null");
            }
            return new PortOneVirtualAccountResponse(account, portOnePaymentId);
        }catch (RestClientResponseException e) {
            throw new ExternalApiException("Could not get PortOneVirtualAccount [" + e.getStatusCode() + "] : "
                    + e.getResponseBodyAsString());
        }
    }
    public PortOneVirtualAccountInfoResponse getVirtualAccountInfo(String portOnePaymentId) {
        PortOneTokenResponseToken token = getAccessToken();
        try{
            PortOneVirtualAccountInfoResponse info = restClient.get()
                    .uri("/payments/{paymentId}", portOnePaymentId)
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(PortOneVirtualAccountInfoResponse.class);
            if (info == null) {
                throw new ExternalApiException("PortOne virtual account Info response is null");
            }
            return info;
        }catch(RestClientResponseException e) {
            throw new ExternalApiException("Could not get account info[" + e.getStatusCode() + "] : "
                    +  e.getResponseBodyAsString());
        }
    }
}
