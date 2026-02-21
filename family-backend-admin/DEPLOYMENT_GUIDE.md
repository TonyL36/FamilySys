# 家族关系管理系统 - 部署和测试指南

## 快速启动步骤

### 1. 环境准备
- 确保已安装 Java 8 或更高版本
- 确保已安装 Maven（推荐）
- 确保 curl 工具可用（已验证存在）

### 2. 启动服务

#### 方法一：使用 Maven（推荐）
```bash
cd family-backend
mvn clean compile
mvn exec:java -Dexec.mainClass="Application"
```

#### 方法二：使用编译后的类文件
```bash
cd family-backend
target\classes
java -cp ".;target\dependency\*" Application
```

### 3. 验证服务运行
服务默认在端口 8000 运行。启动后，您应该看到日志：
```
INFO  Server started on port 8000
```

### 4. 测试远亲关系查询功能

#### 测试端点是否正常工作
```bash
curl -s http://localhost:8000/relationship?memberID=1 | jq '.'
```

#### 测试远亲关系查询（核心功能）
```bash
# 查询成员17和成员19的远亲关系
curl -s "http://localhost:8000/relationship?distantRelative=&member1ID=17&member2ID=19" | jq '.'

# 查询成员1和成员50的远亲关系
curl -s "http://localhost:8000/relationship?distantRelative=&member1ID=1&member2ID=50" | jq '.'

# 查询成员8和成员9的远亲关系（兄弟关系）
curl -s "http://localhost:8000/relationship?distantRelative=&member1ID=8&member2ID=9" | jq '.'
```

### 5. 预期响应示例

**成功响应：**
```json
{
  "isDistantRelative": true,
  "description": "堂/表兄弟姐妹",
  "closestCommonAncestorID": 2,
  "commonAncestorCount": 1
}
```

**无共同祖先响应：**
```json
{
  "isDistantRelative": false,
  "description": "无共同祖先（向上两代内）",
  "closestCommonAncestorID": -1,
  "commonAncestorCount": 0
}
```

## 故障排除

### 常见问题

**问题：Maven命令不可用**
- 解决方案：下载并安装 Maven，或使用 IDE 的 Maven 集成

**问题：Java 类找不到**
- 解决方案：确保先运行 `mvn clean compile` 编译项目

**问题：Log4j 依赖缺失**
- 解决方案：确保 pom.xml 中包含 Log4j 依赖，或使用 Maven 运行

**问题：数据库连接失败**
- 解决方案：检查 `src/main/resources/family.db` 文件是否存在

## 数据库说明

- 使用 SQLite 数据库 `family.db`
- 数据库包含 `Members` 和 `Relationships` 表
- 远亲计算算法完全兼容现有数据结构

## 技术支持

如需进一步帮助，请联系：
- 查看完整文档：`family-backend/README.md`
- 查看源代码：`family-backend/src/main/java/service/FamilyRelationshipCalculator.java`
- 运行测试：`family-backend/src/test/java/service/FamilyRelationshipCalculatorTest.java`

---
*版本：1.0 | 最后更新：2026-02-11*