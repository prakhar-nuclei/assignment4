package com.nuclei.userservice.grpc.server;

import com.nuclei.user.proto.GetUserRequest;
import com.nuclei.user.proto.UserResponse;
import com.nuclei.user.proto.UserServiceGrpc;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.grpc.interceptor.IdempotencyInterceptor;
import com.nuclei.userservice.grpc.mapper.AuthenticationResponseMapper;
import com.nuclei.userservice.grpc.mapper.UserResponseMapper;
import com.nuclei.userservice.security.GrpcJwtAuthInterceptor;
import com.nuclei.userservice.security.GrpcJwtTestConfig;
import com.nuclei.userservice.service.IAuthenticationService;
import com.nuclei.userservice.service.IIdempotencyService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.validation.UserRequestValidator;
import java.time.Instant;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureTestGrpcTransport
@Import({
        GrpcJwtTestConfig.class,
        UserGrpcService.class,
        GrpcJwtAuthInterceptor.class,
        IdempotencyInterceptor.class
})
@ImportGrpcClients(types = UserServiceGrpc.UserServiceBlockingStub.class)
class UserGrpcServiceE2ETest {

    private static final String TEST_USER_ID = "1";
    private static final String TEST_NAME = "Prakhar";
    private static final String TEST_EMAIL = "prakhar@example.com";
    private static final String TEST_ISSUER = "user-service";

    @Autowired
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private IUserService userService;

    @MockitoBean
    private IAuthenticationService authenticationService;

    @MockitoBean
    private IIdempotencyService idempotencyService;

    @MockitoBean
    private UserRequestValidator userRequestValidator;

    @MockitoBean
    private UserResponseMapper userResponseMapper;

    @MockitoBean
    private AuthenticationResponseMapper authenticationResponseMapper;

    @Test
    void getUser_shouldReturnUserForValidJwt() {

        final UserResponseDto userResponse =
                new UserResponseDto(
                        1L,
                        TEST_NAME,
                        TEST_EMAIL
                );

        when(userService.getUser(1L))
                .thenReturn(userResponse);

        final UserResponse grpcResponse =
                UserResponse.newBuilder()
                        .setUserId(1L)
                        .setName(TEST_NAME)
                        .setEmail(TEST_EMAIL)
                        .build();

        when(userResponseMapper.toGrpcResponse(userResponse))
                .thenReturn(grpcResponse);

        final String token = createToken(TEST_USER_ID);

        final Metadata metadata = new Metadata();

        final Metadata.Key<String> authorizationKey =
                Metadata.Key.of(
                        "authorization",
                        Metadata.ASCII_STRING_MARSHALLER
                );

        metadata.put(
                authorizationKey,
                "Bearer " + token
        );

        final UserServiceGrpc.UserServiceBlockingStub authenticatedStub =
                userStub.withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(metadata)
                );

        final GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setUserId(1L)
                        .build();

        final UserResponse response =
                authenticatedStub.getUser(request);

        assertEquals(
                1L,
                response.getUserId()
        );

        assertEquals(
                TEST_NAME,
                response.getName()
        );

        assertEquals(
                TEST_EMAIL,
                response.getEmail()
        );
    }

    private String createToken(final String subject) {

        final Instant now = Instant.now();

        final JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(TEST_ISSUER)
                        .subject(subject)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .build();

        final JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims
                )
        ).getTokenValue();
    }
}