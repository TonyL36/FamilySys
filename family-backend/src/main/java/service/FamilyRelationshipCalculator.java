package service;

import model.Member;
import model.Relationship;
import repository.MemberRepository;
import repository.RelationshipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.*;

/**
 * 工具类：用于计算家族成员之间的远亲关系
 * 只要存在任意一种联系（直接关系或通过任意代/姻亲的路径）均能查询并返回
 */
public class FamilyRelationshipCalculator {
    private static final Logger logger = LogManager.getLogger(FamilyRelationshipCalculator.class);
    private static final int MAX_ANCESTOR_GENERATIONS = 10;

    private final MemberRepository memberRepository;
    private final RelationshipRepository relationshipRepository;

    public FamilyRelationshipCalculator(MemberRepository memberRepository, RelationshipRepository relationshipRepository) {
        this.memberRepository = memberRepository;
        this.relationshipRepository = relationshipRepository;
    }

    /**
     * 查找两个成员之间的关系：只要存在任意一种联系即返回有联系，并返回路径上的节点与边（用于图示）
     */
    public DistantRelativeResult findDistantRelative(int member1ID, int member2ID) {
        try {
            Member member1 = memberRepository.findMemberById(member1ID);
            Member member2 = memberRepository.findMemberById(member2ID);

            if (member1 == null || member2 == null) {
                return new DistantRelativeResult(false, "成员不存在", -1, 0, null, null);
            }

            // 1. 先检查是否存在直接关系（一条边相连）
            Relationship directRel = findDirectRelationship(member1ID, member2ID);
            if (directRel != null) {
                String desc = "直接关系：" + directRel.getRelationshipDescription();
                List<PathNode> nodes = Arrays.asList(
                        new PathNode(member1ID, member1.getName()),
                        new PathNode(member2ID, member2.getName())
                );
                List<PathEdge> edges = Arrays.asList(
                        new PathEdge(member1ID, member2ID, directRel.getRelationshipDescription())
                );
                return new DistantRelativeResult(true, desc, -1, 1, nodes, edges);
            }

            // 2. 获取双方祖先（扩大为多代），找共同祖先并计算关系类型
            Set<Integer> ancestors1 = getAncestorsUpToGenerations(member1ID, MAX_ANCESTOR_GENERATIONS);
            Set<Integer> ancestors2 = getAncestorsUpToGenerations(member2ID, MAX_ANCESTOR_GENERATIONS);
            Set<Integer> commonAncestors = new HashSet<>(ancestors1);
            commonAncestors.retainAll(ancestors2);

            if (!commonAncestors.isEmpty()) {
                int closestCommonAncestor = findClosestCommonAncestor(member1ID, member2ID, commonAncestors);
                String relationshipType = calculateDistantRelationshipType(member1, member2, closestCommonAncestor);
                PathResult pathResult = findShortestPath(member1ID, member2ID);
                return new DistantRelativeResult(true, relationshipType, closestCommonAncestor, commonAncestors.size(),
                        pathResult != null ? pathResult.nodes : null, pathResult != null ? pathResult.edges : null);
            }

            // 3. 无共同祖先时，检查图中是否存在任意路径（姻亲、远房等）
            PathResult pathResult = findShortestPath(member1ID, member2ID);
            if (pathResult != null) {
                return new DistantRelativeResult(true, "存在亲属关系（通过若干代或姻亲相连）", -1, 0, pathResult.nodes, pathResult.edges);
            }

            return new DistantRelativeResult(false, "无亲属关系", -1, 0, null, null);
        } catch (SQLException e) {
            logger.error("查找远亲关系时出错: {}", e.getMessage());
            return new DistantRelativeResult(false, "查询失败: " + e.getMessage(), -1, 0, null, null);
        }
    }

