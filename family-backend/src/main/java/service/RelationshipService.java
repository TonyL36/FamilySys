package service;

import model.Member;
import model.Relationship;
import repository.MemberRepository;
import repository.RelationshipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RelationshipService {
    private static final Logger logger = LogManager.getLogger(RelationshipService.class);
    private RelationshipRepository relationshipRepository;
    private MemberRepository memberRepository;
    private FamilyRelationshipCalculator familyRelationshipCalculator;

    public RelationshipService(RelationshipRepository relationshipRepository, MemberRepository memberRepository) {
        this.relationshipRepository = relationshipRepository;
        this.memberRepository = memberRepository;
        this.familyRelationshipCalculator = new FamilyRelationshipCalculator(memberRepository, relationshipRepository);
    }

    public boolean addRelationship(int member1ID, int member2ID, int relationType) {
        try {
            Member member1 = memberRepository.findMemberById(member1ID);
            Member member2 = memberRepository.findMemberById(member2ID);

            if (member1 == null || member2 == null) {
                logger.warn("One or both members not found. Member1ID: {}, Member2ID: {}", member1ID, member2ID);
                return false;
            }

            if (!validateRelationship(member1, member2, relationType)) {
                logger.warn("Invalid relationship. Member1ID: {}, Member2ID: {}, RelationType: {}", member1ID, member2ID, relationType);
                return false;
            }

            boolean success = relationshipRepository.addRelationship(member1ID, member2ID, relationType);
            if (success) {
                logger.info("Relationship added successfully. Member1ID: {}, Member2ID: {}, RelationType: {}", member1ID, member2ID, relationType);
                addRecursiveRelationships(member1, member2, relationType);
                removeDuplicateRelationships();
            } else {
                logger.error("Failed to add relationship. Member1ID: {}, Member2ID: {}, RelationType: {}", member1ID, member2ID, relationType);
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error adding relationship: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateRelationship(Member member1, Member member2, int relationType) {
        switch (relationType) {
            case 1: // 丈夫
                return member1.getGender() == 1 && member2.getGender() == 0 && member1.getGeneration() == member2.getGeneration();
            case 2: // 妻子
                return member1.getGender() == 0 && member2.getGender() == 1 && member1.getGeneration() == member2.getGeneration();
            case 5: // 长子
            case 6: // 次子
            case 7: // 小子
                return member2.getGender() == 0 && member2.getGeneration() == member1.getGeneration() + 1;
            case 8: // 长女
            case 9: // 次女
            case 10: // 小女
                return member2.getGender() == 1 && member2.getGeneration() == member1.getGeneration() + 1;
            case 15: // 表哥
            case 17: // 表弟
                return member2.getGender() == 0 && member1.getGeneration() == member2.getGeneration();
            case 16: // 表姐
            case 18: // 表妹
                return member2.getGender() == 1 && member1.getGeneration() == member2.getGeneration();
            default:
                return false;
        }
    }

    private void addRecursiveRelationships(Member member1, Member member2, int relationType) {
        try {
            int memberID1 = member1.getMemberID();
            int memberID2 = member2.getMemberID();
            int memberID0 = -1;
            Member member0;
            switch (relationType) {
                case 1: // 丈夫
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 2);
                    addParentInLawRelationships(memberID1, memberID2);
                    break;
                case 2: // 妻子
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 1);
                    addParentInLawRelationships(memberID1, memberID2);
                    break;
                case 5: // 长子
                case 6: // 次子
                case 7: // 小子
                case 8: // 长女
                case 9: // 次女
                case 10: // 小女
                    if (member1.getGender() == 0) {
                        memberID0 = relationshipRepository.getMember2ByMember1AndRelation(memberID1, 2);
                        member0 = memberRepository.findMemberById(memberID0);
                    } else {
                        memberID0 = relationshipRepository.getMember2ByMember1AndRelation(memberID1, 1);
                        member0 = memberRepository.findMemberById(memberID0);
                    }
                    addParentRelationships(member1, member2, relationType);
                    addSiblingRelationships(member1, member2);
                    addGrandparentRelationships(member1, member2);
                    if (member0 != null) {
                        addGrandparentRelationships(member0, member2);
                    }
                    break;
                case 15: // 表哥
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 17);
                    break;
                case 16: // 表姐
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 18);
                    break;
                case 17: // 表弟
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 15);
                    break;
                case 18: // 表妹
                    relationshipRepository.addRelationship(member2.getMemberID(), member1.getMemberID(), 16);
                    break;
            }
        } catch (SQLException e) {
            logger.error("Error adding recursive relationships: {}", e.getMessage());
        }
    }

    private void addSiblingRelationships(Member parent, Member newChild) throws SQLException {
        List<Relationship> parentChildRelationships = new ArrayList<>();
        for (int i = 5; i <= 10; i++) {
            parentChildRelationships.addAll(relationshipRepository.getRelationshipsByRelationType(i));
        }

        for (Relationship relationship : parentChildRelationships) {
            if (relationship.getMember1() == parent.getMemberID()) {
                Member sibling = memberRepository.findMemberById(relationship.getMember2());
                if (sibling.getMemberID() != newChild.getMemberID()) {
                    if (sibling.getMemberID() < newChild.getMemberID()) {
                        relationshipRepository.addRelationship(newChild.getMemberID(), sibling.getMemberID(),
                                sibling.getGender() == 0 ? 11 : 12);
                        relationshipRepository.addRelationship(sibling.getMemberID(), newChild.getMemberID(),
                                newChild.getGender() == 0 ? 13 : 14);
                    } else {
                        relationshipRepository.addRelationship(newChild.getMemberID(), sibling.getMemberID(),
                                sibling.getGender() == 0 ? 13 : 14);
                        relationshipRepository.addRelationship(sibling.getMemberID(), newChild.getMemberID(),
                                newChild.getGender() == 0 ? 11 : 12);
                    }
                }
            }
        }
    }

    public void removeDuplicateRelationships() {
        try {
            relationshipRepository.removeDuplicateRelationships();
        } catch (SQLException e) {
            logger.error("Error removing duplicate relationships: {}", e.getMessage());
        }
    }

    private void addGrandparentRelationships(Member parent, Member child) {
        try {
            boolean isPaternal = parent.getGender() == 0;
            List<Relationship> grandparentRelationships = relationshipRepository.getRelationshipsForMember(parent.getMemberID());
            for (Relationship relationship : grandparentRelationships) {
                if (relationship.getRelation() == 3 || relationship.getRelation() == 4) {
                    Member grandparent = memberRepository.findMemberById(relationship.getMember2());
                    if (isPaternal) {
                        relationshipRepository.addRelationship(child.getMemberID(), grandparent.getMemberID(),
                                grandparent.getGender() == 0 ? 19 : 20);
                        relationshipRepository.addRelationship(grandparent.getMemberID(), child.getMemberID(),
                                child.getGender() == 0 ? 23 : 24);
                    } else {
                        relationshipRepository.addRelationship(child.getMemberID(), grandparent.getMemberID(),
                                grandparent.getGender() == 0 ? 22 : 21);
                        relationshipRepository.addRelationship(grandparent.getMemberID(), child.getMemberID(),
                                child.getGender() == 0 ? 25 : 26);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding grandparent relationships: {}", e.getMessage());
        }
    }

    private void addParentInLawRelationships(int member1, int member2) {
        try {
            if (memberRepository.findMemberById(member1).getGender() == 1) {
                int temp = member1;
                member1 = member2;
                member2 = temp;
            }
            int father1 = relationshipRepository.getMember2ByMember1AndRelation(member1, 3);
            if (father1 > 0) {
                relationshipRepository.addRelationship(member2, father1, 29);
                relationshipRepository.addRelationship(father1, member2, 31);
            }
            int mother1 = relationshipRepository.getMember2ByMember1AndRelation(member1, 4);
            if (mother1 > 0) {
                relationshipRepository.addRelationship(member2, mother1, 30);
                relationshipRepository.addRelationship(mother1, member2, 31);
            }
            int father2 = relationshipRepository.getMember2ByMember1AndRelation(member2, 3);
            if (father2 > 0) {
                relationshipRepository.addRelationship(member1, father2, 27);
                relationshipRepository.addRelationship(father2, member2, 32);
            }
            int mother2 = relationshipRepository.getMember2ByMember1AndRelation(member2, 4);
            if (mother2 > 0) {
                relationshipRepository.addRelationship(member1, mother2, 28);
                relationshipRepository.addRelationship(mother2, member2, 32);
            }
        } catch (SQLException e) {
            logger.error("Error adding parent-in-law relationships: {}", e.getMessage());
        }
    }

    public List<Relationship> getRelationshipsForMember(int memberID) {
        try {
            return relationshipRepository.getRelationshipsForMember(memberID);
        } catch (SQLException e) {
            logger.error("Error getting relationships for member: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Relationship> getAllRelationships() {
        try {
            return relationshipRepository.getAllRelationships();
        } catch (SQLException e) {
            logger.error("Error getting all relationships: {}", e.getMessage());
            return List.of();
        }
    }

    private void addParentRelationships(Member parent, Member child, int relationType) {
        try {
            int parentID = parent.getMemberID();
            int childID = child.getMemberID();
            int parentGender = parent.getGender();

            if (parentGender == 0) {
                relationshipRepository.addRelationship(childID, parentID, 3);
                int motherID = relationshipRepository.getMember2ByMember1AndRelation(parentID, 2);
                if (motherID != -1) {
                    relationshipRepository.addRelationship(childID, motherID, 4);
                    relationshipRepository.addRelationship(motherID, childID, relationType);
                }
            } else {
                relationshipRepository.addRelationship(childID, parentID, 4);
                int fatherID = relationshipRepository.getMember2ByMember1AndRelation(parentID, 1);
                if (fatherID != -1) {
                    relationshipRepository.addRelationship(childID, fatherID, 3);
                    relationshipRepository.addRelationship(fatherID, childID, relationType);
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding parent relationships: {}", e.getMessage());
        }
    }

    public List<Relationship> getRelationshipsByRelationType(int relationType) {
        try {
            return relationshipRepository.getRelationshipsByRelationType(relationType);
        } catch (SQLException e) {
            logger.error("Error getting relationships by relation type: {}", e.getMessage());
            return List.of();
        }
    }

    public Relationship getRelationshipByRelationID(int relationID) {
        try {
            return relationshipRepository.getRelationshipByRelationID(relationID);
        } catch (SQLException e) {
            logger.error("Error getting relationship by RelationID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查找两个成员之间的远亲关系
     */
    public FamilyRelationshipCalculator.DistantRelativeResult findDistantRelative(int member1ID, int member2ID) {
        return familyRelationshipCalculator.findDistantRelative(member1ID, member2ID);
    }
}

