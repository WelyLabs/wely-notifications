package com.calendar.notifications.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link OpenApiConfig#openApi}, {@link OpenApiConfig#problemResponses} and the two things that make the documentation useful:
 * that it answers without a token, and that it describes the paths the gateway actually routes.
 *
 * <p>The specification is reachable in-cluster and in dev only. The gateway forwards
 * {@code /api/v1/<service>/**} and nothing else, so none of these paths is exposed publicly.
 */
// A running server, not bindToApplicationContext: springdoc registers its routes on the web
// handler, and the context-bound client does not see them — the first version of this test 404ed
// for that reason, which masked the real defect the last test here guards against.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private ApplicationContext context;

    @Value("${local.server.port}")
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("the generated document names the service rather than the application class")
    void openApi_shouldDescribeTheService() {
        var info = context.getBean(OpenAPI.class).getInfo();

        assertThat(info.getTitle()).isEqualTo("wely-notifications");
        assertThat(info.getVersion()).isEqualTo("v1");
        assertThat(info.getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("the specification answers without a token")
    void apiDocs_shouldBeReachableUnauthenticated() {
        client().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("wely-notifications");
    }

    @Test
    @DisplayName("the Swagger UI and its assets load without a token")
    void swaggerUi_shouldBeReachableUnauthenticated() {
        client().get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk();

        client().get().uri("/webjars/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("the documented paths carry the service prefix the gateway strips")
    void apiDocs_shouldDocumentPrefixedPaths() {
        // The regression this guards: WebConfig used to prefix every @RestController on the
        // classpath, springdoc's own included. That moved the specification itself to
        // /notifications-service/v3/api-docs behind authentication, and /v3/api-docs answered 404.
        byte[] body = client().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"/notifications-service/");
    }

    @Test
    @DisplayName("the Problem schema is registered, with every code of the service as the code enum")
    void problemResponses_shouldRegisterTheProblemSchema() {
        OpenAPI openApi = new OpenAPI();

        new OpenApiConfig().problemResponses().customise(openApi);

        Schema<?> problem = openApi.getComponents().getSchemas().get(OpenApiConfig.PROBLEM_SCHEMA);
        assertThat(problem).isNotNull();
        assertThat(problem.getProperties()).containsKeys("type", "title", "status", "detail", "instance", "code",
                "timestamp", "requestId", "errors");
        Schema<?> code = problem.getProperties().get("code");
        assertThat(code.getEnum().stream().map(Object::toString).toList())
                .contains("NTF-BUS-001", "NTF-TEC-001")
                .containsExactlyInAnyOrderElementsOf(OpenApiConfig.allErrorCodes());
        assertThat(code.getDescription()).contains("BUS", "VAL", "REQ", "TEC");
    }

    @Test
    @DisplayName("every operation gets 4XX and 5XX problem responses, and keeps the ones it declared itself")
    void problemResponses_shouldDeclareProblemResponses_withoutOverridingDeclaredOnes() {
        ApiResponse ownClientError = new ApiResponse().description("declared by the operation");
        Operation undocumented = new Operation();
        Operation documented = new Operation().responses(new ApiResponses().addApiResponse("4XX", ownClientError));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/a", new PathItem().get(undocumented))
                .addPathItem("/b", new PathItem().post(documented)));

        new OpenApiConfig().problemResponses().customise(openApi);

        assertThat(undocumented.getResponses()).containsKeys("4XX", "5XX");
        assertThat(undocumented.getResponses().get("5XX").getContent().get("application/problem+json")
                .getSchema().get$ref()).isEqualTo("#/components/schemas/Problem");
        assertThat(documented.getResponses().get("4XX")).isSameAs(ownClientError);
        assertThat(documented.getResponses()).containsKey("5XX");
    }

    @Test
    @DisplayName("the served document carries the Problem schema and the problem responses")
    void apiDocs_shouldDocumentTheProblemResponses() {
        String operation = "$.paths['/notifications-service/notifications'].get.responses";
        client().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.schemas.Problem.properties.code.type").isEqualTo("string")
                .jsonPath("$.components.schemas.Problem.properties.code.enum").value(codes ->
                        assertThat(codes.toString()).contains("NTF-BUS-001", "NTF-TEC-001"))
                .jsonPath(operation + "['4XX'].content['application/problem+json'].schema.$ref")
                .isEqualTo("#/components/schemas/Problem")
                .jsonPath(operation + "['5XX'].content['application/problem+json'].schema.$ref")
                .isEqualTo("#/components/schemas/Problem");
    }
}
