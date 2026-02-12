package service;

import model.Member;
import model.Relationship;
import repository.MemberRepository;
import repository.RelationshipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.*;

/** 工具类：用于计算家族成员之间的远亲关系 */
public class FamilyRelationshipCalculator {
    private static final Logger logger = LogManager.getLogger(FamilyRelationshipCalculator.class);
    private static final int MAX_ANCESTOR_GENERATIONS = 10;

    private final MemberRepository memberRepository;
    private final RelationshipRepository relationshipRepository;

    public FamilyRelationshipCalculator(MemberRepository memberRepository, RelationshipRepository relationshipRepository) {
        this.memberRepository = memberRepository;
        this.relationshipRepository = relationshipRepository;
    }

/** 查找两个成员之间的关系 */
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
                Map<Integer, Member> memberMap = new HashMap<>();
                if (member1 != null) memberMap.put(member1ID, member1);
                if (member2 != null) memberMap.put(member2ID, member2);
                String edgeDesc = edgeDescriptionForDirection(directRel, member1ID, member2ID, memberMap);
                String desc = "直接关系：" + edgeDesc;
                List<PathNode> nodes = Arrays.asList(
                        new PathNode(member1ID, member1.getName()),
                        new PathNode(member2ID, member2.getName())
                );
                List<PathEdge> edges = Arrays.asList(new PathEdge(member1ID, member2ID, edgeDesc));
                String preciseTerm = edgeDesc; // 直接关系即精确称谓
                return new DistantRelativeResult(true, desc, -1, 1, nodes, edges, preciseTerm);
            }

            // 2. 获取双方祖先（扩大为多代），找共同祖先并计算关系类型
            Set<Integer> ancestors1 = getAncestorsUpToGenerations(member1ID, MAX_ANCESTOR_GENERATIONS);
            Set<Integer> ancestors2 = getAncestorsUpToGenerations(member2ID, MAX_ANCESTOR_GENERATIONS);
            Set<Integer> commonAncestors = new HashSet<>(ancestors1);
            commonAncestors.retainAll(ancestors2);

            if (!commonAncestors.isEmpty()) {
                int closestCommonAncestor = findClosestCommonAncestor(member1ID, member2ID, commonAncestors);
                String relationshipType = calculateDistantRelationshipType(member1, member2, closestCommonAncestor);
                
                // 获取路径并确保使用正确的成员信息
                PathResult pathResult = findShortestPath(member1ID, member2ID);
                List<PathEdge> edges = pathResult != null ? pathResult.edges : null;
                
                // 使用完整的成员信息计算精确称谓
                String preciseTerm = computePreciseKinshipTerm(edges, member1, member2);
                if (preciseTerm != null && !preciseTerm.isEmpty()) {
                    relationshipType = preciseTerm;
                }
                
                return new DistantRelativeResult(true, relationshipType, closestCommonAncestor, commonAncestors.size(),
                        pathResult != null ? pathResult.nodes : null, edges, preciseTerm);
            }

            // 3. 无共同祖先时，检查图中是否存在任意路径（姻亲、远房等）
            PathResult pathResult = findShortestPath(member1ID, member2ID);
            if (pathResult != null) {
                String preciseTerm = computePreciseKinshipTerm(pathResult.edges, member1, member2);
                String desc = (preciseTerm != null && !preciseTerm.isEmpty()) ? preciseTerm : "存在亲属关系（通过若干代或姻亲相连）";
                return new DistantRelativeResult(true, desc, -1, 0, pathResult.nodes, pathResult.edges, preciseTerm);
            }

            return new DistantRelativeResult(false, "无亲属关系", -1, 0, null, null, null);
        } catch (SQLException e) {
            logger.error("查找远亲关系时出错: {}", e.getMessage());
            return new DistantRelativeResult(false, "查询失败: " + e.getMessage(), -1, 0, null, null, null);
        }
    }

    /** 库存关系 (member1, member2, type) 表示 member1 是 member2 的 [type]。边 from→to 的展示为「to 是 from 的 ???」 */
    private String edgeDescriptionForDirection(Relationship rel, int fromId, int toId, Map<Integer, Member> memberMap) {
        if (rel.getMember1() == fromId && rel.getMember2() == toId)
            return rel.getRelationshipDescription();
        if (rel.getMember1() == toId && rel.getMember2() == fromId)
            return getReverseRelationshipDescription(rel.getRelation(), fromId, toId, memberMap);
        
        return getCorrectRelationshipDescription(rel.getRelation(), fromId, toId, null);
    }

    

    /** 关系类型的反向称谓（member2 对 member1 的称呼） */
    private static String reverseRelationshipDescription(int relationType) {
        switch (relationType) {
            case 1: return "妻子";   case 2: return "丈夫";
            case 3: return "子女";   case 4: return "子女";
            case 5: case 6: case 7: return "父母"; case 8: case 9: case 10: return "父母";
            case 11: case 12: return "弟弟/妹妹";
            case 13: case 14: return "哥哥/姐姐";
            case 15: case 16: return "表弟/表妹";
            case 17: case 18: return "表哥/表姐";
            case 19: case 20: return "孙辈"; case 21: case 22: return "外孙辈";
            case 23: case 24: return "爷爷/奶奶"; case 25: case 26: return "外祖父/外祖母";
            case 27: case 28: return "女婿/儿媳";
            case 29: case 30: return "女婿/儿媳";
            case 31: case 32: return "公公/婆婆";
            default: return "亲属";
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
        
        // 获取成员信息用于关系判断
        Map<Integer, Member> memberMap = new HashMap<>();
        Member startMember = memberRepository.findMemberById(member1ID);
        if (startMember != null) {
            memberMap.put(member1ID, startMember);
        }
        
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            List<Relationship> list = relationshipRepository.getRelationshipsInvolvingMember(cur);
            for (Relationship rel : list) {
                int next = rel.getMember1() == cur ? rel.getMember2() : rel.getMember1();
                
                // 获取成员信息
                if (!memberMap.containsKey(next)) {
                    Member nextMember = memberRepository.findMemberById(next);
                    if (nextMember != null) {
                        memberMap.put(next, nextMember);
                    }
                }
                
                if (next == member2ID) {
                    prevNode.put(next, new int[]{cur});
                    prevRel.put(next, rel);
                    return buildPathFromBacktrack(member1ID, member2ID, prevNode, prevRel, memberMap);
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

    private PathResult buildPathFromBacktrack(int fromID, int toID, Map<Integer, int[]> prevNode, Map<Integer, Relationship> prevRel, Map<Integer, Member> memberMap) throws SQLException {
        List<Integer> idOrder = new ArrayList<>();
        int cur = toID;
        while (cur != -1 && cur != fromID) {
            idOrder.add(cur);
            int[] p = prevNode.get(cur);
            cur = p != null && p.length > 0 ? p[0] : -1;
        }
        idOrder.add(fromID);
        Collections.reverse(idOrder);
        
        // 确保成员信息完整
        for (int id : idOrder) {
            if (!memberMap.containsKey(id)) {
                Member m = memberRepository.findMemberById(id);
                if (m != null) {
                    memberMap.put(id, m);
                }
            }
        }
        
        List<PathNode> nodes = new ArrayList<>();
        List<PathEdge> edges = new ArrayList<>();
        for (int id : idOrder) {
            Member m = memberMap.get(id);
            nodes.add(new PathNode(id, m != null ? m.getName() : ""));
        }
        
        // 构建边，确保使用正确的关系方向和称呼
        for (int i = 0; i < idOrder.size() - 1; i++) {
            int a = idOrder.get(i), b = idOrder.get(i + 1);
            Relationship r = prevRel.get(b);
            if (r != null) {
                // 确保关系方向正确：a 是前序节点，b 是后序节点
                String edgeDesc = getEdgeDescriptionForMember1(r, a, b, memberMap);
                edges.add(new PathEdge(a, b, edgeDesc));
            } else {
                edges.add(new PathEdge(a, b, ""));
            }
        }
        return new PathResult(nodes, edges);
    }

/** 获取成员1的关系描述，确保使用正确的关系称呼 */
    private String getEdgeDescriptionForMember1(Relationship rel, int member1Id, int member2Id, Map<Integer, Member> memberMap) {
        // 检查关系方向
        if (rel.getMember1() == member1Id && rel.getMember2() == member2Id) {
            return rel.getRelationshipDescription();
        } else if (rel.getMember1() == member2Id && rel.getMember2() == member1Id) {
            // 如果关系方向相反，需要返回反向的关系描述
            return getReverseRelationshipDescription(rel.getRelation(), member1Id, member2Id, memberMap);
        }
        
        // 如果关系方向不匹配，使用正确的关系类型
        return getCorrectRelationshipDescription(rel.getRelation(), member1Id, member2Id, memberMap);
    }

    /** 根据关系类型、成员ID和成员信息获取正确的关系称呼 */
    private String getCorrectRelationshipDescription(int relationType, int member1Id, int member2Id, Map<Integer, Member> memberMap) {
        Relationship rel = new Relationship(0, member1Id, member2Id, relationType);
        String desc = rel.getRelationshipDescription();
        return "未知关系".equals(desc) ? "亲属" : desc;
    }

    /** 根据路径上的边（方向已修正为 起点→终点）计算精确称谓；同辈时做对称归一（避免 A→B 表兄弟、B→A 表侄） */
    private String computePreciseKinshipTerm(List<PathEdge> pathEdges, Member member1, Member member2) {
        if (pathEdges == null || pathEdges.isEmpty()) return null;
        List<String> steps = new ArrayList<>();
        for (PathEdge e : pathEdges) steps.add(e.getDescription());
        
        // 获取成员信息用于关系判断
        Map<Integer, Member> memberMap = new HashMap<>();
        for (PathEdge edge : pathEdges) {
            try {
                Member m1 = memberRepository.findMemberById(edge.getFromId());
                Member m2 = memberRepository.findMemberById(edge.getToId());
                if (m1 != null) memberMap.put(edge.getFromId(), m1);
                if (m2 != null) memberMap.put(edge.getToId(), m2);
            } catch (SQLException e) {
                logger.error("Error fetching member information: " + e.getMessage());
                // 如果无法获取成员信息，继续处理其他边
            }
        }
        
        String term = preciseKinshipFromSteps(steps, memberMap);
        term = refineLinealTerm(term, steps, member2);
        boolean sameGenerationByMember = member1 != null && member2 != null && member1.getGeneration() == member2.getGeneration();
        boolean sameGenerationByPath = isSameGenerationFromSteps(steps);
        if (term != null && (sameGenerationByMember || sameGenerationByPath)) {
            term = normalizeSameGenerationTerm(term);
        }
        return term;
    }

    /** 从路径步骤判断两人是否同辈（上代步数 == 下代步数） */
    private static boolean isSameGenerationFromSteps(List<String> steps) {
        int up = 0, down = 0;
        for (String s : steps) {
            if (s == null) continue;
            switch (s) {
                case "父亲": case "母亲": case "外祖父": case "外祖母": up++; break;
                case "爷爷": case "奶奶": up += 2; break;
                case "子女": case "长子": case "次子": case "小子": case "长女": case "次女": case "小女": down++; break;
                case "孙辈": case "孙子": case "孙女": down += 2; break;
                case "外孙辈": case "外孙": case "外孙女": down += 2; break;
                default: break;
            }
        }
        return up == down && (up + down) >= 2;
    }

    private static String refineLinealTerm(String term, List<String> steps, Member target) {
        if (term == null || target == null) return term;
        int gender = target.getGender();
        boolean maternalLine = false;
        for (String s : steps) {
            if (s == null) continue;
            if ("母亲".equals(s) || "外祖父".equals(s) || "外祖母".equals(s)) {
                maternalLine = true;
                break;
            }
            if ("长女".equals(s) || "次女".equals(s) || "小女".equals(s)) {
                maternalLine = true;
                break;
            }
        }
        if ("孙辈".equals(term)) return gender == 0 ? "孙子" : "孙女";
        if ("外孙辈".equals(term)) return gender == 0 ? "外孙" : "外孙女";
        if ("祖辈".equals(term)) {
            if (maternalLine) return gender == 0 ? "外祖父" : "外祖母";
            return gender == 0 ? "爷爷" : "奶奶";
        }
        return term;
    }

    /** 获取反向关系描述 */
    private static String getReverseRelationshipDescription(int relationType, int fromId, int toId, Map<Integer, Member> memberMap) {
        int fromGender = -1;
        if (memberMap != null) {
            Member from = memberMap.get(fromId);
            if (from != null) fromGender = from.getGender();
        }
        String parentTerm = fromGender == 0 ? "父亲" : fromGender == 1 ? "母亲" : "父母";
        String childTerm = fromGender == 0 ? "孙子" : fromGender == 1 ? "孙女" : "孙辈";
        String maternalGrandchild = fromGender == 0 ? "外孙" : fromGender == 1 ? "外孙女" : "外孙辈";
        String paternalGrandparent = fromGender == 0 ? "爷爷" : fromGender == 1 ? "奶奶" : "祖父母";
        String maternalGrandparent = fromGender == 0 ? "外祖父" : fromGender == 1 ? "外祖母" : "外祖父母";
        String youngerSibling = fromGender == 0 ? "弟弟" : fromGender == 1 ? "妹妹" : "弟弟/妹妹";
        String olderSibling = fromGender == 0 ? "哥哥" : fromGender == 1 ? "姐姐" : "哥哥/姐姐";
        String youngerCousin = fromGender == 0 ? "表弟" : fromGender == 1 ? "表妹" : "表弟/表妹";
        String olderCousin = fromGender == 0 ? "表哥" : fromGender == 1 ? "表姐" : "表哥/表姐";
        String inLawParent = fromGender == 0 ? "公公" : fromGender == 1 ? "婆婆" : "公公/婆婆";
        String childInLaw = fromGender == 0 ? "女婿" : fromGender == 1 ? "儿媳" : "女婿/儿媳";
        switch (relationType) {
            case 1: return "妻子";
            case 2: return "丈夫";
            case 3: case 4: return "子女";
            case 5: case 6: case 7: return parentTerm;
            case 8: case 9: case 10: return parentTerm;
            case 11: case 12: return youngerSibling;
            case 13: case 14: return olderSibling;
            case 15: case 16: return youngerCousin;
            case 17: case 18: return olderCousin;
            case 19: case 20: return childTerm;
            case 21: case 22: return maternalGrandchild;
            case 23: case 24: return paternalGrandparent;
            case 25: case 26: return maternalGrandparent;
            case 27: case 28: return childInLaw;
            case 29: case 30: return childInLaw;
            case 31: case 32: return inLawParent;
            default: return "亲属";
        }
    }

    /** 同辈时称谓应对称：表侄/堂侄/表伯/堂伯等统一为表兄弟/堂兄弟（或表姐妹/堂姐妹） */
    private static String normalizeSameGenerationTerm(String term) {
        if (term == null) return null;
        switch (term) {
            case "表侄": case "表侄女": case "表伯": case "表叔": case "表姑": return "表兄弟/表姐妹";
            case "堂侄": case "堂侄女": case "堂伯": case "堂叔": case "堂姑": return "堂兄弟/堂姐妹";
            case "再从侄": case "再从侄女": case "再从伯": case "再从叔": case "再从姑": return "再从堂兄弟/再从堂姐妹";
            case "三从侄": case "三从侄女": case "三从伯": case "三从叔": case "三从姑": return "三从堂兄弟/三从堂姐妹";
            default:
                if (term.contains("表侄") || term.contains("表伯") || term.contains("表叔") || term.contains("表姑")) return "表兄弟/表姐妹";
                if (term.contains("堂侄") || term.contains("堂伯") || term.contains("堂叔") || term.contains("堂姑")) return "堂兄弟/堂姐妹";
                return term;
        }
    }

    /** 从路径步骤序列解析精确称谓（堂伯、堂侄、表兄弟、再从、三从等）；每步为 起点→终点 的称谓 */
    private static String preciseKinshipFromSteps(List<String> steps, Map<Integer, Member> memberMap) {
        if (steps.isEmpty()) return null;
        if (steps.size() == 1) {
            String s = steps.get(0);
            if (s != null && (s.equals("父亲") || s.equals("母亲") || s.equals("哥哥") || s.equals("弟弟") || s.equals("姐姐") || s.equals("妹妹")
                    || s.equals("表哥") || s.equals("表弟") || s.equals("表姐") || s.equals("表妹") || s.equals("丈夫") || s.equals("妻子")
                    || s.equals("子女") || s.equals("爷爷") || s.equals("奶奶") || s.equals("孙子") || s.equals("孙女") || s.equals("外孙") || s.equals("外孙女")
                    || s.equals("岳父") || s.equals("岳母") || s.equals("公公") || s.equals("婆婆") || s.equals("儿媳") || s.equals("女婿")))
                return s;
        }
        if (steps.size() >= 3) {
            String s0 = steps.get(0), s1 = steps.get(1), s2 = steps.get(2);
            boolean up1 = "父亲".equals(s0) || "母亲".equals(s0);
            boolean sibling = "哥哥".equals(s1) || "弟弟".equals(s1) || s1.equals("姐姐") || s1.equals("妹妹");
            boolean inLaw = "儿媳".equals(s2) || "女婿".equals(s2);
            if (up1 && sibling && inLaw) return "堂嫂/堂弟媳（堂兄弟之配偶）";
        }
        
        // 明确区分父子母子关系和爷孙关系
        int up = 0, down = 0;
        boolean paternal = true;
        boolean hasParentChild = false;
        boolean hasGrandparentGrandchild = false;
        boolean hasMaternalLine = false; // 母系线
        
        for (String s : steps) {
            if (s == null) continue;
            switch (s) {
                case "父亲": 
                    up++; 
                    paternal = true; 
                    hasParentChild = true;
                    break;
                case "母亲": 
                    up++; 
                    paternal = false; 
                    hasParentChild = true;
                    hasMaternalLine = true;
                    break;
                case "爷爷": 
                    up += 2; 
                    paternal = true; 
                    hasGrandparentGrandchild = true;
                    break;
                case "奶奶": 
                    up += 2; 
                    paternal = true; 
                    hasGrandparentGrandchild = true;
                    break;
                case "外祖父": 
                    up++; 
                    paternal = false; 
                    hasGrandparentGrandchild = true;
                    hasMaternalLine = true;
                    break;
                case "外祖母": 
                    up++; 
                    paternal = false; 
                    hasGrandparentGrandchild = true;
                    hasMaternalLine = true;
                    break;
                case "子女": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "长子": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "次子": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "小子": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "长女": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "次女": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "小女": 
                    down++; 
                    hasParentChild = true;
                    break;
                case "孙辈": 
                    down += 2; 
                    hasGrandparentGrandchild = true;
                    break;
                case "孙子": 
                    down += 2; 
                    hasGrandparentGrandchild = true;
                    break;
                case "孙女": 
                    down += 2; 
                    hasGrandparentGrandchild = true;
                    break;
                case "外孙辈": 
                    down += 2; 
                    paternal = false; 
                    hasGrandparentGrandchild = true;
                    hasMaternalLine = true;
                    break;
                case "外孙": 
                    down += 2; 
                    paternal = false; 
                    hasGrandparentGrandchild = true;
                    hasMaternalLine = true;
                    break;
                case "外孙女": 
                    down += 2; 
                    paternal = false; 
                    hasGrandparentGrandchild = true;
                    hasMaternalLine = true;
                    break;
                case "哥哥": case "弟弟": case "姐姐": case "妹妹": break;
                case "表哥": case "表弟": case "表姐": case "表妹": paternal = false; break;
                case "丈夫": case "妻子": case "儿媳": case "女婿": case "岳父": case "岳母": case "公公": case "婆婆": break;
                default: break;
            }
        }
        
        // 区分父子母子关系和爷孙关系
        if (hasParentChild && !hasGrandparentGrandchild) {
            // 父子母子关系
            if (up == 1 && down == 0) {
                if (steps.size() >= 2) {
                    String last = steps.get(steps.size() - 1);
                    if ("哥哥".equals(last)) return "伯父";
                    if ("弟弟".equals(last)) return "叔父";
                    if ("姐姐".equals(last) || "妹妹".equals(last)) return "姑母";
                }
                return steps.size() == 1 ? steps.get(0) : null;
            }
            if (up == 0 && down == 1) return steps.size() == 1 ? steps.get(0) : null;
        } else if (hasGrandparentGrandchild) {
            // 爷孙关系 - 需要根据成员性别和代际关系进一步区分
            if (up >= 2 && down == 0) {
                // 祖辈
                return "祖辈";
            }
            if (up == 0 && down >= 2) {
                // 孙辈 - 需要区分是孙子还是外孙
                if (hasMaternalLine) {
                    // 母系线 - 外孙
                    return "外孙辈";
                } else {
                    // 父系线 - 孙子
                    return "孙辈";
                }
            }
        }
        
        if (up == 1 && down == 1) {
            String last = steps.get(steps.size() - 1);
            if ("哥哥".equals(last)) return paternal ? "堂伯" : "表伯";
            if ("弟弟".equals(last)) return paternal ? "堂叔" : "表叔";
            if ("姐姐".equals(last) || "妹妹".equals(last)) return paternal ? "堂姑" : "表姑";
            return paternal ? "堂兄弟/堂姐妹" : "表兄弟/表姐妹";
        }
        if (up == 2 && down == 1) return paternal ? "堂伯/堂叔/堂姑" : "表伯/表叔/表姑";
        if (up == 1 && down == 2) return paternal ? "堂侄/堂侄女" : "表侄/表侄女";
        if (up == 3 && down == 1) return "再从伯/再从叔/再从姑";
        if (up == 1 && down == 3) return "再从侄/再从侄女";
        if (up == 4 && down == 1) return "三从伯/三从叔/三从姑";
        if (up == 1 && down == 4) return "三从侄/三从侄女";
        if (up == 2 && down == 2) return paternal ? "堂兄弟/堂姐妹" : "表兄弟/表姐妹";
        if (up == 3 && down == 3) return paternal ? "再从堂兄弟/再从堂姐妹" : "再从表兄弟/再从表姐妹";
        if (up == 3 && down == 2) return "三从表侄/三从表侄女";
        if (up == 2 && down == 3) return "三从表伯/三从表叔";
        if (up + down >= 4) {
            int total = up + down;
            if (total == 4) return "远亲（约从堂/表）";
            if (total == 5) return "远亲（约再从）";
            if (total == 6) return "远亲（约三从）";
            return "远亲（约" + total + "代）";
        }
        return null;
    }

    private static class PathResult {
        final List<PathNode> nodes;
        final List<PathEdge> edges;
        PathResult(List<PathNode> nodes, List<PathEdge> edges) { this.nodes = nodes; this.edges = edges; }
    }

    /** 若两人存在直接关系，返回该关系，否则返回 null */
    private Relationship findDirectRelationship(int member1ID, int member2ID) throws SQLException {
        List<Relationship> list = relationshipRepository.getRelationshipsInvolvingMember(member1ID);
        for (Relationship rel : list) {
            int other = rel.getMember1() == member1ID ? rel.getMember2() : rel.getMember1();
            if (other == member2ID) return rel;
        }
        return null;
    }

    /** 获取指定成员向上 maxGenerations 代内的所有祖先 */
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

/** 查找最近的共同祖先 */
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

/** 在关系图上计算两成员间最短路径步数 */
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

    /** 路径上的节点 */
    public static class PathNode {
        private final int id;
        private final String name;
        public PathNode(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
    }

    /** 路径上的边 */
    public static class PathEdge {
        private final int fromId;
        private final int toId;
        private final String description;
        public PathEdge(int fromId, int toId, String description) { this.fromId = fromId; this.toId = toId; this.description = description; }
        public int getFromId() { return fromId; }
        public int getToId() { return toId; }
        public String getDescription() { return description; }
    }

/** 远亲关系结果类 */
    public static class DistantRelativeResult {
        private final boolean isDistantRelative;
        private final String description;
        private final int closestCommonAncestorID;
        private final int commonAncestorCount;
        private final List<PathNode> pathNodes;
        private final List<PathEdge> pathEdges;
        private final String preciseKinshipTerm;

        public DistantRelativeResult(boolean isDistantRelative, String description) {
            this(isDistantRelative, description, -1, 0, null, null, null);
        }

        public DistantRelativeResult(boolean isDistantRelative, String description, int closestCommonAncestorID, int commonAncestorCount) {
            this(isDistantRelative, description, closestCommonAncestorID, commonAncestorCount, null, null, null);
        }

        public DistantRelativeResult(boolean isDistantRelative, String description, int closestCommonAncestorID, int commonAncestorCount,
                                     List<PathNode> pathNodes, List<PathEdge> pathEdges) {
            this(isDistantRelative, description, closestCommonAncestorID, commonAncestorCount, pathNodes, pathEdges, null);
        }

        public DistantRelativeResult(boolean isDistantRelative, String description, int closestCommonAncestorID, int commonAncestorCount,
                                     List<PathNode> pathNodes, List<PathEdge> pathEdges, String preciseKinshipTerm) {
            this.isDistantRelative = isDistantRelative;
            this.description = description;
            this.closestCommonAncestorID = closestCommonAncestorID;
            this.commonAncestorCount = commonAncestorCount;
            this.pathNodes = pathNodes != null ? pathNodes : Collections.emptyList();
            this.pathEdges = pathEdges != null ? pathEdges : Collections.emptyList();
            this.preciseKinshipTerm = preciseKinshipTerm;
        }

        public boolean isDistantRelative() { return isDistantRelative; }
        public String getDescription() { return description; }
        public int getClosestCommonAncestorID() { return closestCommonAncestorID; }
        public int getCommonAncestorCount() { return commonAncestorCount; }
        public List<PathNode> getPathNodes() { return pathNodes; }
        public List<PathEdge> getPathEdges() { return pathEdges; }
        public String getPreciseKinshipTerm() { return preciseKinshipTerm; }
    }
}
