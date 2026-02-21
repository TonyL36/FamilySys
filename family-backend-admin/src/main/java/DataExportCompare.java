import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataExportCompare {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  compare [oldDir] [newDir] [outHtml]");
            System.exit(1);
        }
        String command = args[0].toLowerCase();
        if (!"compare".equals(command)) {
            System.out.println("Unknown command: " + command);
            System.exit(1);
        }
        Path baseDir = Paths.get("").toAbsolutePath();
        Path defaultOldDir = baseDir.resolve("../exports/old").normalize();
        Path defaultNewDir = baseDir.resolve("../exports/current").normalize();
        Path defaultOut = baseDir.resolve("../exports/diff-report.html").normalize();

        Path oldDir = args.length > 1 ? Paths.get(args[1]) : defaultOldDir;
        Path newDir = args.length > 2 ? Paths.get(args[2]) : defaultNewDir;
        Path outHtml = args.length > 3 ? Paths.get(args[3]) : defaultOut;

        compare(oldDir, newDir, outHtml);
    }

    private static void compare(Path oldDir, Path newDir, Path outHtml) throws Exception {
        JSONArray oldMembersArr = readJsonArray(oldDir.resolve("members.json"));
        JSONArray newMembersArr = readJsonArray(newDir.resolve("members.json"));
        JSONArray oldRelArr = readJsonArray(oldDir.resolve("base_relationships.json"));
        JSONArray newRelArr = readJsonArray(newDir.resolve("base_relationships.json"));

        Map<Integer, JSONObject> oldMembers = indexMembers(oldMembersArr);
        Map<Integer, JSONObject> newMembers = indexMembers(newMembersArr);

        List<JSONObject> membersAdded = new ArrayList<>();
        List<JSONObject> membersRemoved = new ArrayList<>();
        List<MemberChange> membersChanged = new ArrayList<>();

        for (Map.Entry<Integer, JSONObject> e : newMembers.entrySet()) {
            int id = e.getKey();
            JSONObject newM = e.getValue();
            JSONObject oldM = oldMembers.get(id);
            if (oldM == null) {
                membersAdded.add(newM);
            } else {
                if (!sameMember(oldM, newM)) {
                    membersChanged.add(new MemberChange(oldM, newM));
                }
            }
        }
        for (Map.Entry<Integer, JSONObject> e : oldMembers.entrySet()) {
            if (!newMembers.containsKey(e.getKey())) {
                membersRemoved.add(e.getValue());
            }
        }

        Set<String> oldRel = indexRelationships(oldRelArr);
        Set<String> newRel = indexRelationships(newRelArr);

        List<String> relAdded = new ArrayList<>();
        List<String> relRemoved = new ArrayList<>();

        for (String r : newRel) {
            if (!oldRel.contains(r)) {
                relAdded.add(r);
            }
        }
        for (String r : oldRel) {
            if (!newRel.contains(r)) {
                relRemoved.add(r);
            }
        }

        membersAdded.sort(Comparator.comparingInt(m -> m.getInt("id")));
        membersRemoved.sort(Comparator.comparingInt(m -> m.getInt("id")));
        membersChanged.sort(Comparator.comparingInt(c -> c.newMember.getInt("id")));
        relAdded.sort(String::compareTo);
        relRemoved.sort(String::compareTo);

        String html = buildHtmlReport(oldDir, newDir, membersAdded, membersRemoved, membersChanged, relAdded, relRemoved);
        Files.createDirectories(outHtml.getParent());
        Files.writeString(outHtml, html, StandardCharsets.UTF_8);

        System.out.println("Report generated: " + outHtml.toAbsolutePath());
    }

    private static JSONArray readJsonArray(Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return new JSONArray(content);
    }

    private static Map<Integer, JSONObject> indexMembers(JSONArray members) {
        Map<Integer, JSONObject> map = new HashMap<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject m = members.getJSONObject(i);
            map.put(m.getInt("id"), m);
        }
        return map;
    }

    private static Set<String> indexRelationships(JSONArray rels) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < rels.length(); i++) {
            JSONObject r = rels.getJSONObject(i);
            int member1 = r.getInt("member1");
            int member2 = r.getInt("member2");
            int relation = r.getInt("relation");
            set.add(member1 + "->" + member2 + ":" + relation);
        }
        return set;
    }

    private static boolean sameMember(JSONObject a, JSONObject b) {
        return a.getString("name").equals(b.getString("name"))
                && a.getInt("generation") == b.getInt("generation")
                && a.getInt("gender") == b.getInt("gender");
    }

    private static String buildHtmlReport(Path oldDir, Path newDir,
                                          List<JSONObject> membersAdded,
                                          List<JSONObject> membersRemoved,
                                          List<MemberChange> membersChanged,
                                          List<String> relAdded,
                                          List<String> relRemoved) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        sb.append("<title>家谱数据差异报告</title>");
        sb.append("<style>");
        sb.append("body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#111}");
        sb.append("h1{margin:0 0 12px 0}h2{margin:28px 0 8px 0}");
        sb.append(".summary{display:flex;gap:12px;flex-wrap:wrap;margin:8px 0 16px 0}");
        sb.append(".card{border:1px solid #ddd;border-radius:8px;padding:10px 14px;background:#fafafa}");
        sb.append("table{border-collapse:collapse;width:100%;margin-top:8px}");
        sb.append("th,td{border:1px solid #ddd;padding:6px 8px;text-align:left;font-size:13px}");
        sb.append("th{background:#f2f2f2}");
        sb.append(".added{background:#e6ffed}");
        sb.append(".removed{background:#ffeef0}");
        sb.append(".changed{background:#fff5b1}");
        sb.append(".mono{font-family:Consolas,monospace;font-size:12px}");
        sb.append("</style></head><body>");
        sb.append("<h1>家谱数据差异报告</h1>");
        sb.append("<div>旧版本: ").append(escape(oldDir.toString())).append("</div>");
        sb.append("<div>新版本: ").append(escape(newDir.toString())).append("</div>");

        sb.append("<div class=\"summary\">");
        sb.append(card("成员新增", membersAdded.size()));
        sb.append(card("成员删除", membersRemoved.size()));
        sb.append(card("成员修改", membersChanged.size()));
        sb.append(card("关系新增", relAdded.size()));
        sb.append(card("关系删除", relRemoved.size()));
        sb.append("</div>");

        sb.append("<h2>成员新增</h2>");
        sb.append(renderMembersTable(membersAdded, "added"));

        sb.append("<h2>成员删除</h2>");
        sb.append(renderMembersTable(membersRemoved, "removed"));

        sb.append("<h2>成员修改</h2>");
        sb.append(renderMemberChangesTable(membersChanged));

        sb.append("<h2>关系新增（基础关系）</h2>");
        sb.append(renderRelationsTable(relAdded, "added"));

        sb.append("<h2>关系删除（基础关系）</h2>");
        sb.append(renderRelationsTable(relRemoved, "removed"));

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String card(String title, int count) {
        return "<div class=\"card\"><div>" + escape(title) + "</div><div><strong>" + count + "</strong></div></div>";
    }

    private static String renderMembersTable(List<JSONObject> list, String rowClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        sb.append("<th>ID</th><th>姓名</th><th>代际</th><th>性别</th><th>备注</th>");
        sb.append("</tr></thead><tbody>");
        for (JSONObject m : list) {
            sb.append("<tr class=\"").append(rowClass).append("\">");
            sb.append("<td>").append(m.getInt("id")).append("</td>");
            sb.append("<td>").append(escape(m.getString("name"))).append("</td>");
            sb.append("<td>").append(m.getInt("generation")).append("</td>");
            sb.append("<td>").append(m.getInt("gender")).append("</td>");
            sb.append("<td>").append(escape(m.optString("remark", ""))).append("</td>");
            sb.append("</tr>");
        }
        if (list.isEmpty()) {
            sb.append("<tr><td colspan=\"5\">无</td></tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String renderMemberChangesTable(List<MemberChange> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        sb.append("<th>ID</th><th>字段</th><th>旧值</th><th>新值</th>");
        sb.append("</tr></thead><tbody>");
        for (MemberChange c : list) {
            int id = c.newMember.getInt("id");
            if (!c.oldMember.getString("name").equals(c.newMember.getString("name"))) {
                sb.append(changeRow(id, "name", c.oldMember.getString("name"), c.newMember.getString("name")));
            }
            if (c.oldMember.getInt("generation") != c.newMember.getInt("generation")) {
                sb.append(changeRow(id, "generation",
                        String.valueOf(c.oldMember.getInt("generation")),
                        String.valueOf(c.newMember.getInt("generation"))));
            }
            if (c.oldMember.getInt("gender") != c.newMember.getInt("gender")) {
                sb.append(changeRow(id, "gender",
                        String.valueOf(c.oldMember.getInt("gender")),
                        String.valueOf(c.newMember.getInt("gender"))));
            }
            String oldRemark = c.oldMember.optString("remark", "");
            String newRemark = c.newMember.optString("remark", "");
            if (!oldRemark.equals(newRemark)) {
                sb.append(changeRow(id, "remark", oldRemark, newRemark));
            }
        }
        if (list.isEmpty()) {
            sb.append("<tr><td colspan=\"4\">无</td></tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String changeRow(int id, String field, String oldValue, String newValue) {
        return "<tr class=\"changed\"><td>" + id + "</td><td>" + escape(field) + "</td><td>" +
                escape(oldValue) + "</td><td>" + escape(newValue) + "</td></tr>";
    }

    private static String renderRelationsTable(List<String> list, String rowClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        sb.append("<th>关系 (member1->member2:relation)</th>");
        sb.append("</tr></thead><tbody>");
        for (String r : list) {
            sb.append("<tr class=\"").append(rowClass).append("\"><td class=\"mono\">")
                    .append(escape(r)).append("</td></tr>");
        }
        if (list.isEmpty()) {
            sb.append("<tr><td>无</td></tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class MemberChange {
        private final JSONObject oldMember;
        private final JSONObject newMember;

        private MemberChange(JSONObject oldMember, JSONObject newMember) {
            this.oldMember = oldMember;
            this.newMember = newMember;
        }
    }
}
