package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import service.RelationshipService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class KinshipNetworkController implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(KinshipNetworkController.class);
    private final RelationshipService relationshipService;
    private final int maxQueryLength;

    public KinshipNetworkController(RelationshipService relationshipService, int maxQueryLength) {
        this.relationshipService = relationshipService;
        this.maxQueryLength = maxQueryLength;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method Not Allowed"), "no-store");
                return;
            }

            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || query.isBlank()) {
                sendResponse(exchange, 400, createErrorResponse("memberID is required"), "no-store");
                return;
            }
            if (query.length() > maxQueryLength) {
                sendResponse(exchange, 400, createErrorResponse("Query is too long"), "no-store");
                return;
            }

            int memberID = -1;
            int generations = 2;
            String[] params = query.split("&");
            for (String param : params) {
                String decoded = URLDecoder.decode(param, StandardCharsets.UTF_8);
                if (decoded.startsWith("memberID=")) {
                    try {
                        memberID = Integer.parseInt(decoded.substring(9));
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, createErrorResponse("Invalid memberID format"), "no-store");
                        return;
                    }
                } else if (decoded.startsWith("generations=")) {
                    try {
                        generations = Integer.parseInt(decoded.substring(12));
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, createErrorResponse("Invalid generations format"), "no-store");
                        return;
                    }
                }
            }

            if (memberID <= 0) {
                sendResponse(exchange, 400, createErrorResponse("memberID must be a positive integer"), "no-store");
                return;
            }
            if (generations < 1 || generations > 4) {
                sendResponse(exchange, 400, createErrorResponse("generations must be between 1 and 4"), "no-store");
                return;
            }

            RelationshipService.KinshipNetworkResult result = relationshipService.getKinshipNetwork(memberID, generations);
            if (result == null) {
                sendResponse(exchange, 404, createErrorResponse("成员不存在或无可用数据"), "no-store");
                return;
            }

            sendResponse(exchange, 200, kinshipNetworkToJson(result).toString(), "public, max-age=60");
        } catch (Exception e) {
            logger.error("Error in handle: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private JSONObject kinshipNetworkToJson(RelationshipService.KinshipNetworkResult result) {
        JSONObject json = new JSONObject();
        json.put("centerId", result.getCenterId());
        json.put("generations", result.getGenerations());
        json.put("centerGeneration", result.getCenterGeneration());
        json.put("hiddenRelationsCount", result.getHiddenRelationsCount());
        JSONArray nodesArr = new JSONArray();
        for (RelationshipService.KinshipNetworkNode node : result.getNodes()) {
            JSONObject obj = new JSONObject();
            obj.put("id", node.getId());
            obj.put("name", node.getName());
            obj.put("gender", node.getGender());
            obj.put("generation", node.getGeneration());
            obj.put("level", node.getLevel());
            nodesArr.put(obj);
        }
        json.put("nodes", nodesArr);
        JSONArray edgesArr = new JSONArray();
        for (RelationshipService.KinshipNetworkEdge edge : result.getEdges()) {
            JSONObject obj = new JSONObject();
            obj.put("fromId", edge.getFromId());
            obj.put("toId", edge.getToId());
            obj.put("relationType", edge.getRelationType());
            obj.put("description", edge.getDescription());
            obj.put("edgeType", edge.getEdgeType());
            edgesArr.put(obj);
        }
        json.put("edges", edgesArr);
        return json;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String cacheControl) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().add("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().add("Cache-Control", cacheControl);
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private String createErrorResponse(String message) {
        JSONObject json = new JSONObject();
        json.put("error", message);
        return json.toString();
    }
}
