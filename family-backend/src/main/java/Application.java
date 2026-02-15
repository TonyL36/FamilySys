import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import controller.KinshipNetworkController;
import controller.MemberController;
import controller.RelationshipController;
import repository.MemberRepository;
import repository.RelationshipRepository;
import service.MemberService;
import service.RelationshipService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

public class Application {
    private static final Logger logger = LogManager.getLogger(Application.class);
    private static final Map<String, RateLimitEntry> rateLimit = new ConcurrentHashMap<>();
    private static int rateLimitPerMinute = 300;
    private static String apiKey = "";
    private static Set<String> allowedOrigins = Set.of("*");

    private static HttpHandler withSecurity(HttpHandler handler) {
        return (HttpExchange exchange) -> {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.isEmpty()) {
                if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
                }
            } else if (allowedOrigins.contains("*")) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With, X-API-Key");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (apiKey != null && !apiKey.isEmpty()) {
                String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (requestKey == null || !requestKey.equals(apiKey)) {
                    byte[] payload = "{\"error\":\"Unauthorized\"}".getBytes();
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(401, payload.length);
                    exchange.getResponseBody().write(payload);
                    exchange.close();
                    return;
                }
            }
            if (rateLimitPerMinute > 0) {
                String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
                long now = Instant.now().getEpochSecond();
                RateLimitEntry entry = rateLimit.computeIfAbsent(ip, k -> new RateLimitEntry(now));
                if (!entry.allow(now, rateLimitPerMinute)) {
                    byte[] payload = "{\"error\":\"Too Many Requests\"}".getBytes();
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(429, payload.length);
                    exchange.getResponseBody().write(payload);
                    exchange.close();
                    return;
                }
            }
            handler.handle(exchange);
        };
    }

    public static void main(String[] args) {
        try {
            Properties prop = loadProperties();
            int port = Integer.parseInt(prop.getProperty("server.port", "8000"));
            apiKey = prop.getProperty("security.apiKey", "");
            rateLimitPerMinute = Integer.parseInt(prop.getProperty("security.rateLimitPerMinute", "300"));
            String allowed = prop.getProperty("security.allowedOrigins", "*");
            if (allowed != null && !allowed.isBlank()) {
                allowedOrigins = Arrays.stream(allowed.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
            }
            int maxBodyBytes = Integer.parseInt(prop.getProperty("security.maxBodyBytes", "16384"));
            int maxQueryLength = Integer.parseInt(prop.getProperty("security.maxQueryLength", "512"));
            int maxNameLength = Integer.parseInt(prop.getProperty("security.maxNameLength", "50"));
            int maxGeneration = Integer.parseInt(prop.getProperty("security.maxGeneration", "100"));

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // 设置依赖关系
            MemberRepository memberRepository = new MemberRepository();
            MemberService memberService = new MemberService(memberRepository);

            RelationshipRepository relationshipRepository = new RelationshipRepository(memberRepository);
            RelationshipService relationshipService = new RelationshipService(relationshipRepository, memberRepository);

            // 设置控制器（包装 CORS，允许前端跨域访问）
            server.createContext("/member", withSecurity(new MemberController(memberService, maxBodyBytes, maxQueryLength, maxNameLength, maxGeneration)));
            server.createContext("/relationship", withSecurity(new RelationshipController(relationshipService, maxBodyBytes, maxQueryLength)));
            server.createContext("/kinship-network", withSecurity(new KinshipNetworkController(relationshipService, maxQueryLength)));

            server.setExecutor(null);
            server.start();
            logger.info("Server started on port {}", port);
        } catch (IOException e) {
            logger.error("Error starting server: {}", e.getMessage());
        }
    }

    private static Properties loadProperties() {
        Properties prop = new Properties();
        try (InputStream input = Application.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                logger.error("Unable to find application.properties");
                return prop;
            }
            prop.load(input);
        } catch (IOException ex) {
            logger.error("Error loading properties file: {}", ex.getMessage());
        }
        return prop;
    }

    private static class RateLimitEntry {
        private long windowStart;
        private int count;

        private RateLimitEntry(long now) {
            this.windowStart = now;
            this.count = 0;
        }

        private synchronized boolean allow(long now, int limit) {
            if (now - windowStart >= 60) {
                windowStart = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
