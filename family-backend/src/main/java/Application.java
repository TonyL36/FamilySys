import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Properties;

public class Application {
    private static final Logger logger = LogManager.getLogger(Application.class);

    /** 为跨域请求添加 CORS 支持，不限制前端端口：根据请求 Origin 动态回显，允许任意来源访问 */
    private static HttpHandler withCors(HttpHandler handler) {
        return (HttpExchange exchange) -> {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.isEmpty()) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            } else {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            handler.handle(exchange);
        };
    }

    public static void main(String[] args) {
        try {
            Properties prop = loadProperties();
            int port = Integer.parseInt(prop.getProperty("server.port", "8000"));

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // 设置依赖关系
            MemberRepository memberRepository = new MemberRepository();
            MemberService memberService = new MemberService(memberRepository);

            RelationshipRepository relationshipRepository = new RelationshipRepository(memberRepository);
            RelationshipService relationshipService = new RelationshipService(relationshipRepository, memberRepository);

            // 设置控制器（包装 CORS，允许前端跨域访问）
            server.createContext("/member", withCors(new MemberController(memberService)));
            server.createContext("/relationship", withCors(new RelationshipController(relationshipService)));

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
}

