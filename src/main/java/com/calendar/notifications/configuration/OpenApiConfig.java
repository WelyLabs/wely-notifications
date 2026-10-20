package com.calendar.notifications.configuration;

import com.calendar.notifications.exception.BusinessErrorCode;
import com.calendar.notifications.exception.GlobalErrorHandler;
import com.calendar.notifications.exception.TechnicalErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Names the API that springdoc generates from the controllers, and documents the error shape every route shares. */
@Configuration
public class OpenApiConfig {

    /** Name of the RFC 7807 schema under {@code components.schemas}. */
    static final String PROBLEM_SCHEMA = "Problem";
    static final String CLIENT_ERROR_RESPONSE = "4XX";
    static final String SERVER_ERROR_RESPONSE = "5XX";

    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
    private static final String CODE_DESCRIPTION = "Stable identifier a client branches on. Families: "
            + "BUS, a business rule refused the request: change the request; "
            + "VAL, invalid fields, listed under errors; "
            + "REQ, the framework refused the request (unknown path, method not allowed, unreadable body); "
            + "TEC, a dependency failed: retry later, it is an incident on our side.";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("wely-notifications")
                .version("v1")
                .description("Notification center and Web Push. Calls, messages, event dates and friend requests "
                        + "arrive from Kafka; clients read them over REST and SSE, devices receive them as push."));
    }

    /**
     * Registers the {@code Problem} schema and declares {@code 4XX}/{@code 5XX} problem responses on
     * every operation, so a client generated from the document knows the error shape without
     * reading {@code GlobalErrorHandler}.
     */
    @Bean
    public OpenApiCustomizer problemResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(OpenApiConfig::declareProblemResponses);
        };
    }

    /** Every code this service can answer, read from the enums and the handler so the list cannot drift. */
    static List<String> allErrorCodes() {
        return Stream.of(
                        Arrays.stream(BusinessErrorCode.values()).map(BusinessErrorCode::getCode),
                        Arrays.stream(TechnicalErrorCode.values()).map(TechnicalErrorCode::getCode),
                        Stream.of(GlobalErrorHandler.VALIDATION_FAILED_CODE, GlobalErrorHandler.REQUEST_REFUSED_CODE,
                                GlobalErrorHandler.UNEXPECTED_ERROR_CODE))
                .flatMap(codes -> codes)
                .sorted()
                .toList();
    }

    private static Schema<?> problemSchema() {
        StringSchema code = new StringSchema();
        code.description(CODE_DESCRIPTION);
        code.setEnum(allErrorCodes());

        return new ObjectSchema()
                .description("RFC 7807 problem detail, as answered by every route of this service.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", code)
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("requestId", new StringSchema()
                        .description("Request id, also printed on this request's log lines."))
                .addProperty("errors", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .description("Field name to message; only on " + GlobalErrorHandler.VALIDATION_FAILED_CODE + "."));
    }

    // Declared responses win: an operation that documents its own 4XX or 5XX keeps it.
    private static void declareProblemResponses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        ApiResponses responses = operation.getResponses();
        if (!responses.containsKey(CLIENT_ERROR_RESPONSE)) {
            responses.addApiResponse(CLIENT_ERROR_RESPONSE,
                    problemResponse("Refused: the code tells a business rule (BUS), invalid fields (VAL) "
                            + "or a request the framework could not serve (REQ)."));
        }
        if (!responses.containsKey(SERVER_ERROR_RESPONSE)) {
            responses.addApiResponse(SERVER_ERROR_RESPONSE,
                    problemResponse("Failed on our side: a dependency is down (TEC); retry later."));
        }
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
    }
}
