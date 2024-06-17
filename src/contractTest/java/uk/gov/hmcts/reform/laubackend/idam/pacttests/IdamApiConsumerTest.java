package uk.gov.hmcts.reform.laubackend.idam.pacttests;

import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.TokenRequest;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "idamApi_oidc", pactVersion = PactSpecVersion.V3)
@ActiveProfiles("pact")
@PactDirectory("pacts")
@MockServerConfig(port = "8891")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class IdamApiConsumerTest {

    private final IdamApi idamApi;

    @Value("${pact.idam.grant_type}")
    private String grantType = "password";

    @Value("${pact.idam.client_id}")
    private String clientId;

    @Value("${pact.idam.client_secret}")
    private String clientSecret;

    @Value("${pact.idam.redirect_uri}")
    private String redirectUri;

    @Value("${pact.idam.username}")
    private String username;

    @Value("${pact.idam.password}")
    private String password;

    @Value("${pact.idam.scope}")
    private String scope;

    public IdamApiConsumerTest(@Autowired IdamApi idamApi) {
        this.idamApi = idamApi;
    }

    @Pact(provider = "idamApi_oidc", consumer = "lauApi")
    public RequestResponsePact expectedPactForGenerateOpenIdToken(PactDslWithProvider builder) {

        String query = UriComponentsBuilder.newInstance()
            .queryParam(
                "redirect_uri",
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
            .queryParam("client_id", clientId)
            .queryParam("grant_type", grantType)
            .queryParam("username", username)
            .queryParam("password", password)
            .queryParam("scope", scope)
            .queryParam("client_secret", clientSecret)
            .build()
            .getQuery();

        assert query != null;
        return builder
            .given("a token is requested")
            .uponReceiving("a token request from LAU_IDAM_API")
            .path("/o/token")
            .method("POST")
            .body(query, APPLICATION_FORM_URLENCODED_VALUE)
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(createAuthResponse())
            .toPact();
    }

    @Pact(provider = "idamApi_oidc", consumer = "lauApi")
    public RequestResponsePact expectedPactForUserInfo(PactDslWithProvider builder) {
        return builder
            .given("userinfo is requested")
            .uponReceiving("a user details request from LAU_IDAM_API")
            .path("/o/userinfo")
            .method("GET")
            .headers(Map.of("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJraWQiOiJiL082T3ZWdjEre"))
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(createUserDetailsResponse())
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "expectedPactForGenerateOpenIdToken")
    public void verifyIdamTokenRequest() {
        TokenResponse token = idamApi.generateOpenIdToken(buildTokenRequestMap());
        assertThat(token.accessToken).startsWith("eyJ0eXAiOiJKV1QiLCJraWQiOiJiL082T3ZWdjEre");
    }

    @Test
    @PactTestFor(pactMethod = "expectedPactForUserInfo")
    public void verifyIdamUserInfoRequest() {
        UserInfo userInfo = idamApi.retrieveUserInfo("Bearer eyJ0eXAiOiJKV1QiLCJraWQiOiJiL082T3ZWdjEre");

        assertThat(userInfo).isNotNull();
        assertThat(userInfo).hasNoNullFieldsOrProperties();
        assertThat(userInfo.getUid()).isEqualTo(username);
        assertThat(userInfo.getGivenName()).isNotBlank();
        assertThat(userInfo.getFamilyName()).isNotBlank();
        assertThat(userInfo.getRoles()).isNotEmpty();
        assertThat(userInfo.getRoles().getFirst()).isEqualTo("user");
    }

    private TokenRequest buildTokenRequestMap() {
        return new TokenRequest(
            clientId,
            clientSecret,
            grantType,
            redirectUri,
            username,
            password,
            scope,
            null, null
        );
    }

    private PactDslJsonBody createUserDetailsResponse() {

        return new PactDslJsonBody()
            .stringType("sub", "61")
            .stringType("uid", username)
            .stringType("name", username)
            .stringType("givenName", "Test")
            .stringType("familyName", "Lau")
            .minArrayLike("roles", 1, PactDslJsonRootValue.stringType("user"), 1);
    }

    private PactDslJsonBody createAuthResponse() {
        return new PactDslJsonBody()
            .stringType("access_token", "eyJ0eXAiOiJKV1QiLCJraWQiOiJiL082T3ZWdjEre")
            .stringType("scope", "openid roles profile");
    }
}
