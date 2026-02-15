# 2026年2月15日 FamilySys 技术文档

本文档记录 2026-02-15 的关系网功能实现、页面风格同步、部署脚本修复与云端部署更新，并附关键代码与部署验证信息。

## 1. 背景与目标

1. 完成“近亲网状视图”核心逻辑与展示（血缘步数扩散 + 夫妻关系补充 + 关系合并显示）。
2. 统一前端页面风格与导航体验，提升系统一致性与可维护性。
3. 修复部署脚本中的变量拼写错误，确保自动化部署稳定可靠。
4. 完成云端更新与基本可用性验证。

## 2. 变更内容

### 2.1 近亲网状视图核心逻辑
以“步数=血缘扩散”为主线，按步限制代差（≤1），步后补充夫妻关系，不新增节点；同时合并双向关系为一条边展示，并统计未展示的关系数量。

后端核心逻辑（血缘扩散 + 夫妻补充 + 关系合并）：
```java
for (Relationship rel : relationships) {
    if (isBloodRelation(rel.getRelation())) {
        adjacency.computeIfAbsent(rel.getMember1(), k -> new ArrayList<>()).add(rel);
        adjacency.computeIfAbsent(rel.getMember2(), k -> new ArrayList<>()).add(rel);
    }
}

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
```

关系合并展示（双向关系合并为“父子/母女/兄妹/夫妻”等）：
```java
if (types.contains(1) && types.contains(2)) {
    return "夫妻";
}
if (types.contains(27) && types.contains(32)) {
    return "岳父/女婿";
}
if (types.contains(29) && types.contains(31)) {
    return "公公/儿媳";
}
```

接口输出增加隐藏关系计数：
```java
json.put("hiddenRelationsCount", result.getHiddenRelationsCount());
```

前端渲染策略（节点代差颜色 + 血缘/婚姻线型区分 + 结果提示）：
```javascript
const links = data.edges.map(edge => {
    const isMarriage = edge.edgeType === 'marriage';
    return {
        source: String(edge.fromId),
        target: String(edge.toId),
        edgeLabel: edge.description,
        lineStyle: {
            color: isMarriage ? '#ec4899' : '#94a3b8',
            type: isMarriage ? 'dashed' : 'solid',
            width: isMarriage ? 2.5 : 2
        }
    };
});

if (hiddenCount > 0) {
    setStatus(`已加载 ${data.nodes.length} 个节点 / ${data.edges.length} 条关系，未展示关系 ${hiddenCount} 条`, 'error');
} else {
    setStatus(`已加载 ${data.nodes.length} 个节点 / ${data.edges.length} 条关系，已完整展示`, 'success');
}
```

### 2.2 前端页面风格同步
已将成员管理、关系管理、家族关系树、远亲关系查询等页面统一页眉样式与导航按钮，并补齐“近亲网状视图”入口，保持本地与远端页面一致。

涉及页面（本地与远端）：
- family-frontend：members2.html、relationships4.html、family-tree-final.html、distant-relative.html
- family-frontend-remote：members2.html、relationships4.html、family-tree-final.html、distant-relative.html

### 2.3 部署脚本修复
修复 `deploy_update.ps1` 中数据库变量的拼写问题，避免脚本执行时变量未定义导致的部署中断。

```powershell
$LOCAL_JAR = "family-backend\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_DB = "family-backend\src\main\resources\family.db"
```

### 2.4 云端部署更新
更新部署产物到阿里云 ECS（Ubuntu 24.04），并完成前端与后端可达性验证。

## 3. 部署与验证

### 3.1 本地打包
```bash
cd family-backend
mvn clean package
```

### 3.2 一键部署脚本
```powershell
powershell -ExecutionPolicy Bypass -File .\deploy_update.ps1
```

### 3.3 线上可用性检查
```bash
curl -I http://47.109.193.180/family/members2.html
curl -I http://47.109.193.180:8000
```

## 4. 关键产出

- 关系网完成落地：步数血缘扩散、婚姻补充、关系合并显示与隐藏关系提示可用。
- 前端页面体验一致：统一页眉、按钮风格、导航结构，减少认知切换成本。
- 部署脚本可用性修复：消除变量拼写错误带来的部署阻断风险。
- 线上更新成功：前端 200 OK，后端端口可达。

## 5. 传统文化融合式总结

家族系统讲究“修谱以正名，续脉以传承”。这次更新如同修订族谱中的条目：
- **“礼以定分”**：关系网以步数与代差为纲，边界清晰，亲疏有序。
- **“慎终追远”**：部署流程的稳定性修补，既是对现有数据的守护，也是对后续传承的负责。
- **“协和万邦”**：前后端联动与云端发布，如同族脉相通，系统整体运转顺畅而有序。

技术是器，文化是道。以器载道，系统才有长久之道。
