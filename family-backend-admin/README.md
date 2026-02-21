# 家族关系管理系统 - 远亲关系查询功能

## 功能概述

本系统实现了基于家族数据库和向上两辈相同祖先的远亲关系判断查询功能。通过分析家族成员之间的血缘关系，系统能够自动识别和分类远亲关系，包括堂/表兄弟姐妹、叔伯/姑姨与侄子/侄女等关系。

## 核心算法

### 远亲关系计算原理
1. **共同祖先查找**：向上追溯最多两代，查找两个成员的所有共同祖先
2. **最近共同祖先**：计算代际距离，找到距离最短的共同祖先
3. **关系类型判定**：根据共同祖先和代际关系确定具体的远亲类型

### 关系类型定义
- **堂/表兄弟姐妹**：具有共同祖父母或外祖父母的同辈成员
- **叔伯/姑姨与侄子/侄女**：一方是另一方父母的兄弟姐妹
- **远房堂/表兄弟姐妹**：具有更远共同祖先的同辈成员
- **远亲**：其他无法归类的远亲关系

## API 使用说明

### 远亲关系查询端点
```
GET /relationship?distantRelative=&member1ID={id1}&member2ID={id2}
```

### 请求参数
- `member1ID`：第一个成员的ID（必填）
- `member2ID`：第二个成员的ID（必填）

### 响应格式
```json
{
  "isDistantRelative": true,
  "description": "堂/表兄弟姐妹",
  "closestCommonAncestorID": 101,
  "commonAncestorCount": 1
}
```

### 响应字段说明
- `isDistantRelative`: 是否为远亲关系（布尔值）
- `description`: 关系描述文本
- `closestCommonAncestorID`: 最近共同祖先的ID
- `commonAncestorCount`: 共同祖先总数

## 系统架构

### 新增组件
- `FamilyRelationshipCalculator.java`: 核心算法实现类
- `RelationshipService.findDistantRelative()`: 服务层方法
- `RelationshipController` 新增处理逻辑: 控制器层API端点

### 数据库支持
- 基于现有的 `Members` 和 `Relationships` 表结构
- 无需修改数据库模式，完全兼容现有数据

## 使用示例

### 示例1：查询两个成员是否为远亲
```
GET /relationship?distantRelative=&member1ID=17&member2ID=19
```

响应：
```json
{
  "isDistantRelative": true,
  "description": "堂/表兄弟姐妹",
  "closestCommonAncestorID": 2,
  "commonAncestorCount": 1
}
```

### 示例2：查询无共同祖先的成员
```
GET /relationship?distantRelative=&member1ID=1&member2ID=50
```

响应：
```json
{
  "isDistantRelative": false,
  "description": "无共同祖先（向上两代内）",
  "closestCommonAncestorID": -1,
  "commonAncestorCount": 0
}
```

## 部署说明

1. 确保数据库文件 `family.db` 存在于 `src/main/resources/` 目录
2. 编译项目：`mvn clean compile`
3. 运行应用：`mvn exec:java -Dexec.mainClass="Application"`
4. 访问API：`http://localhost:8000/relationship?distantRelative=&member1ID=1&member2ID=2`

## 技术特点

- **高效算法**：使用BFS（广度优先搜索）确保找到最短路径
- **内存优化**：限制向上追溯代数，避免性能问题
- **错误处理**：完善的异常处理和日志记录
- **向后兼容**：不影响现有所有功能
- **可扩展性**：易于添加新的关系类型和算法优化

## 未来改进方向

- 支持更多代数的祖先追溯
- 添加可视化家谱图生成功能
- 实现关系置信度评分
- 增加批量远亲关系查询
- 添加关系路径详细信息返回

---
*版本：1.0 | 最后更新：2026-02-11*