    /** BFS 求最短路径，返回路径上的节点与边（从 member1 到 member2） */
    private PathResult findShortestPath(int member1ID, int member2ID) throws SQLException {
        if (member1ID == member2ID) {
            Member m = memberRepository.findMemberById(member1ID);
            return new PathResult(Collections.singletonList(new PathNode(member1ID, m != null ? m.getName() : "")), Collections.emptyList());
        }
        // (currentId -> {prevId, relationship})
        Map<Integer, int[]> prevNode = new HashMap<>();
        Map<Integer, Relationship> prevRel = new HashMap<>();
        Queue<Integer> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();
        queue.offer(member1ID);
        visited.add(member1ID);
        prevNode.put(member1ID, new int[]{-1});
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            List<Relationship> list = relationshipRepository.getRelationshipsInvolvingMember(cur);
            for (Relationship rel : list) {
                int next = rel.getMember1() == cur ? rel.getMember2() : rel.getMember1();
                String desc = rel.getRelationshipDescription();
                if (next == member2ID) {
                    prevNode.put(next, new int[]{cur});
                    prevRel.put(next, rel);
                    return buildPathFromBacktrack(member1ID, member2ID, prevNode, prevRel);
                }
                if (!visited.contains(next)) {
                    visited.add(next);
                    prevNode.put(next, new int[]{cur});
                    prevRel.put(next, rel);
                    queue.offer(next);
                }
            }
        }
        return null;
    }

    private PathResult buildPathFromBacktrack(int fromID, int toID, Map<Integer, int[]> prevNode, Map<Integer, Relationship> prevRel) throws SQLException {
        List<Integer> idOrder = new ArrayList<>();
        int cur = toID;
        while (cur != -1 && cur != fromID) {
            idOrder.add(cur);
            int[] p = prevNode.get(cur);
            cur = p != null && p.length > 0 ? p[0] : -1;
        }
        idOrder.add(fromID);
        Collections.reverse(idOrder);
        List<PathNode> nodes = new ArrayList<>();
        List<PathEdge> edges = new ArrayList<>();
        for (int id : idOrder) {
            Member m = memberRepository.findMemberById(id);
            nodes.add(new PathNode(id, m != null ? m.getName() : ""));
        }
        for (int i = 0; i < idOrder.size() - 1; i++) {
            int a = idOrder.get(i), b = idOrder.get(i + 1);
            Relationship r = prevRel.get(b);
            edges.add(new PathEdge(a, b, r != null ? r.getRelationshipDescription() : ""));
        }
        return new PathResult(nodes, edges);
    }

    private static class PathResult {
        final List<PathNode> nodes;
        final List<PathEdge> edges;
        PathResult(List<PathNode> nodes, List<PathEdge> edges) { this.nodes = nodes; this.edges = edges; }
    }

    /** 若两人存在直接关系（一条边），返回该关系，否则返回 null */
    private Relationship findDirectRelationship(int member1ID, int member2ID) throws SQLException {
        List<Relationship> list = relationshipRepository.getRelationshipsInvolvingMember(member1ID);
        for (Relationship rel : list) {
            int other = rel.getMember1() == member1ID ? rel.getMember2() : rel.getMember1();
            if (other == member2ID) return rel;
        }
        return null;
    }

    /** 获取指定成员向上 maxGenerations 代内的所有祖先（仅通过父母关系 3/4 追溯） */
    private Set<Integer> getAncestorsUpToGenerations(int memberID, int maxGenerations) throws SQLException {
        Set<Integer> ancestors = new HashSet<>();
        List<Relationship> parentRels = relationshipRepository.getRelationshipsByRelationType(3);
        parentRels.addAll(relationshipRepository.getRelationshipsByRelationType(4));
        Set<Integer> currentLevel = new HashSet<>();
        currentLevel.add(memberID);
        for (int gen = 0; gen < maxGenerations && !currentLevel.isEmpty(); gen++) {
            Set<Integer> nextLevel = new HashSet<>();
            for (Relationship rel : parentRels) {
                if (currentLevel.contains(rel.getMember1())) {
                    nextLevel.add(rel.getMember2());
                }
            }
            ancestors.addAll(nextLevel);
            currentLevel = nextLevel;
        }
        return ancestors;
    }

    /**
     * 查找最近的共同祖先（代际距离最小）
     */
    private int findClosestCommonAncestor(int member1ID, int member2ID, Set<Integer> commonAncestors) throws SQLException {
        int minDistance = Integer.MAX_VALUE;
        int closestAncestor = -1;
        
        for (int ancestorID : commonAncestors) {
            int distance1 = getGenerationDistance(member1ID, ancestorID);
            int distance2 = getGenerationDistance(member2ID, ancestorID);
            
            if (distance1 > 0 && distance2 > 0) {
                int totalDistance = distance1 + distance2;
                if (totalDistance < minDistance) {
                    minDistance = totalDistance;
                    closestAncestor = ancestorID;
                }
            }
        }
        
        return closestAncestor;
    }

    /** 在关系图上计算两成员间最短路径步数（沿任意关系边双向） */
    private int getGenerationDistance(int fromMemberID, int toMemberID) throws SQLException {
        if (fromMemberID == toMemberID) return 0;
        Queue<int[]> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();
        queue.offer(new int[]{fromMemberID, 0});
        visited.add(fromMemberID);
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int curID = cur[0], dist = cur[1];
            List<Relationship> list = relationshipRepository.getRelationshipsInvolvingMember(curID);
            for (Relationship rel : list) {
                int nextID = rel.getMember1() == curID ? rel.getMember2() : rel.getMember1();
                if (nextID == toMemberID) return dist + 1;
                if (!visited.contains(nextID)) {
                    visited.add(nextID);
                    queue.offer(new int[]{nextID, dist + 1});
                }
            }
        }
        return -1;
    }

    /**
     * 根据共同祖先计算远亲关系类型
     */
    private String calculateDistantRelationshipType(Member member1, Member member2, int commonAncestorID) throws SQLException {
        // 获取共同祖先
        Member ancestor = memberRepository.findMemberById(commonAncestorID);
        if (ancestor == null) {
            return "未知关系";
        }
        
        // 获取两个成员相对于共同祖先的代际距离
        int genDistance1 = Math.abs(member1.getGeneration() - ancestor.getGeneration());
        int genDistance2 = Math.abs(member2.getGeneration() - ancestor.getGeneration());
        
        // 如果是同一代，可能是堂/表兄弟姐妹
        if (genDistance1 == 1 && genDistance2 == 1) {
            // 都是共同祖先的子女 -> 兄弟姐妹
            if (member1.getGeneration() == member2.getGeneration()) {
                return "兄弟姐妹";
            }
        }
        
        // 如果都是共同祖先的孙辈（第二代）
        if (genDistance1 == 2 && genDistance2 == 2) {
            // 堂/表兄弟姐妹
            return "堂/表兄弟姐妹";
        }
        
        // 如果一个是子女，一个是孙辈
        if ((genDistance1 == 1 && genDistance2 == 2) || (genDistance1 == 2 && genDistance2 == 1)) {
            return "叔伯/姑姨与侄子/侄女";
        }
        
        // 更远的关系
        if (genDistance1 >= 2 && genDistance2 >= 2) {
            int maxGen = Math.max(genDistance1, genDistance2);
            if (maxGen == 2) {
                return "堂/表兄弟姐妹";
            } else if (maxGen == 3) {
                return "远房堂/表兄弟姐妹";
            } else {
                return "远亲";
            }
        }
        
        return "远亲";
    }

    /** 路径上的节点（人） */
    public static class PathNode {
        private final int id;
        private final String name;
        public PathNode(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
    }

    /** 路径上的边（关系） */
    public static class PathEdge {
        private final int fromId;
        private final int toId;
        private final String description;
        public PathEdge(int fromId, int toId, String description) { this.fromId = fromId; this.toId = toId; this.description = description; }
        public int getFromId() { return fromId; }
        public int getToId() { return toId; }
        public String getDescription() { return description; }
    }

    /**
     * 远亲关系结果类（含路径节点与边，用于前端图示）
     */
    public static class DistantRelativeResult {
        private final boolean isDistantRelative;
        private final String description;
        private final int closestCommonAncestorID;
        private final int commonAncestorCount;
        private final List<PathNode> pathNodes;
        private final List<PathEdge> pathEdges;

        public DistantRelativeResult(boolean isDistantRelative, String description) {
            this(isDistantRelative, description, -1, 0, null, null);
        }

        public DistantRelativeResult(boolean isDistantRelative, String description, int closestCommonAncestorID, int commonAncestorCount) {
            this(isDistantRelative, description, closestCommonAncestorID, commonAncestorCount, null, null);
        }

        public DistantRelativeResult(boolean isDistantRelative, String description, int closestCommonAncestorID, int commonAncestorCount,
                                     List<PathNode> pathNodes, List<PathEdge> pathEdges) {
            this.isDistantRelative = isDistantRelative;
            this.description = description;
            this.closestCommonAncestorID = closestCommonAncestorID;
            this.commonAncestorCount = commonAncestorCount;
            this.pathNodes = pathNodes != null ? pathNodes : Collections.emptyList();
            this.pathEdges = pathEdges != null ? pathEdges : Collections.emptyList();
        }

        public boolean isDistantRelative() { return isDistantRelative; }
        public String getDescription() { return description; }
        public int getClosestCommonAncestorID() { return closestCommonAncestorID; }
        public int getCommonAncestorCount() { return commonAncestorCount; }
        public List<PathNode> getPathNodes() { return pathNodes; }
        public List<PathEdge> getPathEdges() { return pathEdges; }
    }
}