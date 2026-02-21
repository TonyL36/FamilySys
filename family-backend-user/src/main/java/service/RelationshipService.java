package service;

import model.Member;
import model.Relationship;
import repository.MemberRepository;
import repository.RelationshipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private void addInLawRelationshipsForChild(Member child, Member parent) throws SQLException {
        int childID = child.getMemberID();
        int spouseID = relationshipRepository.getMember2ByMember1AndRelation(childID, child.getGender() == 0 ? 2 : 1);
        if (spouseID <= 0) {
            return;
        }
        Member spouse = memberRepository.findMemberById(spouseID);
        if (spouse == null) {
            return;
        }
        int parentID = parent.getMemberID();
        int parentGender = parent.getGender();
        if (spouse.getGender() == 0) {
            relationshipRepository.addRelationship(spouseID, parentID, parentGender == 0 ? 27 : 28);
            relationshipRepository.addRelationship(parentID, spouseID, 32);
        } else if (spouse.getGender() == 1) {
            relationshipRepository.addRelationship(spouseID, parentID, parentGender == 0 ? 29 : 30);
            relationshipRepository.addRelationship(parentID, spouseID, 31);
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
                relationshipRepository.addRelationship(father2, member1, 32);
            }
            int mother2 = relationshipRepository.getMember2ByMember1AndRelation(member2, 4);
            if (mother2 > 0) {
                relationshipRepository.addRelationship(member1, mother2, 28);
                relationshipRepository.addRelationship(mother2, member1, 32);
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
                    Member mother = memberRepository.findMemberById(motherID);
                    if (mother != null) {
                        addInLawRelationshipsForChild(child, mother);
                    }
                }
                addInLawRelationshipsForChild(child, parent);
            } else {
                relationshipRepository.addRelationship(childID, parentID, 4);
                int fatherID = relationshipRepository.getMember2ByMember1AndRelation(parentID, 1);
                if (fatherID != -1) {
                    relationshipRepository.addRelationship(childID, fatherID, 3);
                    relationshipRepository.addRelationship(fatherID, childID, relationType);
                    Member father = memberRepository.findMemberById(fatherID);
                    if (father != null) {
                        addInLawRelationshipsForChild(child, father);
                    }
                }
                addInLawRelationshipsForChild(child, parent);
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

    public KinshipNetworkResult getKinshipNetwork(int centerId, int generations) {
        try {
            Member center = memberRepository.findMemberById(centerId);
            if (center == null) {
                return null;
            }
            List<Relationship> relationships = relationshipRepository.getAllRelationships();
            Set<Integer> memberIds = new HashSet<>();
            memberIds.add(centerId);
            for (Relationship rel : relationships) {
                memberIds.add(rel.getMember1());
                memberIds.add(rel.getMember2());
            }
            Map<Integer, Member> memberMap = new HashMap<>();
            for (Integer id : memberIds) {
                Member member = memberRepository.findMemberById(id);
                if (member != null) {
                    memberMap.put(id, member);
                }
            }

            Map<Integer, List<Relationship>> adjacency = new HashMap<>();
            for (Relationship rel : relationships) {
                if (isBloodRelation(rel.getRelation())) {
                    adjacency.computeIfAbsent(rel.getMember1(), k -> new ArrayList<>()).add(rel);
                    adjacency.computeIfAbsent(rel.getMember2(), k -> new ArrayList<>()).add(rel);
                }
            }

            Map<Integer, Integer> levelMap = new HashMap<>();
            Set<Integer> bloodSelected = new HashSet<>();
            Set<Integer> spouseSelected = new HashSet<>();
            Set<Integer> frontier = new HashSet<>();
            levelMap.put(centerId, 0);
            bloodSelected.add(centerId);
            frontier.add(centerId);

            for (int step = 1; step <= generations; step++) {
                Set<Integer> nextFrontier = new HashSet<>();
                for (Integer current : frontier) {
                    List<Relationship> neighbors = adjacency.get(current);
                    if (neighbors == null) {
                        continue;
                    }
                    Member currentMember = memberMap.get(current);
                    if (currentMember == null) {
                        continue;
                    }
                    for (Relationship rel : neighbors) {
                        int next = rel.getMember1() == current ? rel.getMember2() : rel.getMember1();
                        if (bloodSelected.contains(next)) {
                            continue;
                        }
                        Member nextMember = memberMap.get(next);
                        if (nextMember == null) {
                            continue;
                        }
                        int generationDiff = Math.abs(nextMember.getGeneration() - currentMember.getGeneration());
                        if (generationDiff > 1) {
                            continue;
                        }
                        bloodSelected.add(next);
                        levelMap.put(next, step);
                        nextFrontier.add(next);
                    }
                }

                for (Relationship rel : relationships) {
                    if (!isMarriageRelation(rel.getRelation())) {
                        continue;
                    }
                    int m1 = rel.getMember1();
                    int m2 = rel.getMember2();
                    if (bloodSelected.contains(m1) && !spouseSelected.contains(m2)) {
                        spouseSelected.add(m2);
                        levelMap.putIfAbsent(m2, levelMap.getOrDefault(m1, step));
                    }
                    if (bloodSelected.contains(m2) && !spouseSelected.contains(m1)) {
                        spouseSelected.add(m1);
                        levelMap.putIfAbsent(m1, levelMap.getOrDefault(m2, step));
                    }
                }

                if (nextFrontier.isEmpty()) {
                    break;
                }
                frontier = nextFrontier;
            }

            int centerGeneration = center.getGeneration();
            Set<Integer> nodeIds = new HashSet<>(bloodSelected);
            nodeIds.addAll(spouseSelected);

            Set<Integer> filteredNodeIds = new HashSet<>();
            for (Integer id : nodeIds) {
                Member member = memberMap.get(id);
                if (member == null) {
                    continue;
                }
                int steps = levelMap.getOrDefault(id, generations);
                if (steps <= generations || id == centerId) {
                    filteredNodeIds.add(id);
                    memberMap.put(id, member);
                }
            }

            List<KinshipNetworkNode> nodes = new ArrayList<>();
            for (Integer id : filteredNodeIds) {
                Member member = memberMap.get(id);
                if (member != null) {
                    nodes.add(new KinshipNetworkNode(
                            member.getMemberID(),
                            member.getName(),
                            member.getGender(),
                            member.getGeneration(),
                            levelMap.getOrDefault(id, generations)
                    ));
                }
            }
            nodes.sort(Comparator.comparingInt(KinshipNetworkNode::getId));

            Map<String, List<Relationship>> edgeGroups = new HashMap<>();
            for (Relationship rel : relationships) {
                if (filteredNodeIds.contains(rel.getMember1()) && filteredNodeIds.contains(rel.getMember2()) && isDisplayRelation(rel.getRelation())) {
                    int a = Math.min(rel.getMember1(), rel.getMember2());
                    int b = Math.max(rel.getMember1(), rel.getMember2());
                    String key = a + "-" + b;
                    edgeGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(rel);
                }
            }

            List<KinshipNetworkEdge> edges = new ArrayList<>();
            for (List<Relationship> rels : edgeGroups.values()) {
                if (rels.isEmpty()) {
                    continue;
                }
                Relationship first = rels.get(0);
                int a = Math.min(first.getMember1(), first.getMember2());
                int b = Math.max(first.getMember1(), first.getMember2());
                String label = buildMergedRelationLabel(rels, memberMap);
                String edgeType = isMarriageRelation(first.getRelation()) ? "marriage" : "blood";
                edges.add(new KinshipNetworkEdge(a, b, first.getRelation(), label, edgeType));
            }

            int hiddenRelationsCount = 0;
            for (Relationship rel : relationships) {
                if (filteredNodeIds.contains(rel.getMember1()) && filteredNodeIds.contains(rel.getMember2()) && !isDisplayRelation(rel.getRelation())) {
                    hiddenRelationsCount++;
                }
            }

            return new KinshipNetworkResult(centerId, generations, centerGeneration, nodes, edges, hiddenRelationsCount);
        } catch (SQLException e) {
            logger.error("Error building kinship network: {}", e.getMessage());
            return null;
        }
    }

    private boolean isBloodRelation(int relationType) {
        return relationType >= 3 && relationType <= 26;
    }

    private boolean isMarriageRelation(int relationType) {
        return relationType == 1 || relationType == 2;
    }

    private boolean isDisplayRelation(int relationType) {
        return relationType >= 1 && relationType <= 32;
    }

    private String buildMergedRelationLabel(List<Relationship> rels, Map<Integer, Member> memberMap) {
        Set<Integer> types = new HashSet<>();
        Map<String, Integer> dirTypes = new HashMap<>();
        for (Relationship rel : rels) {
            types.add(rel.getRelation());
            dirTypes.put(rel.getMember1() + "-" + rel.getMember2(), rel.getRelation());
        }

        if (types.contains(1) && types.contains(2)) {
            return "夫妻";
        }

        for (Relationship rel : rels) {
            if (rel.getRelation() == 3 || rel.getRelation() == 4) {
                int parentId = rel.getMember2();
                int childId = rel.getMember1();
                Integer reverseType = dirTypes.get(parentId + "-" + childId);
                if (reverseType != null && reverseType >= 5 && reverseType <= 10) {
                    Member child = memberMap.get(childId);
                    boolean childMale = child != null ? child.getGender() == 0 : reverseType <= 7;
                    if (rel.getRelation() == 3) {
                        return childMale ? "父子" : "父女";
                    }
                    return childMale ? "母子" : "母女";
                }
            }
        }

        boolean hasSibling = false;
        for (int type : types) {
            if (type >= 11 && type <= 14) {
                hasSibling = true;
                break;
            }
        }
        if (hasSibling) {
            Integer[] ids = extractPairIds(rels);
            Member m1 = ids != null ? memberMap.get(ids[0]) : null;
            Member m2 = ids != null ? memberMap.get(ids[1]) : null;
            if (m1 != null && m2 != null) {
                if (m1.getGender() == 0 && m2.getGender() == 0) {
                    return "兄弟";
                }
                if (m1.getGender() == 1 && m2.getGender() == 1) {
                    return "姐妹";
                }
            }
            if (types.contains(12) && types.contains(13)) {
                return "姐弟";
            }
            if (types.contains(11) && types.contains(14)) {
                return "兄妹";
            }
            return "兄妹";
        }

        if (types.contains(27) && types.contains(32)) {
            return "岳父/女婿";
        }
        if (types.contains(28) && types.contains(32)) {
            return "岳母/女婿";
        }
        if (types.contains(29) && types.contains(31)) {
            return "公公/儿媳";
        }
        if (types.contains(30) && types.contains(31)) {
            return "婆婆/儿媳";
        }

        return rels.get(0).getRelationshipDescription();
    }

    private Integer[] extractPairIds(List<Relationship> rels) {
        if (rels == null || rels.isEmpty()) {
            return null;
        }
        Relationship first = rels.get(0);
        int a = Math.min(first.getMember1(), first.getMember2());
        int b = Math.max(first.getMember1(), first.getMember2());
        return new Integer[]{a, b};
    }

    public static class KinshipNetworkNode {
        private final int id;
        private final String name;
        private final int gender;
        private final int generation;
        private final int level;

        public KinshipNetworkNode(int id, String name, int gender, int generation, int level) {
            this.id = id;
            this.name = name;
            this.gender = gender;
            this.generation = generation;
            this.level = level;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getGender() { return gender; }
        public int getGeneration() { return generation; }
        public int getLevel() { return level; }
    }

    public static class KinshipNetworkEdge {
        private final int fromId;
        private final int toId;
        private final int relationType;
        private final String description;
        private final String edgeType;

        public KinshipNetworkEdge(int fromId, int toId, int relationType, String description, String edgeType) {
            this.fromId = fromId;
            this.toId = toId;
            this.relationType = relationType;
            this.description = description;
            this.edgeType = edgeType;
        }

        public int getFromId() { return fromId; }
        public int getToId() { return toId; }
        public int getRelationType() { return relationType; }
        public String getDescription() { return description; }
        public String getEdgeType() { return edgeType; }
    }

    public static class KinshipNetworkResult {
        private final int centerId;
        private final int generations;
        private final int centerGeneration;
        private final List<KinshipNetworkNode> nodes;
        private final List<KinshipNetworkEdge> edges;
        private final int hiddenRelationsCount;

        public KinshipNetworkResult(int centerId, int generations, int centerGeneration, List<KinshipNetworkNode> nodes, List<KinshipNetworkEdge> edges, int hiddenRelationsCount) {
            this.centerId = centerId;
            this.generations = generations;
            this.centerGeneration = centerGeneration;
            this.nodes = nodes;
            this.edges = edges;
            this.hiddenRelationsCount = hiddenRelationsCount;
        }

        public int getCenterId() { return centerId; }
        public int getGenerations() { return generations; }
        public int getCenterGeneration() { return centerGeneration; }
        public List<KinshipNetworkNode> getNodes() { return nodes; }
        public List<KinshipNetworkEdge> getEdges() { return edges; }
        public int getHiddenRelationsCount() { return hiddenRelationsCount; }
    }
}
