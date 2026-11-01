# 学生公寓管理系统

Java Web 课程实训项目。使用 Java 17、Spring Boot 3.4、Spring Security、JDBC 和 MySQL 8.0.16+；前端使用 Vue 3、Ant Design Vue 和 @phosphor-icons/vue。数据库与作业2的六实体、字段及状态保持一致。Java 21 也可运行。前端生产文件已放入后端静态资源，直接打包运行无需 Node.js。

## 启动（Docker 数据库 + 本机 Java）

需要 JDK 17 或 21、Maven 3.9、Docker 和 Compose。首次启动数据库会自动执行 [作业2原始 SQL](sql/assignment2-original.sql)，保留测试数据；已有数据卷不会重新导入。

```bash
docker compose up -d
# 等待 docker compose ps 显示数据库 healthy
mvn clean package
DB_URL='jdbc:mysql://127.0.0.1:3307/dorm_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
DB_USER=dorm_app DB_PASSWORD=dorm_app_demo java -jar target/dorm-management-1.0.0.jar
```

Windows PowerShell 将最后一条命令改为：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3307/dorm_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USER='dorm_app'; $env:DB_PASSWORD='dorm_app_demo'
java -jar target/dorm-management-1.0.0.jar
```

浏览器访问 <http://localhost:8080>。测试账号 `admin`、`student01`，初始密码均为 `123456`；测试演示后请在账号管理中修改密码。端口冲突时设置环境变量 `SERVER_PORT`。停止应用用 Ctrl+C；数据库停止用 `docker compose stop`。数据保存在 Docker 卷内。不要使用 `docker compose down -v`，该命令会删除数据库数据。

也可自行安装 MySQL 8.0.16+，在**全新数据库**中执行 `mysql --default-character-set=utf8mb4 -u root -p < sql/assignment2-original.sql`（脚本建库并插入示例数据，不可对已有同名表重复执行），为应用创建具有该库读写权限的账号，按上述方式设置 `DB_URL`（默认端口 3306）、`DB_USER` 和 `DB_PASSWORD`。连接地址应指向数据库 `dorm_management`。

## 演示操作

作业2测试数据中的 `student01` 已分配 1号楼301的01床，并有一条待受理报修：

1. 管理员登录，在“报修管理”对示例报修依次点击“受理”“标记完成”；学生登录并填写评价确认。也可由学生新建报修后重走整个流程。
2. 管理员在“入住与退宿”对示例记录点击“办理入住”；学生登录点击“申请退宿”；管理员点击“办理退宿”，床位自动变为空闲。然后学生可新建申请，管理员选择空床位分配，再办理入住，重复演示完整流程。
3. 管理员查看“数据概览”中的入住率、报修处理率和分项统计。床位一旦分配即计入占用，办理退宿后才释放。
4. 基础信息中可维护账号、学生、宿舍、床位；报修支持新增、查看、修改和删除（修改/删除限待受理）。先建 STUDENT 账号，再建立并关联学生。列表每页 10 条；有关联或已进入处理的记录受约束，不能任意删除。

学生仅能操作自己的记录；管理员负责受理、完成、分配和办理，学生负责确认评价及申请退宿。每个业务操作在后端重新校验角色、所属人和当前状态。

## 修改前端后重新构建

需要 Node.js 20+ 和 npm。修改 `frontend/src` 后，在项目根目录执行：

```bash
cd frontend
npm ci
npm run build
cd ..
mvn clean package
```

Vite 将前端构建到 `src/main/resources/static`，Spring Boot 同源提供页面和 `/api` 接口，现场演示无需另外启动前端服务器。开发时可运行 `npm run dev`，其 `/api` 会代理至 `localhost:8080`，需先启动后端。

## 项目结构

- `src/main/java/cn/edu/dorm/SecurityConfig.java`：登录、角色授权、密码哈希（兼容作业2初始密码格式）和 CSRF。
- `AdminService.java`：四类基础数据的校验、分页与维护。
- `BusinessService.java`：报修、入住状态变更，事务与行锁、统计查询。
- `ApiController.java`：JSON 接口、参数传递及错误反馈。
- `frontend/src/App.vue`：Vue 页面，使用 Ant Design Vue 组件和 Phosphor 图标；`frontend/src/style.css` 为少量界面样式。
- `src/main/resources/static/`：已经构建好的前端文件，随 Java 包一起交付。
- `sql/assignment2-original.sql`：从作业2原样提取的建库、建表和数据。
- `docs/`：部署说明、测试与讲解提纲、AI 使用说明草稿。

详细部署及局域网访问见 [部署说明](docs/部署说明.md)，验证方法见 [测试与讲解](docs/测试与讲解.md)。请结合实际操作和个人理解完善最终课程报告，学号、班级、实际截图、AI 使用过程等需本人填写。作业2是否已提交无法从文件判断。
