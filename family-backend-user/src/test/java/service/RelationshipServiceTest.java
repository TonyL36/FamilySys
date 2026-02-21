package service;

import model.Member;
import model.Relationship;
import org.junit.jupiter.api.Test;
import repository.MemberRepository;
import repository.RelationshipRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelationshipServiceTest {

    // Stub repositories
    static class MemberRepositoryStub extends MemberRepository {
        public MemberRepositoryStub() {
            super(); 
        }

        @Override
        public Member findMemberById(int id) throws SQLException {
            if (id == 1) return new Member(1, "Luo Yinrong", 1, 0); // Male, Gen 1
            if (id == 2) return new Member(2, "Luo Chengyao", 2, 1); // Female, Gen 2
            if (id == 3) return new Member(3, "Li Xinshe", 2, 0); // Male, Gen 2
            return null;
        }
    }

    static class RelationshipRepositoryStub extends RelationshipRepository {
        public List<String> addedRelationships = new ArrayList<>();

        public RelationshipRepositoryStub(MemberRepository memberRepo) {
            super(memberRepo);
        }

        @Override
        public int getMember2ByMember1AndRelation(int member1, int relation) throws SQLException {
            // Luo Chengyao (2) has Father (3) -> Luo Yinrong (1)
            // Note: In code logic, Relation 3 is Father.
            if (member1 == 2 && relation == 3) return 1;
            return -1;
        }

        @Override
        public boolean addRelationship(int member1, int member2, int relation) throws SQLException {
            addedRelationships.add(member1 + "->" + member2 + ":" + relation);
            return true;
        }

        @Override
        public List<Relationship> getRelationshipsByRelationType(int relationType) throws SQLException {
            return new ArrayList<>();
        }
        
        @Override
        public void removeDuplicateRelationships() throws SQLException {
            // Do nothing
        }
    }

    @Test
    public void testAddSonInLawRelationship() {
        MemberRepositoryStub memberRepo = new MemberRepositoryStub();
        RelationshipRepositoryStub relationRepo = new RelationshipRepositoryStub(memberRepo);
        RelationshipService service = new RelationshipService(relationRepo, memberRepo);

        // Add relationship: Luo Chengyao (2) has Husband Li Xinshe (3)
        // Type 1: Husband (Subject has Husband Object)
        // member1 = 2 (Wife), member2 = 3 (Husband)
        boolean result = service.addRelationship(2, 3, 1);

        assertTrue(result);

        // Verify captured relationships
        // 1. (3, 2, 2) -> Husband has Wife (2)
        // 2. (3, 1, 27) -> Husband (3) has Father-in-law (1) (Type 27)
        // 3. (1, 3, 32) -> Father-in-law (1) has Son-in-law (3) (Type 32)
        
        // Check for the bug fix: (1, 3, 32) should be present, NOT (1, 2, 32)
        boolean foundSonInLaw = false;
        boolean foundBug = false;
        
        for (String rel : relationRepo.addedRelationships) {
            System.out.println("Added: " + rel);
            if (rel.equals("1->3:32")) {
                foundSonInLaw = true;
            }
            if (rel.equals("1->2:32")) {
                foundBug = true;
            }
        }

        assertTrue(foundSonInLaw, "Should have added Father-in-law -> Son-in-law relationship (1->3:32)");
        assertEquals(false, foundBug, "Should NOT have added Father-in-law -> Daughter relationship (1->2:32)");
    }
}
