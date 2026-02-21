package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import model.Member;
import service.MemberService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MemberController implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(MemberController.class);
    private MemberService memberService;
    private final int maxBodyBytes;
    private final int maxQueryLength;
    private final int maxNameLength;
    private final int maxGeneration;

    public MemberController(MemberService memberService, int maxBodyBytes, int maxQueryLength, int maxNameLength, int maxGeneration) {
        this.memberService = memberService;
        this.maxBodyBytes = maxBodyBytes;
        this.maxQueryLength = maxQueryLength;
        this.maxNameLength = maxNameLength;
        this.maxGeneration = maxGeneration;
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
                case "PUT":
                    handlePut(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendResponse(exchange, 405, createErrorResponse("Method Not Allowed"), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage());
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
                if (query.startsWith("name=")) {
                    String name = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
                    if (name.trim().isEmpty()) {
                        sendResponse(exchange, 400, createErrorResponse("Name cannot be empty"), "no-store");
                        return;
                    }
                    if (name.length() > maxNameLength || containsControlChars(name)) {
                        sendResponse(exchange, 400, createErrorResponse("Name is invalid"), "no-store");
                        return;
                    }
                    Member member = memberService.findMemberByName(name);
                    if (member != null) {
                        sendResponse(exchange, 200, memberToJson(member).toString(), "public, max-age=60");
                    } else {
                        sendResponse(exchange, 404, createErrorResponse("Member not found"), "no-store");
                    }
                } else {
                    sendResponse(exchange, 400, createErrorResponse("Invalid query parameter"), "no-store");
                }
            } else {
                List<Member> members = memberService.getAllMembers();
                JSONArray jsonArray = new JSONArray();
                for (Member member : members) {
                    jsonArray.put(memberToJson(member));
                }
                sendResponse(exchange, 200, jsonArray.toString(), "public, max-age=60");
            }
        } catch (Exception e) {
            logger.error("Error in handleGet: {}", e.getMessage());
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
            if (!json.has("name") || !json.has("generation") || !json.has("gender")) {
                sendResponse(exchange, 400, createErrorResponse("Missing required fields: name, generation, and gender are required"), "no-store");
                return;
            }

            // 验证字段值
            String name = json.getString("name");
            if (name == null || name.trim().isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Name cannot be empty"), "no-store");
                return;
            }
            if (name.length() > maxNameLength || containsControlChars(name)) {
                sendResponse(exchange, 400, createErrorResponse("Name is invalid"), "no-store");
                return;
            }

            int generation;
            try {
                generation = json.getInt("generation");
                if (generation < 0 || generation > maxGeneration) {
                    sendResponse(exchange, 400, createErrorResponse("Generation is out of range"), "no-store");
                    return;
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Generation must be a valid integer"), "no-store");
                return;
            }

            int gender;
            try {
                gender = json.getInt("gender");
                if (gender != 0 && gender != 1) {
                    sendResponse(exchange, 400, createErrorResponse("Gender must be 0 (Male) or 1 (Female)"), "no-store");
                    return;
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Gender must be a valid integer (0 or 1)"), "no-store");
                return;
            }

            String remark = null;
            if (json.has("remark") && !json.isNull("remark")) {
                remark = json.optString("remark", null);
                if (remark != null && remark.length() > maxQueryLength) {
                    sendResponse(exchange, 400, createErrorResponse("Remark is too long"), "no-store");
                    return;
                }
                if (remark != null && containsControlChars(remark)) {
                    sendResponse(exchange, 400, createErrorResponse("Remark is invalid"), "no-store");
                    return;
                }
            }

            // 添加成员
            Member newMember = memberService.addMember(name, generation, gender, remark);
            if (newMember != null) {
                sendResponse(exchange, 201, memberToJson(newMember).toString(), "no-store");
            } else {
                sendResponse(exchange, 400, createErrorResponse("Failed to add member"), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error in handlePost: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");

            if (pathParts.length != 3) {
                sendResponse(exchange, 400, createErrorResponse("Invalid request path"), "no-store");
                return;
            }

            int memberId;
            try {
                memberId = Integer.parseInt(pathParts[2]);
                if (memberId <= 0) {
                    sendResponse(exchange, 400, createErrorResponse("Member ID must be positive"), "no-store");
                    return;
                }
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid member ID format"), "no-store");
                return;
            }

            String requestBody = readRequestBody(exchange);
            if (requestBody == null) {
                sendResponse(exchange, 413, createErrorResponse("Request body too large"), "no-store");
                return;
            }
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Request body cannot be empty"), "no-store");
                return;
            }

            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid JSON format"), "no-store");
                return;
            }

            if (!json.has("name") && !json.has("remark") && !json.has("gender")) {
                sendResponse(exchange, 400, createErrorResponse("Missing required fields: name, remark, or gender"), "no-store");
                return;
            }

            String name = null;
            if (json.has("name") && !json.isNull("name")) {
                name = json.getString("name");
                if (name.trim().isEmpty()) {
                    sendResponse(exchange, 400, createErrorResponse("Name cannot be empty"), "no-store");
                    return;
                }
                if (name.length() > maxNameLength || containsControlChars(name)) {
                    sendResponse(exchange, 400, createErrorResponse("Name is invalid"), "no-store");
                    return;
                }
            }

            String remark = null;
            if (json.has("remark") && !json.isNull("remark")) {
                remark = json.optString("remark", null);
                if (remark != null && remark.length() > maxQueryLength) {
                    sendResponse(exchange, 400, createErrorResponse("Remark is too long"), "no-store");
                    return;
                }
                if (remark != null && containsControlChars(remark)) {
                    sendResponse(exchange, 400, createErrorResponse("Remark is invalid"), "no-store");
                    return;
                }
            }

            Integer gender = null;
            if (json.has("gender") && !json.isNull("gender")) {
                try {
                    gender = json.getInt("gender");
                    if (gender != 0 && gender != 1) {
                        sendResponse(exchange, 400, createErrorResponse("Gender must be 0 (Male) or 1 (Female)"), "no-store");
                        return;
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 400, createErrorResponse("Gender must be a valid integer (0 or 1)"), "no-store");
                    return;
                }
            }

            Member existing = memberService.findMemberById(memberId);
            if (existing == null) {
                sendResponse(exchange, 404, createErrorResponse("Member not found"), "no-store");
                return;
            }

            String updatedName = name != null ? name : existing.getName();
            String updatedRemark = json.has("remark") ? remark : existing.getRemark();
            int updatedGender = gender != null ? gender : existing.getGender();

            Member updated = memberService.updateMember(memberId, updatedName, updatedGender, updatedRemark);
            if (updated != null) {
                sendResponse(exchange, 200, memberToJson(updated).toString(), "no-store");
            } else {
                sendResponse(exchange, 404, createErrorResponse("Member not found"), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error in handlePut: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");

            if (pathParts.length != 3) {
                sendResponse(exchange, 400, createErrorResponse("Invalid request path"), "no-store");
                return;
            }

            try {
                int memberId = Integer.parseInt(pathParts[2]);
                if (memberId <= 0) {
                    sendResponse(exchange, 400, createErrorResponse("Member ID must be positive"), "no-store");
                    return;
                }

                boolean success = memberService.deleteMember(memberId);
                if (success) {
                    sendResponse(exchange, 200, createSuccessResponse("Member deleted successfully"), "no-store");
                } else {
                    sendResponse(exchange, 404, createErrorResponse("Member not found"), "no-store");
                }
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid member ID format"), "no-store");
            }
        } catch (Exception e) {
            logger.error("Error in handleDelete: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"), "no-store");
        }
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

    private JSONObject memberToJson(Member member) {
        JSONObject json = new JSONObject();
        json.put("id", member.getMemberID());
        json.put("name", member.getName());
        json.put("generation", member.getGeneration());
        json.put("gender", member.getGender());
        json.put("genderText", member.getGender() == 0 ? "Male" : "Female");
        json.put("remark", member.getRemark());
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

    private boolean containsControlChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
