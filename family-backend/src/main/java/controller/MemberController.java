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
import java.util.List;

public class MemberController implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(MemberController.class);
    private MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
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
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendResponse(exchange, 405, createErrorResponse("Method Not Allowed"));
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"));
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                if (query.startsWith("name=")) {
                    String name = query.substring(5);
                    if (name.trim().isEmpty()) {
                        sendResponse(exchange, 400, createErrorResponse("Name cannot be empty"));
                        return;
                    }
                    Member member = memberService.findMemberByName(name);
                    if (member != null) {
                        sendResponse(exchange, 200, memberToJson(member).toString());
                    } else {
                        sendResponse(exchange, 404, createErrorResponse("Member not found"));
                    }
                } else {
                    sendResponse(exchange, 400, createErrorResponse("Invalid query parameter"));
                }
            } else {
                List<Member> members = memberService.getAllMembers();
                JSONArray jsonArray = new JSONArray();
                for (Member member : members) {
                    jsonArray.put(memberToJson(member));
                }
                sendResponse(exchange, 200, jsonArray.toString());
            }
        } catch (Exception e) {
            logger.error("Error in handleGet: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"));
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // 读取请求体
            String requestBody = new String(exchange.getRequestBody().readAllBytes());

            // 验证请求体不为空
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Request body cannot be empty"));
                return;
            }

            // 解析 JSON
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid JSON format"));
                return;
            }

            // 验证必需字段存在
            if (!json.has("name") || !json.has("generation") || !json.has("gender")) {
                sendResponse(exchange, 400, createErrorResponse("Missing required fields: name, generation, and gender are required"));
                return;
            }

            // 验证字段值
            String name = json.getString("name");
            if (name == null || name.trim().isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Name cannot be empty"));
                return;
            }

            int generation;
            try {
                generation = json.getInt("generation");
                if (generation < 0) {
                    sendResponse(exchange, 400, createErrorResponse("Generation cannot be negative"));
                    return;
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Generation must be a valid integer"));
                return;
            }

            int gender;
            try {
                gender = json.getInt("gender");
                if (gender != 0 && gender != 1) {
                    sendResponse(exchange, 400, createErrorResponse("Gender must be 0 (Male) or 1 (Female)"));
                    return;
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorResponse("Gender must be a valid integer (0 or 1)"));
                return;
            }

            // 添加成员
            Member newMember = memberService.addMember(name, generation, gender);
            if (newMember != null) {
                sendResponse(exchange, 201, memberToJson(newMember).toString());
            } else {
                sendResponse(exchange, 400, createErrorResponse("Failed to add member"));
            }
        } catch (Exception e) {
            logger.error("Error in handlePost: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"));
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");

            if (pathParts.length != 3) {
                sendResponse(exchange, 400, createErrorResponse("Invalid request path"));
                return;
            }

            try {
                int memberId = Integer.parseInt(pathParts[2]);
                if (memberId <= 0) {
                    sendResponse(exchange, 400, createErrorResponse("Member ID must be positive"));
                    return;
                }

                boolean success = memberService.deleteMember(memberId);
                if (success) {
                    sendResponse(exchange, 200, createSuccessResponse("Member deleted successfully"));
                } else {
                    sendResponse(exchange, 404, createErrorResponse("Member not found"));
                }
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, createErrorResponse("Invalid member ID format"));
            }
        } catch (Exception e) {
            logger.error("Error in handleDelete: {}", e.getMessage());
            sendResponse(exchange, 500, createErrorResponse("Internal Server Error"));
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private JSONObject memberToJson(Member member) {
        JSONObject json = new JSONObject();
        json.put("id", member.getMemberID());
        json.put("name", member.getName());
        json.put("generation", member.getGeneration());
        json.put("gender", member.getGender());
        json.put("genderText", member.getGender() == 0 ? "Male" : "Female");
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
}