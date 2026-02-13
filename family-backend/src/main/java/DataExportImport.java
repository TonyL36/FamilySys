import org.json.JSONArray;
import org.json.JSONObject;
import repository.MemberRepository;
import repository.RelationshipRepository;
import service.RelationshipService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataExportImport {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  export [dbPath] [outDir]");
            System.out.println("  rebuild [dbPath] [membersFile] [relationshipsFile]");
            System.exit(1);
        }
        String command = args[0].toLowerCase();
        Path baseDir = Paths.get("").toAbsolutePath();
        Path defaultDb = baseDir.resolve("src/main/resources/family.db");
        Path defaultOutDir = baseDir.resolve("../exports").normalize();

        if ("export".equals(command)) {
            Path dbPath = args.length > 1 ? Paths.get(args[1]) : defaultDb;
            Path outDir = args.length > 2 ? Paths.get(args[2]) : defaultOutDir;
            exportData(dbPath, outDir);
            return;
        }

        if ("rebuild".equals(command)) {
            Path dbPath = args.length > 1 ? Paths.get(args[1]) : defaultDb;
            Path membersFile = args.length > 2 ? Paths.get(args[2]) : defaultOutDir.resolve("members.json");
            Path relationshipsFile = args.length > 3 ? Paths.get(args[3]) : defaultOutDir.resolve("base_relationships.json");
            rebuildData(dbPath, membersFile, relationshipsFile);
            return;
        }

        System.out.println("Unknown command: " + command);
        System.exit(1);
    }

    private static void exportData(Path dbPath, Path outDir) throws Exception {
        Files.createDirectories(outDir);
        JSONArray members = new JSONArray();
        JSONArray relationships = new JSONArray();

        try (Connection conn = openConnection(dbPath);
             PreparedStatement stmt = conn.prepareStatement("SELECT MemberID, Name, Generation, Gender FROM Members ORDER BY MemberID");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                JSONObject m = new JSONObject();
                m.put("id", rs.getInt("MemberID"));
                m.put("name", rs.getString("Name"));
                m.put("generation", rs.getInt("Generation"));
                m.put("gender", rs.getInt("Gender"));
                members.put(m);
            }
        }

        String relSql = "SELECT member1, member2, relation FROM Relationships WHERE relation IN (1,2,5,6,7,8,9,10) ORDER BY relation, member1, member2";
        Set<String> seen = new HashSet<>();
        try (Connection conn = openConnection(dbPath);
             PreparedStatement stmt = conn.prepareStatement(relSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int member1 = rs.getInt("member1");
                int member2 = rs.getInt("member2");
                int relation = rs.getInt("relation");
                String key = member1 + ":" + member2 + ":" + relation;
                if (seen.add(key)) {
                    JSONObject r = new JSONObject();
                    r.put("member1", member1);
                    r.put("member2", member2);
                    r.put("relation", relation);
                    relationships.put(r);
                }
            }
        }

        Path membersFile = outDir.resolve("members.json");
        Path relationshipsFile = outDir.resolve("base_relationships.json");
        Files.writeString(membersFile, members.toString(2), StandardCharsets.UTF_8);
        Files.writeString(relationshipsFile, relationships.toString(2), StandardCharsets.UTF_8);

        System.out.println("Exported members: " + members.length());
        System.out.println("Exported relationships: " + relationships.length());
        System.out.println("Members file: " + membersFile.toAbsolutePath());
        System.out.println("Relationships file: " + relationshipsFile.toAbsolutePath());
    }

    private static void rebuildData(Path dbPath, Path membersFile, Path relationshipsFile) throws Exception {
        String membersText = Files.readString(membersFile, StandardCharsets.UTF_8);
        String relationshipsText = Files.readString(relationshipsFile, StandardCharsets.UTF_8);
        JSONArray members = new JSONArray(membersText);
        JSONArray relationships = new JSONArray(relationshipsText);

        resetDatabase(dbPath);
        insertMembers(dbPath, members);

        MemberRepository memberRepository = new MemberRepository();
        RelationshipRepository relationshipRepository = new RelationshipRepository(memberRepository);
        RelationshipService relationshipService = new RelationshipService(relationshipRepository, memberRepository);

        List<JSONObject> spouseRelations = new ArrayList<>();
        List<JSONObject> parentChildRelations = new ArrayList<>();

        for (int i = 0; i < relationships.length(); i++) {
            JSONObject r = relationships.getJSONObject(i);
            int relation = r.getInt("relation");
            if (relation == 1 || relation == 2) {
                spouseRelations.add(r);
            } else {
                parentChildRelations.add(r);
            }
        }

        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (JSONObject r : spouseRelations) {
            if (addRelationship(relationshipService, r, errors)) {
                success++;
            } else {
                failed++;
            }
        }

        for (JSONObject r : parentChildRelations) {
            if (addRelationship(relationshipService, r, errors)) {
                success++;
            } else {
                failed++;
            }
        }

        relationshipService.removeDuplicateRelationships();

        System.out.println("Rebuild complete");
        System.out.println("Relationships added: " + success);
        System.out.println("Relationships failed: " + failed);
        if (!errors.isEmpty()) {
            System.out.println("Failures:");
            for (String e : errors) {
                System.out.println(e);
            }
        }
    }

    private static boolean addRelationship(RelationshipService relationshipService, JSONObject r, List<String> errors) {
        int member1 = r.getInt("member1");
        int member2 = r.getInt("member2");
        int relation = r.getInt("relation");
        boolean result = relationshipService.addRelationship(member1, member2, relation);
        if (!result) {
            errors.add(member1 + "->" + member2 + ":" + relation);
        }
        return result;
    }

    private static void resetDatabase(Path dbPath) throws Exception {
        try (Connection conn = openConnection(dbPath);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS Members");
            stmt.execute("DROP TABLE IF EXISTS Relationships");
            stmt.execute("CREATE TABLE IF NOT EXISTS Members (MemberID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, Generation INTEGER NOT NULL, Gender INTEGER NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS Relationships (RelationID INTEGER PRIMARY KEY AUTOINCREMENT, member1 INTEGER NOT NULL, member2 INTEGER NOT NULL, relation INTEGER NOT NULL, FOREIGN KEY(member1) REFERENCES Members(MemberID), FOREIGN KEY(member2) REFERENCES Members(MemberID))");
        }
    }

    private static void insertMembers(Path dbPath, JSONArray members) throws Exception {
        try (Connection conn = openConnection(dbPath);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO Members(MemberID, Name, Generation, Gender) VALUES(?, ?, ?, ?)")) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject m = members.getJSONObject(i);
                stmt.setInt(1, m.getInt("id"));
                stmt.setString(2, m.getString("name"));
                stmt.setInt(3, m.getInt("generation"));
                stmt.setInt(4, m.getInt("gender"));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        int maxId = 0;
        for (int i = 0; i < members.length(); i++) {
            int id = members.getJSONObject(i).getInt("id");
            if (id > maxId) {
                maxId = id;
            }
        }

        try (Connection conn = openConnection(dbPath);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM sqlite_sequence WHERE name='Members'");
            stmt.execute("INSERT INTO sqlite_sequence(name, seq) VALUES('Members', " + maxId + ")");
        } catch (Exception ignored) {
        }
    }

    private static Connection openConnection(Path dbPath) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }
}
