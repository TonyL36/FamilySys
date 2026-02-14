// DatabaseConnection.java
package controller;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:sqlite:family.db";

    private static String getCorrectDatabaseFile() {
        // 1. 优先检查当前工作目录下的 family.db (用于服务器持久化)
        File cwdDb = new File("family.db");
        if (cwdDb.exists()) {
            return "jdbc:sqlite:" + cwdDb.getAbsolutePath();
        }

        // 2. 开发环境路径检查
        File resourceDb = new File("src/main/resources/family.db");
        if (resourceDb.exists()) {
            return "jdbc:sqlite:" + resourceDb.getAbsolutePath();
        }
        File backendResourceDb = new File("family-backend/src/main/resources/family.db");
        if (backendResourceDb.exists()) {
            return "jdbc:sqlite:" + backendResourceDb.getAbsolutePath();
        }

        // 3. JAR包资源回退 (提取到当前目录以确保持久化)
        URL resource = DatabaseConnection.class.getClassLoader().getResource("family.db");
        if (resource != null) {
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                try {
                    return "jdbc:sqlite:" + new File(resource.toURI()).getAbsolutePath();
                } catch (Exception ignored) {
                }
            } else {
                try (InputStream in = resource.openStream()) {
                    // 关键修改：不再使用临时文件，而是提取到工作目录的 family.db
                    Path target = new File("family.db").toPath();
                    if (!Files.exists(target)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return "jdbc:sqlite:" + target.toAbsolutePath();
                } catch (Exception ignored) {
                }
            }
        }
        return DB_URL;
    }

public static Connection getConnection() throws SQLException {
        String url = getCorrectDatabaseFile();
        System.out.println("Using database: " + url); // Log the DB path
        Connection conn = DriverManager.getConnection(url);
        // 设置UTF-8编码
        conn.createStatement().execute("PRAGMA encoding = 'UTF-8'");
        initializeDatabase(conn);
        return conn;
    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        // 创建Members表
        String createMembersTable = "CREATE TABLE IF NOT EXISTS Members (" +
                "MemberID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Name TEXT NOT NULL," +
                "Generation INTEGER NOT NULL," +
                "Gender INTEGER NOT NULL)";

        // 创建Relationships表
        String createRelationshipsTable = "CREATE TABLE IF NOT EXISTS Relationships (" +
                "RelationID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "member1 INTEGER NOT NULL," +
                "member2 INTEGER NOT NULL," +
                "relation INTEGER NOT NULL," +
                "FOREIGN KEY(member1) REFERENCES Members(MemberID)," +
                "FOREIGN KEY(member2) REFERENCES Members(MemberID))";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createMembersTable);
            stmt.execute(createRelationshipsTable);
        }
    }
}
