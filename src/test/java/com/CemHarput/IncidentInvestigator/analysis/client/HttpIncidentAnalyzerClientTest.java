package com.CemHarput.IncidentInvestigator.analysis.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpIncidentAnalyzerClientTest {

    @Test
    void analyze_shouldClassifyTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOfSatisfying(AnalyzerUnavailableException.class, ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(AnalysisExecutionFailureType.TIMEOUT);
                    assertThat(ex.getMessage()).isEqualTo("Incident analyzer request timed out");
                });
        server.verify();
    }

    @Test
    void analyze_shouldClassifyTimeoutWhileExtractingResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(request -> {
                    InputStream body = new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new SocketTimeoutException("Read timed out");
                        }
                    };
                    MockClientHttpResponse response = new MockClientHttpResponse(body, HttpStatus.OK);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOfSatisfying(AnalyzerUnavailableException.class, ex -> {
                    assertThat(ex.getFailureType()).isEqualTo(AnalysisExecutionFailureType.TIMEOUT);
                    assertThat(ex.getMessage()).isEqualTo("Incident analyzer request timed out");
                });
        server.verify();
    }

    @Test
    void analyze_shouldClassifyConnectionFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(withException(new ConnectException("Connection refused")));
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOfSatisfying(AnalyzerUnavailableException.class, ex -> {
                    assertThat(ex.getFailureType())
                            .isEqualTo(AnalysisExecutionFailureType.CONNECTION_FAILURE);
                    assertThat(ex.getMessage()).isEqualTo("Incident analyzer service is unavailable");
                });
        server.verify();
    }

    @Test
    void analyze_shouldClassifyDownstream4xxWithoutRetrySemantics() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(InvalidAnalyzerRequestException.class)
                .hasMessage("Incident analyzer rejected request with status 422");
        server.verify();
    }

    @Test
    void analyze_shouldExposeRetryabilityForTransient5xxOnly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOfSatisfying(AnalyzerDownstreamException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getMessage()).isEqualTo("Incident analyzer returned status 503");
                });
        server.verify();
    }

    @Test
    void analyze_shouldClassifyMalformedJsonResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analyzer/api/v1/analyze"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        HttpIncidentAnalyzerClient client = new HttpIncidentAnalyzerClient(builder, "http://analyzer");

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(InvalidAnalyzerResponseException.class)
                .hasMessage("Incident analyzer returned an invalid response");
        server.verify();
    }

    private AnalysisRequest request() {
        return new AnalysisRequest(42L, "Latency", "LATENCY", List.of());
    }
}
