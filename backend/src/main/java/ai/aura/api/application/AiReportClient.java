package ai.aura.api.application;

import ai.aura.api.api.ResearchClaimComparisonResponse;
import ai.aura.api.api.ResearchQueryPlanResponse;
import ai.aura.api.api.ResearchReportResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class AiReportClient {

    private final RestClient client;
    private final String internalToken;

    public AiReportClient(
            @Value("${aura.ai.service-url:http://ai:8000}") String serviceUrl,
            @Value("${aura.ai.internal-token:}") String internalToken
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(185));
        this.client = RestClient.builder()
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalToken = internalToken;
    }

    public ResearchReportResponse generate(AiResearchReportRequest request) {
        if (internalToken.isBlank()) {
            throw new AiServiceUnavailableException();
        }
        try {
            ResearchReportResponse response = client.post()
                    .uri("/api/v1/research/report")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .body(request)
                    .retrieve()
                    .body(ResearchReportResponse.class);
            if (response == null) {
                throw new AiServiceUnavailableException();
            }
            return response;
        } catch (RestClientException exception) {
            throw new AiServiceUnavailableException();
        }
    }

    public ResearchQueryPlanResponse suggestQueries(AiResearchQueryPlanRequest request) {
        if (internalToken.isBlank()) {
            throw new AiServiceUnavailableException();
        }
        try {
            ResearchQueryPlanResponse response = client.post()
                    .uri("/api/v1/research/query-plan")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .body(request)
                    .retrieve()
                    .body(ResearchQueryPlanResponse.class);
            if (response == null) {
                throw new AiServiceUnavailableException();
            }
            return response;
        } catch (RestClientException exception) {
            throw new AiServiceUnavailableException();
        }
    }

    public ResearchClaimComparisonResponse compare(AiClaimComparisonRequest request) {
        if (internalToken.isBlank()) {
            throw new AiServiceUnavailableException();
        }
        try {
            ResearchClaimComparisonResponse response = client.post()
                    .uri("/api/v1/research/compare")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .body(request)
                    .retrieve()
                    .body(ResearchClaimComparisonResponse.class);
            if (response == null) {
                throw new AiServiceUnavailableException();
            }
            return response;
        } catch (RestClientException exception) {
            throw new AiServiceUnavailableException();
        }
    }
}
