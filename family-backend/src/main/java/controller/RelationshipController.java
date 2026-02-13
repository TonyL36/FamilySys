package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import model.Relationship;
import service.FamilyRelationshipCalculator;
import service.RelationshipService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RelationshipController implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(RelationshipController.class);
    private RelationshipService relationshipService;
    private final int maxBodyBytes;
    private final int maxQueryLength;

    public RelationshipController(RelationshipService relationshipService, int maxBodyBytes, int maxQueryLength) {
        this.relationshipService = relationshipService;
        this.maxBodyBytes = maxBodyBytes;
        this.maxQueryLength = maxQueryLength;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                default:
                    sendResponse(exchange, 405, createErrorResponse("Method Not Allowed"), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            String requestBody = readRequestBody(exchange);
            if (requestBody == null) {
                sendResponse(exchange, 413, createErrorResponse("Request body too large"), "no-store");
                return;
            }

            // 验证请求体不为空
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Request body cannot be empty"), "no-store");
                return;
            }

            // 解析 JSON
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid JSON format"), "no-store");
                return;
            }

            // 验证必需字段存在
            if (!json.has("member1ID") || !json.has("member2ID") || !json.has("relationType")) {
                sendResponse(exchange, 400, createErrorResponse("Missing required fields: member1ID, member2ID, and relationType are required"), "no-store");
                return;
            }

            // 验证字段类型和值
            int member1ID, member2ID, relationType;
            try {
                member1ID = json.getInt("member1ID");
                member2ID = json.getInt("member2ID");
                relationType = json.getInt("relationType");
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid field type: member1ID, member2ID, and relationType must be integers"), "no-store");
                return;
            }

            // 验证字段值的有效性
            if (member1ID <= 0) {
                sendResponse(exchange, 400, createErrorResponse("member1ID must be positive"), "no-store");
                return;
            }
            if (member2ID <= 0) {
                sendResponse(exchange, 400, createErrorResponse("member2ID must be positive"), "no-store");
                return;
            }
            if (relationType < 1 || relationType > 32) {
                sendResponse(exchange, 400, createErrorResponse("relationType must be between 1 and 32"), "no-store");
                return;
            }

            // 验证成员不能是同一个人
            if (member1ID == member2ID) {
                sendResponse(exchange, 400, createErrorResponse("member1ID and member2ID cannot be the same"), "no-store");
                return;
            }

            // 尝试添加关系
            boolean success = relationshipService.addRelationship(member1ID, member2ID, relationType);
            if (success) {
                sendResponse(exchange, 201, createSuccessResponse("Relationship added successfully"), "no-store");
            } else {
                sendResponse(exchange, 400, createErrorResponse("Failed to add relationship. Please check if members exist and the relationship is valid."), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error in handlePost: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                if (query.length() > maxQueryLength) {
                    sendResponse(exchange, 400, createErrorResponse("Query is too long"), "no-store");
                    return;
                }
                if (query.startsWith("memberID=")) {
                    String memberIDStr = query.substring(9);
                    if (memberIDStr.trim().isEmpty()) {
                        sendResponse(exchange, 400, createErrorResponse("memberID cannot be empty"), "no-store");
                        return;
                    }
                    try {
                        int memberID = Integer.parseInt(memberIDStr);
                        if (memberID <= 0) {
                            sendResponse(exchange, 400, createErrorResponse("memberID must be positive"), "no-store");
                            return;
                        }
                        List<Relationship> relationships = relationshipService.getRelationshipsForMember(memberID);
                        sendResponse(exchange, 200, relationshipsToJson(relationships).toString(), "public, max-age=60");
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, createErrorResponse("Invalid memberID format"), "no-store");
                    }
                } else if (query.startsWith("relationID=")) {
                    String relationIDStr = query.substring(11);
                    if (relationIDStr.trim().isEmpty()) {
                        sendResponse(exchange, 400, createErrorResponse("relationID cannot be empty"), "no-store");
                        return;
                    }
                    try {
                        int relationID = Integer.parseInt(relationIDStr);
                        if (relationID <= 0) {
                            sendResponse(exchange, 400, createErrorResponse("relationID must be positive"), "no-store");
                            return;
                        }
                        Relationship relationship = relationshipService.getRelationshipByRelationID(relationID);
                        if (relationship != null) {
                            sendResponse(exchange, 200, relationshipToJson(relationship).toString(), "public, max-age=60");
                        } else {
                            sendResponse(exchange, 404, createErrorResponse("Relationship not found"), "no-store");
                        }
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, createErrorResponse("Invalid relationID format"), "no-store");
                    }
                } else if (query.startsWith("relationType=")) {
                    String relationTypeStr = query.substring(13).split("&")[0].trim();
                    if (relationTypeStr.isEmpty()) {
                        sendResponse(exchange, 400, createErrorResponse("relationType cannot be empty"), "no-store");
                        return;
                    }
                    try {
                        int relationType = Integer.parseInt(relationTypeStr);
                        if (relationType < 1 || relationType > 32) {
                            sendResponse(exchange, 400, createErrorResponse("relationType must be between 1 and 32"), "no-store");
                            return;
                        }
                        List<Relationship> relationships = relationshipService.getRelationshipsByRelationType(relationType);
                        sendResponse(exchange, 200, relationshipsToJson(relationships).toString(), "public, max-age=60");
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, createErrorResponse("Invalid relationType format"), "no-store");
                    }
                } else if (query.startsWith("distantRelative=")) {
                    // 处理远亲关系查询
                    String[] params = query.substring(16).split("&");
                    int member1ID = -1, member2ID = -1;
                    
                    for (String param : params) {
                        if (param.startsWith("member1ID=")) {
                            try {
                                member1ID = Integer.parseInt(param.substring(10));
                            } catch (NumberFormatException e) {
                                sendResponse(exchange, 400, createErrorResponse("Invalid member1ID format"), "no-store");
                                return;
                            }
                        } else if (param.startsWith("member2ID=")) {
                            try {
                                member2ID = Integer.parseInt(param.substring(10));
                            } catch (NumberFormatException e) {
                                sendResponse(exchange, 400, createErrorResponse("Invalid member2ID format"), "no-store");
                                return;
                            }
                        }
                    }
                    
                    if (member1ID <= 0 || member2ID <= 0) {
                        sendResponse(exchange, 400, createErrorResponse("member1ID and member2ID must be positive integers"), "no-store");
                        return;
                    }
                    
                    FamilyRelationshipCalculator.DistantRelativeResult result = relationshipService.findDistantRelative(member1ID, member2ID);
                    sendResponse(exchange, 200, distantRelativeResultToJson(result).toString(), "public, max-age=60");
                } else {
                    sendResponse(exchange, 400, createErrorResponse("Invalid query parameter"), "no-store");
                }
            } else {
                List<Relationship> relationships = relationshipService.getAllRelationships();
                sendResponse(exchange, 200, relationshipsToJson(relationships).toString(), "public, max-age=60");
            }
        } catch (Exception e) {
            logger.error("Error in handleGet: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private JSONObject distantRelativeResultToJson(FamilyRelationshipCalculator.DistantRelativeResult result) {
        JSONObject json = new JSONObject();
        json.put("isDistantRelative", result.isDistantRelative());
        json.put("description", result.getDescription());
        if (result.getPreciseKinshipTerm() != null && !result.getPreciseKinshipTerm().isEmpty()) {
            json.put("preciseKinshipTerm", result.getPreciseKinshipTerm());
        }
        json.put("closestCommonAncestorID", result.getClosestCommonAncestorID());
        json.put("commonAncestorCount", result.getCommonAncestorCount());
        JSONArray nodesArr = new JSONArray();
        for (FamilyRelationshipCalculator.PathNode n : result.getPathNodes()) {
            JSONObject o = new JSONObject();
            o.put("id", n.getId());
            o.put("name", n.getName());
            nodesArr.put(o);
        }
        json.put("pathNodes", nodesArr);
        JSONArray edgesArr = new JSONArray();
        for (FamilyRelationshipCalculator.PathEdge e : result.getPathEdges()) {
            JSONObject o = new JSONObject();
            o.put("fromId", e.getFromId());
            o.put("toId", e.getToId());
            o.put("description", e.getDescription());
            o.put("relationType", e.getRelationType());
            edgesArr.put(o);
        }
        json.put("pathEdges", edgesArr);
        int pathLength = result.getPathEdges().size();
        json.put("pathLength", pathLength);
        // 亲缘系数 φ = (1/2)^(L+R)，L+R 为路径步数，国际通用 Kinship Coefficient
        double kinshipCoefficient = pathLength > 0 ? Math.pow(0.5, pathLength) : 1.0;
        json.put("kinshipCoefficient", kinshipCoefficient);
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

    private JSONArray relationshipsToJson(List<Relationship> relationships) {
        JSONArray jsonArray = new JSONArray();
        for (Relationship relationship : relationships) {
            jsonArray.put(relationshipToJson(relationship));
        }
        return jsonArray;
    }

    private JSONObject relationshipToJson(Relationship relationship) {
        JSONObject json = new JSONObject();
        json.put("relationID", relationship.getRelationID());
        json.put("member1", relationship.getMember1());
        json.put("member1Name", relationship.getMember1Name());
        json.put("member2", relationship.getMember2());
        json.put("member2Name", relationship.getMember2Name());
        json.put("relation", relationship.getRelation());
        json.put("description", relationship.getRelationshipDescription());
        return json;
    }

    private String createErrorResponse(String message) {
        JSONObject json = new JSONObject();
        json.put("error", message);
        return json.toString();
    }

    private String createSuccessResponse(String message) {
        JSONObject json = new JSONObject();
        json.put("message", message);
        return json.toString();
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] data = exchange.getRequestBody().readNBytes(maxBodyBytes + 1);
        if (data.length > maxBodyBytes) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
