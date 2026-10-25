package com.anime.backend.security;

import com.anime.backend.config.WebSocketAuthChannelInterceptor;
import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.entity.Message;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.DiscussionRoomRepository;
import com.anime.backend.repository.MessageRepository;
import com.anime.backend.repository.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP security filters against a local OIDC provider and an isolated database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles("oauth-integration")
class OAuthLoginIntegrationTest {
    private static final String CLIENT_ID = "google-regression-client";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalGoogle GOOGLE = new LocalGoogle();

    @Autowired private Environment environment;
    @Autowired private JdbcTemplate database;
    @Autowired private UserRepository users;
    @Autowired private AnimeRoomRepository animeRooms;
    @Autowired private DiscussionRoomRepository discussionRooms;
    @Autowired private MessageRepository messages;
    @Autowired private JwtUtil jwt;
    @Autowired private WebSocketAuthChannelInterceptor webSocketAuth;

    @DynamicPropertySource
    static void isolatedSettings(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:oauth_login;MODE=MySQL;DB_CLOSE_DELAY=-1");
        properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.jpa.show-sql", () -> "false");
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("jwt.secret", () -> "test-only-oauth-signing-key-that-is-never-used-outside-this-test-12345");
        properties.add("jwt.expiration-ms", () -> "60000");
        properties.add("frontend.url", () -> "http://frontend.example");
        properties.add("spring.security.oauth2.client.registration.google.client-id", () -> CLIENT_ID);
        properties.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
        properties.add("spring.security.oauth2.client.provider.google.issuer-uri", GOOGLE::url);
        properties.add("spring.security.oauth2.client.provider.google.authorization-uri", () -> GOOGLE.url() + "/authorize");
        properties.add("spring.security.oauth2.client.provider.google.token-uri", () -> GOOGLE.url() + "/token");
        properties.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> GOOGLE.url() + "/jwks");
        properties.add("spring.security.oauth2.client.provider.google.user-info-uri", () -> GOOGLE.url() + "/userinfo");
        properties.add("spring.security.oauth2.client.provider.google.user-name-attribute", () -> "sub");
        properties.add("spring.security.oauth2.client.registration.github.client-id", () -> "unused-test-github-client");
        properties.add("spring.security.oauth2.client.registration.github.client-secret", () -> "unused-test-github-secret");
    }

    @BeforeEach
    void resetData() {
        messages.deleteAll();
        discussionRooms.deleteAll();
        animeRooms.deleteAll();
        users.deleteAll();
        GOOGLE.clear();
    }

    @AfterAll
    static void stopProvider() {
        GOOGLE.server.stop(0);
    }

    @Test
    void existingGoogleLoginKeepsTheAccountRoleAndOwnedData() throws Exception {
        User existing = new User();
        existing.setUsername("original_google_name");
        existing.setGoogleId("existing-google-subject");
        existing.setEmail("old.address@example.com");
        existing.setPassword(null); // Main's Google accounts do not have a local password.
        existing.setRole(Role.ADMIN);
        existing = users.saveAndFlush(existing);

        AnimeRoom anime = new AnimeRoom();
        anime.setName("Existing anime");
        anime.setCreatedBy(existing);
        anime = animeRooms.saveAndFlush(anime);
        DiscussionRoom room = new DiscussionRoom();
        room.setTitle("Existing discussion");
        room.setAnimeRoom(anime);
        room.setCreatedBy(existing);
        room = discussionRooms.saveAndFlush(room);
        Message message = new Message();
        message.setContent("Message sent before the merge");
        message.setSender(existing);
        message.setDiscussionRoom(room);
        message = messages.saveAndFlush(message);

        LoginAttempt login = beginGoogleLogin("existing-google-subject", "new.address@example.com");
        String token = finishGoogleLogin(login);
        assertEquals("original_google_name", jwt.extractUsername(token));
        assertGoogleTokenCanConnectToChat(token);
        var self = authenticatedSelf(login.browser(), token);
        var account = JSON.readTree(self.body());
        assertEquals(existing.getId().longValue(), account.get("id").asLong());
        assertEquals("ADMIN", account.get("role").asString());
        assertEquals(1, users.count(), "Signing in must reuse the existing account");
        assertNull(users.findById(existing.getId()).orElseThrow().getPassword());
        assertEquals(existing.getId(), messages.findById(message.getId()).orElseThrow().getSender().getId());
        assertEquals(existing.getId(), animeRooms.findById(anime.getId()).orElseThrow().getCreatedBy().getId());

        // The temporary OAuth session carries state; API requests still need the issued JWT.
        assertEquals(401, get(login.browser(), appUrl("/api/users/self")).statusCode());
        var passwordLogin = login.browser().send(HttpRequest.newBuilder(appUrl("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                        "username", "original_google_name", "password", "password"))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, passwordLogin.statusCode());
    }

    @Test
    void firstGoogleSignInCreatesOneAccountAndTheNextSignInReusesIt() throws Exception {
        LoginAttempt first = beginGoogleLogin("new-google-subject", "new.user@example.com");
        String token = finishGoogleLogin(first);
        var account = JSON.readTree(authenticatedSelf(first.browser(), token).body());
        long createdId = account.get("id").asLong();
        assertEquals("USER", account.get("role").asString());
        User stored = users.findById(createdId).orElseThrow();
        assertEquals("new-google-subject", stored.getGoogleId());
        assertEquals("new.user@example.com", stored.getEmail());

        LoginAttempt second = beginGoogleLogin("new-google-subject", "new.user@example.com");
        var signedInAgain = JSON.readTree(authenticatedSelf(second.browser(), finishGoogleLogin(second)).body());
        assertEquals(createdId, signedInAgain.get("id").asLong());
        assertEquals(1, users.count());
        assertEquals(2, GOOGLE.tokenRequests.get());
    }

    @Test
    void newGoogleSignupAlsoWorksWithTheOlderNotNullPasswordColumn() throws Exception {
        try {
            // Earlier branch databases required a password even for OAuth-only accounts.
            database.execute("ALTER TABLE users ALTER COLUMN password SET NOT NULL");
            LoginAttempt login = beginGoogleLogin("old-schema-google-subject", "old.schema@example.com");
            String token = finishGoogleLogin(login);
            var account = JSON.readTree(authenticatedSelf(login.browser(), token).body());
            User created = users.findById(account.get("id").asLong()).orElseThrow();
            assertEquals("", created.getPassword());
            assertEquals("old-schema-google-subject", created.getGoogleId());
            var passwordLogin = login.browser().send(HttpRequest.newBuilder(appUrl("/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                            "username", created.getUsername(), "password", "password"))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, passwordLogin.statusCode());
        } finally {
            database.execute("ALTER TABLE users ALTER COLUMN password DROP NOT NULL");
        }
    }

    @Test
    void callbackWithWrongStateCannotExchangeTheCodeOrCreateAnAccount() throws Exception {
        LoginAttempt login = beginGoogleLogin("untrusted-google-subject", "untrusted@example.com");
        Map<String, String> parameters = query(login.callback());
        URI callback = URI.create(login.callback().toString().replace(
                encode(parameters.get("state")), encode("wrong-state")));
        var response = get(login.browser(), callback);
        assertEquals(302, response.statusCode());
        Map<String, String> result = query(URI.create(response.headers().firstValue("Location").orElseThrow()));
        assertFalse(result.containsKey("token"));
        assertTrue(result.containsKey("error"));
        assertEquals(0, GOOGLE.tokenRequests.get());
        assertEquals(0, users.count());
    }

    private LoginAttempt beginGoogleLogin(String subject, String email) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var authorization = get(browser, appUrl("/oauth2/authorization/google"));
        assertEquals(302, authorization.statusCode());
        URI provider = URI.create(authorization.headers().firstValue("Location").orElseThrow());
        assertTrue(provider.toString().startsWith(GOOGLE.url() + "/authorize?"));
        assertFalse(query(provider).get("state").isBlank());
        assertFalse(query(provider).get("nonce").isBlank());
        GOOGLE.nextAccount = new GoogleAccount(subject, email, "Google test user");
        var consent = get(browser, provider);
        assertEquals(302, consent.statusCode());
        return new LoginAttempt(browser, URI.create(consent.headers().firstValue("Location").orElseThrow()));
    }

    private String finishGoogleLogin(LoginAttempt login) throws Exception {
        var callback = get(login.browser(), login.callback());
        assertEquals(302, callback.statusCode(), callback.body());
        URI destination = URI.create(callback.headers().firstValue("Location").orElseThrow());
        assertEquals("http://frontend.example/oauth2/callback", destination.toString().split("\\?")[0]);
        String token = query(destination).get("token");
        assertNotNull(token, destination.toString());
        assertTrue(jwt.validateToken(token));
        return token;
    }

    private void assertGoogleTokenCanConnectToChat(String token) {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setLogin("__jwt__");
        connect.setPasscode(token);
        connect.setLeaveMutable(true);
        var frame = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());
        assertSame(frame, webSocketAuth.preSend(frame, null));
        Authentication principal = assertInstanceOf(Authentication.class, connect.getUser());
        assertTrue(principal.isAuthenticated());
        assertEquals("original_google_name", principal.getName());
        assertTrue(principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN")));
    }

    private HttpResponse<String> authenticatedSelf(HttpClient browser, String token) throws Exception {
        var response = browser.send(HttpRequest.newBuilder(appUrl("/api/users/self"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.body().contains("\"password\""));
        return response;
    }

    private URI appUrl(String path) {
        return URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> query(URI uri) {
        return form(uri.getRawQuery());
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        if (body != null) {
            for (String entry : body.split("&")) {
                String[] pair = entry.split("=", 2);
                values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record LoginAttempt(HttpClient browser, URI callback) {}
    private record GoogleAccount(String subject, String email, String name) {}
    private record Authorization(GoogleAccount account, String nonce) {}

    // This provider replaces Google's network endpoints, while Spring's normal OIDC client validates its tokens.
    private static class LocalGoogle {
        final HttpServer server;
        final KeyPair key;
        final Map<String, Authorization> codes = new ConcurrentHashMap<>();
        final Map<String, GoogleAccount> accessTokens = new ConcurrentHashMap<>();
        final AtomicInteger tokenRequests = new AtomicInteger();
        volatile GoogleAccount nextAccount;

        LocalGoogle() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                key = generator.generateKeyPair();
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", this::handle);
                server.start();
            } catch (Exception error) {
                throw new IllegalStateException("Cannot start the local OAuth test provider", error);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void clear() {
            codes.clear();
            accessTokens.clear();
            tokenRequests.set(0);
            nextAccount = null;
        }

        void handle(HttpExchange request) throws IOException {
            try {
                switch (request.getRequestURI().getPath()) {
                    case "/.well-known/openid-configuration" -> json(request, Map.of(
                            "issuer", url(), "authorization_endpoint", url() + "/authorize",
                            "token_endpoint", url() + "/token", "jwks_uri", url() + "/jwks",
                            "userinfo_endpoint", url() + "/userinfo", "response_types_supported", new String[]{"code"},
                            "subject_types_supported", new String[]{"public"},
                            "id_token_signing_alg_values_supported", new String[]{"RS256"}));
                    case "/authorize" -> authorize(request);
                    case "/token" -> token(request);
                    case "/userinfo" -> {
                        String accessToken = request.getRequestHeaders().getFirst("Authorization").substring(7);
                        json(request, claims(accessTokens.get(accessToken)));
                    }
                    case "/jwks" -> {
                        RSAPublicKey publicKey = (RSAPublicKey) key.getPublic();
                        json(request, Map.of("keys", new Object[]{Map.of("kty", "RSA", "use", "sig",
                                "kid", "oauth-regression-key", "alg", "RS256",
                                "n", unsigned(publicKey.getModulus()), "e", unsigned(publicKey.getPublicExponent()))}));
                    }
                    default -> request.sendResponseHeaders(404, -1);
                }
            } catch (RuntimeException error) {
                request.sendResponseHeaders(500, -1);
            } finally {
                request.close();
            }
        }

        void authorize(HttpExchange request) throws IOException {
            Map<String, String> parameters = query(request.getRequestURI());
            String code = UUID.randomUUID().toString();
            codes.put(code, new Authorization(nextAccount, parameters.get("nonce")));
            request.getResponseHeaders().add("Location", parameters.get("redirect_uri")
                    + "?code=" + encode(code) + "&state=" + encode(parameters.get("state")));
            request.sendResponseHeaders(302, -1);
        }

        void token(HttpExchange request) throws IOException {
            tokenRequests.incrementAndGet();
            Map<String, String> parameters = form(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Authorization authorization = codes.remove(parameters.get("code"));
            GoogleAccount account = authorization.account();
            Instant now = Instant.now();
            String idToken = Jwts.builder().header().keyId("oauth-regression-key").and()
                    .issuer(url()).subject(account.subject()).audience().add(CLIENT_ID).and()
                    .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                    .claim("nonce", authorization.nonce()).claims(claims(account))
                    .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
            String accessToken = UUID.randomUUID().toString();
            accessTokens.put(accessToken, account);
            json(request, Map.of("access_token", accessToken, "token_type", "Bearer", "expires_in", 300,
                    "scope", "openid profile email", "id_token", idToken));
        }

        static Map<String, Object> claims(GoogleAccount account) {
            return Map.of("sub", account.subject(), "email", account.email(), "email_verified", true, "name", account.name());
        }

        static String unsigned(BigInteger value) {
            byte[] bytes = value.toByteArray();
            if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        static void json(HttpExchange request, Object value) throws IOException {
            byte[] body = JSON.writeValueAsBytes(value);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, body.length);
            request.getResponseBody().write(body);
        }
    }
}
