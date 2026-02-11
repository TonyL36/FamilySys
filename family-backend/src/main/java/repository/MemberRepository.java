// MemberRepository.java
package repository;

import model.Member;
import controller.DatabaseConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MemberRepository {
    private static final Logger logger = LogManager.getLogger(MemberRepository.class);

    public Member addMember(String name, int generation, int gender) throws SQLException {
        String sql = "INSERT INTO Members(Name, Generation, Gender) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, name);
            pstmt.setInt(2, generation);
            pstmt.setInt(3, gender);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating member failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return new Member(id, name, generation, gender);
                } else {
                    throw new SQLException("Creating member failed, no ID obtained.");
                }
            }
        }
    }

    public Member findMemberById(int memberId) throws SQLException {
        String sql = "SELECT * FROM Members WHERE MemberID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, memberId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Member(
                            rs.getInt("MemberID"),
                            rs.getString("Name"),
                            rs.getInt("Generation"),
                            rs.getInt("Gender")
                    );
                }
            }
        }
        return null;
    }

    public Member findMemberByName(String name) throws SQLException {
        String sql = "SELECT * FROM Members WHERE Name LIKE ? COLLATE NOCASE";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + name + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Member(
                            rs.getInt("MemberID"),
                            rs.getString("Name"),
                            rs.getInt("Generation"),
                            rs.getInt("Gender")
                    );
                }
            }
        }
        return null;
    }

    public List<Member> getAllMembers() throws SQLException {
        List<Member> members = new ArrayList<>();
        String sql = "SELECT * FROM Members";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                members.add(new Member(
                        rs.getInt("MemberID"),
                        rs.getString("Name"),
                        rs.getInt("Generation"),
                        rs.getInt("Gender")
                ));
            }
        }
        return members;
    }

    public boolean deleteMember(int memberId) throws SQLException {
        String sql = "DELETE FROM Members WHERE MemberID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, memberId);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }
}