// DatabaseConnection.java
package controller;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:sqlite:family.db";

    private static String getCorrectDatabaseFile() {
        File dbFile = new File(DB_URL.replace("jdbc:sqlite:", ""));
        if (!dbFile.exists()) {
            // 如果指定的文件不存在，尝试在当前目录及其父目录中查找
            File currentDir = new File(".");
            for (int i = 0; i < 3; i++) { // 向上查找3层目录
                File[] files = currentDir.listFiles((d, name) -> name.contains("family") && name.endsWith(".db"));
                if (files != null && files.length > 0) {
                    return "jdbc:sqlite:" + files[0].getAbsolutePath();
                }
                currentDir = currentDir.getParentFile();
                if (currentDir == null) break;
            }
        }
        return DB_URL;
    }

    public static Connection getConnection() throws SQLException {
        String url = getCorrectDatabaseFile();
        //System.out.println("Attempting to connect to: " + url);
        Connection conn = DriverManager.getConnection(url);
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