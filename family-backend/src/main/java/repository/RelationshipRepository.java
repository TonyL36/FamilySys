package repository;

import model.Member;
import model.Relationship;
import controller.DatabaseConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RelationshipRepository {
    private static final Logger logger = LogManager.getLogger(RelationshipRepository.class);
    private final MemberRepository memberRepository;

    public RelationshipRepository(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public boolean addRelationship(int member1, int member2, int relation) throws SQLException {
        // 验证输入参数不为空
        if (member1 <= 0 || member2 <= 0 || relation <= 0) {
            throw new SQLException("Invalid input parameters: all fields must not be null");
        }

        String sql = "INSERT INTO Relationships(member1, member2, relation) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, member1);
            pstmt.setInt(2, member2);
            pstmt.setInt(3, relation);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public List<Relationship> getRelationshipsForMember(int member1ID) throws SQLException {
        List<Relationship> relationships = new ArrayList<>();
        String sql = "SELECT * FROM Relationships WHERE member1 = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, member1ID);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Relationship relationship = new Relationship(
                            rs.getInt("RelationID"),
                            rs.getInt("member1"),
                            rs.getInt("member2"),
                            rs.getInt("relation")
                    );
                    // 查询成员名字
                    Member member1 = memberRepository.findMemberById(relationship.getMember1());
                    Member member2 = memberRepository.findMemberById(relationship.getMember2());
                    if (member1 != null) relationship.setMember1Name(member1.getName());
                    if (member2 != null) relationship.setMember2Name(member2.getName());

                    relationships.add(relationship);
                }
            }
        }
        return relationships;
    }

    /** 获取某成员参与的所有关系（作为 member1 或 member2），用于全图连通性判断 */
    public List<Relationship> getRelationshipsInvolvingMember(int memberID) throws SQLException {
        List<Relationship> relationships = new ArrayList<>();
        String sql = "SELECT * FROM Relationships WHERE member1 = ? OR member2 = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, memberID);
            pstmt.setInt(2, memberID);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Relationship relationship = new Relationship(
                            rs.getInt("RelationID"),
                            rs.getInt("member1"),
                            rs.getInt("member2"),
                            rs.getInt("relation")
                    );
                    Member m1 = memberRepository.findMemberById(relationship.getMember1());
                    Member m2 = memberRepository.findMemberById(relationship.getMember2());
                    if (m1 != null) relationship.setMember1Name(m1.getName());
                    if (m2 != null) relationship.setMember2Name(m2.getName());
                    relationships.add(relationship);
                }
            }
        }
        return relationships;
    }

    public List<Relationship> getAllRelationships() throws SQLException {
        List<Relationship> relationships = new ArrayList<>();
        String sql = "SELECT * FROM Relationships";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Relationship relationship = new Relationship(
                        rs.getInt("RelationID"),
                        rs.getInt("member1"),
                        rs.getInt("member2"),
                        rs.getInt("relation")
                );
                // 查询成员名字
                Member member1 = memberRepository.findMemberById(relationship.getMember1());
                Member member2 = memberRepository.findMemberById(relationship.getMember2());
                if (member1 != null) relationship.setMember1Name(member1.getName());
                if (member2 != null) relationship.setMember2Name(member2.getName());

                relationships.add(relationship);
            }
        }
        return relationships;
    }

    public Relationship getRelationshipByRelationID(int relationID) throws SQLException {
        String sql = "SELECT * FROM Relationships WHERE RelationID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, relationID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Relationship relationship = new Relationship(
                            rs.getInt("RelationID"),
                            rs.getInt("member1"),
                            rs.getInt("member2"),
                            rs.getInt("relation")
                    );
                    // 查询成员名字
                    Member member1 = memberRepository.findMemberById(relationship.getMember1());
                    Member member2 = memberRepository.findMemberById(relationship.getMember2());
                    if (member1 != null) relationship.setMember1Name(member1.getName());
                    if (member2 != null) relationship.setMember2Name(member2.getName());

                    return relationship;
                }
            }
        }
        return null;
    }

    public List<Relationship> getRelationshipsByRelationType(int relationType) throws SQLException {
        List<Relationship> relationships = new ArrayList<>();
        String sql = "SELECT * FROM Relationships WHERE relation = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, relationType);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Relationship relationship = new Relationship(
                            rs.getInt("RelationID"),
                            rs.getInt("member1"),
                            rs.getInt("member2"),
                            rs.getInt("relation")
                    );
                    // 查询成员名字
                    Member member1 = memberRepository.findMemberById(relationship.getMember1());
                    Member member2 = memberRepository.findMemberById(relationship.getMember2());
                    if (member1 != null) relationship.setMember1Name(member1.getName());
                    if (member2 != null) relationship.setMember2Name(member2.getName());

                    relationships.add(relationship);
                }
            }
        }
        return relationships;
    }

    public int getMember2ByMember1AndRelation(int member1ID, int relationType) throws SQLException {
        String sql = "SELECT member2 FROM Relationships WHERE member1 = ? AND relation = ?";
        int member2ID = -1;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, member1ID);
            pstmt.setInt(2, relationType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    member2ID = rs.getInt("member2");
                }
            }
        }
        return member2ID;
    }

    public void removeDuplicateRelationships() throws SQLException {
        String sql = "DELETE FROM Relationships " +
                "WHERE RelationID NOT IN (" +
                "    SELECT MIN(RelationID) " +
                "    FROM Relationships " +
                "    GROUP BY member1, member2, relation" +
                ");";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            int rowsDeleted = stmt.executeUpdate(sql);
            logger.info("Deleted {} duplicate relationships.", rowsDeleted);
        }
    }
}

