package dev.varshit.proctor.notification.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.notification.config.NotificationProperties;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FcmPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(FcmPushGateway.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SKEW = Duration.ofSeconds(60);

    private final WebClient client = WebClient.create();
    private final ObjectMapper mapper;
    private final NotificationProperties.Fcm settings;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public FcmPushGateway(ObjectMapper mapper, NotificationProperties properties) {
        this.mapper = mapper;
        this.settings = properties.fcm();
    }

    @Override
    public Mono<Boolean> verify(FcmCredentials credentials) {
        return accessToken(credentials, true).map(token -> true).onErrorReturn(false);
    }

    @Override
    public Mono<PushResult> send(FcmCredentials credentials, String deviceToken, PushMessage message) {
        return post(credentials, deviceToken, message, false);
    }

    private Mono<PushResult> post(FcmCredentials credentials, String deviceToken, PushMessage message, boolean retried) {
        String url = settings.sendUrl().replace("{project}", credentials.projectId());
        return accessToken(credentials, false).flatMap(token -> client.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body(deviceToken, message))
                        .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                                .map(payload -> new Reply(response.statusCode().value(), payload)))
                        .timeout(TIMEOUT))
                .flatMap(reply -> {
                    if (reply.status() == 401 && !retried) {
                        tokens.remove(credentials.clientEmail());
                        return post(credentials, deviceToken, message, true);
                    }
                    return Mono.just(interpret(reply));
                })
                .onErrorResume(error -> Mono.just(PushResult.failed(abbreviate(error.getMessage()))));
    }

    private PushResult interpret(Reply reply) {
        if (reply.status() >= 200 && reply.status() < 300) {
            return PushResult.sent(abbreviate(reply.body()));
        }
        String body = reply.body() == null ? "" : reply.body();
        if (reply.status() == 404 || body.contains("UNREGISTERED")
                || (reply.status() == 400 && body.contains("registration token"))) {
            return PushResult.unregistered("HTTP " + reply.status() + " token is not registered");
        }
        return PushResult.failed("HTTP " + reply.status() + " " + abbreviate(body));
    }

    private Object body(String deviceToken, PushMessage message) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", message.title());
        if (message.body() != null) {
            notification.put("body", message.body());
        }
        Map<String, Object> fcm = new LinkedHashMap<>();
        fcm.put("token", deviceToken);
        fcm.put("notification", notification);
        if (message.data() != null && !message.data().isEmpty()) {
            fcm.put("data", message.data());
        }
        fcm.put("android", Map.of("priority", message.highPriority() ? "HIGH" : "NORMAL"));
        fcm.put("apns", Map.of("headers", Map.of("apns-priority", message.highPriority() ? "10" : "5")));
        fcm.put("webpush", Map.of("headers", Map.of("Urgency", message.highPriority() ? "high" : "normal")));
        return Map.of("message", fcm);
    }

    private Mono<String> accessToken(FcmCredentials credentials, boolean force) {
        CachedToken cached = tokens.get(credentials.clientEmail());
        if (!force && cached != null && cached.expiresAt().isAfter(Instant.now().plus(SKEW))) {
            return Mono.just(cached.value());
        }
        return Mono.fromCallable(() -> assertion(credentials))
                .flatMap(assertion -> client.post().uri(settings.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                                .with("assertion", assertion))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(TIMEOUT))
                .map(json -> {
                    try {
                        JsonNode node = mapper.readTree(json);
                        String token = node.get("access_token").asText();
                        long expiresIn = node.has("expires_in") ? node.get("expires_in").asLong() : 3600;
                        tokens.put(credentials.clientEmail(), new CachedToken(token, Instant.now().plusSeconds(expiresIn)));
                        return token;
                    } catch (Exception e) {
                        throw new IllegalStateException("Unexpected token response");
                    }
                })
                .doOnError(error -> log.warn("FCM authentication failed: {}", error.getMessage()));
    }

    private String assertion(FcmCredentials credentials) throws Exception {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(credentials.clientEmail())
                .claim("scope", SCOPE)
                .audience().add(settings.tokenUrl()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(privateKey(credentials.privateKeyPem()), Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey privateKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private record Reply(int status, String body) {
    }
}
