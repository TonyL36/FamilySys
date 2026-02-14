# 2026年2月14日 FamilySys 部署与修复技术文档

本文档记录了2026年2月14日进行的系统修复、部署架构调整及关键代码变更。

## 1. 部署架构调整 (Frontend Path Adjustment)

为了规范服务器目录结构并解决路径混淆问题，前端静态资源部署路径进行了调整。

*   **变更前路径**: `/var/www/familysys-frontend/family-frontend-remote/` (直接在根目录下)
*   **变更后路径**: `/var/www/familysys-frontend/family-frontend-remote/family/`
*   **新访问地址**: `http://47.109.193.180/family/members2.html` (及其他HTML文件)

### 1.1 部署脚本 (`deploy_update.ps1`) 更新
部署脚本已更新以支持新的目录结构：
*   远程清理命令增加了 `mkdir -p .../family`。
*   SCP传输目标路径更新为包含 `/family` 子目录。

## 2. 前端通信修复 (Frontend API Fixes)

### 2.1 API 地址修正 (`members2.html`)
解决了前端无法连接后端API的问题 (`TypeError: Failed to fetch`)。
*   **问题原因**: 代码中硬编码了 `http://127.0.0.1:8000`，导致部署在服务器上的前端尝试连接用户本地的后端（或无法连接）。
*   **修复**: 将 `API_BASE_URL` 更新为服务器公网IP `http://47.109.193.180:8000`。
*   **文件**: `family-frontend-remote/members2.html` (及其他相关前端文件)

## 3. 后端持久化修复 (Backend Persistence)

### 3.1 数据库连接逻辑优化 (`DatabaseConnection.java`)
解决了服务器重启或重新部署后数据丢失（恢复初始状态）的问题。
*   **问题原因**: 原逻辑在JAR运行时倾向于使用 `getResource` 流读取JAR包内的 `family.db`，并可能通过临时文件处理，导致无法保存修改。
*   **修复**: 
    1.  **优先级调整**: 优先检查当前工作目录（CWD）下的 `family.db`。
    2.  **自动提取**: 如果工作目录没有数据库文件，从JAR包中提取标准库到工作目录，确保后续操作针对的是磁盘上的实体文件而非内存流。
    
```java
// 关键代码逻辑
File cwdDb = new File("family.db");
if (cwdDb.exists()) {
    return "jdbc:sqlite:" + cwdDb.getAbsolutePath();
}
// ... 提取逻辑 ...
```

## 4. 跨域资源共享 (CORS) 配置
确认后端已配置允许跨域请求，支持前端从 Nginx 托管的静态页面访问后端 API。
*   **配置**: `Application.java` 中 `withSecurity` 包装器添加了 `Access-Control-Allow-Origin: *` 等头信息。

## 5. 遗留问题与后续建议
*   **SSH权限**: 服务器SSH访问仍存在权限问题，目前依赖 `deploy_update.ps1` 进行自动化部署，手动登录可能受限。
*   **硬编码IP**: 前端代码中目前硬编码了服务器IP，未来建议通过配置文件或相对路径（如果前后端同源）优化。
