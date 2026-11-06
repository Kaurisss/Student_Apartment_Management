# 学生公寓管理系统

Java Web 课程实训项目。使用 Java 17、Spring Boot 3.4、Spring Security、JDBC 和 MySQL 8.0.16+；前端使用 Vue 3、Vue Router、Arco Design Vue 和 @phosphor-icons/vue。在作业2基础上补充学生注册、报修位置与处理评价、访客登记及资料字段，共七个实体。Java 21 也可运行。前端生产文件已放入后端静态资源，直接打包运行无需 Node.js。

## 启动（Docker 数据库 + 本机 Java）

需要 JDK 17 或 21、Maven 3.9、Docker 和 Compose。首次启动数据库会自动执行 [初始化 SQL](sql/init.sql)，建立当前完整表结构并插入示例数据；已有数据卷不会重新导入。项目仅保留这一份初始化 SQL。

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

也可自行安装 MySQL 8.0.16+，在**全新数据库**中执行 `mysql --default-character-set=utf8mb4 -u root -p < sql/init.sql`（脚本建库并插入示例数据，不可对已有同名表重复执行），为应用创建具有该库读写权限的账号，按上述方式设置 `DB_URL`（默认端口 3306）、`DB_USER` 和 `DB_PASSWORD`。连接地址应指向数据库 `dorm_management`。

## 演示操作

登录页支持学生注册与查看公共统计。注册会同时创建学生账号和档案，不能注册管理员。学生数据概览显示本人的床位、入住状态、待处理报修、待评价报修与报修进度；管理员查看全局统计及各宿舍床位。公共统计仅显示整体入住率与报修处理率，不提供各宿舍明细。

概览及列表每30秒从 API 自动更新，切回可见页面或窗口重新获得焦点时立即更新。后台页面、资料编辑页及打开表单时暂停自动刷新；刷新保留已有内容、分页与筛选条件，避免闪烁或覆盖未提交内容。

宿舍管理按楼栋分组：先选择楼栋，再查看楼内房间、床位总数与空闲床位。点击“添加房间”可新增单间，楼栋自动带入；“批量添加房间”支持起止房号，如301至320，并可按四人间或六人间自动创建床位。“新增楼栋”可连同首批房间一起建立。每批最多200间，数字房号支持001等前导零；遇到重复房号整批回滚。此调整使用已有楼栋与房间数据，不需要数据库迁移。

作业2测试数据中的 `student01` 已分配 1号楼301的01床，并有一条待受理报修：

1. 管理员登录，在“报修管理”对示例报修依次点击“受理”“标记完成”，填写完成说明；学生登录，选择满意或不满意并填写评语，状态变为“已评价”。新建报修时须选择宿舍，位置保存于报修单中，不随换宿变化。
2. 管理员在“入住与退宿”对示例记录点击“办理入住”；学生登录点击“申请退宿”；管理员点击“办理退宿”，床位自动变为空闲。学生再点击“入住申请”，从空闲床位中选择并提交，申请进入待审核（此时床位仍空闲）；管理员点击“分配床位”，确认学生所选床位或改派其他床位，再办理入住，重复演示完整流程。管理员端不提供报修和入住申请入口，仅负责查看和处理。
3. 管理员查看“数据概览”中的入住率、报修处理率和分项统计。床位一旦分配即计入占用，办理退宿后才释放。
4. 基础信息中可维护账号、学生、宿舍、床位和访客；学生资料支持班级、联系电话，宿舍支持房间类型。访客登记包含被访学生、来访和离开时间，离开时间可以暂不填写，填写后不能早于来访时间。报修支持新增、查看、修改和删除（修改/删除限待受理）。管理员也可先建 STUDENT 账号，再建立并关联学生。列表每页 10 条；有关联或已进入处理的记录受约束，不能任意删除。

统计口径：入住率为占用床位数除以总床位数；报修处理率为已完成及已评价单数除以全部报修数，学生评价后仍计入已处理。完整功能核对与验证记录见 [功能缺失核对清单](docs/功能缺失核对清单.md)。

学生仅能操作自己的记录；管理员负责受理、完成、分配和办理，学生负责提交报修与入住申请、确认评价及申请退宿。每个业务操作在后端重新校验角色、所属人和当前状态。

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

## 前端响应式测试

```bash
cd frontend
npm ci
npx playwright install chromium
npm run test:ui
```

测试使用模拟接口数据，覆盖桌面与移动端导航、767/768 像素断点、表格横向滚动、学生角色菜单和空统计。也可通过 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` 指定已安装的 Chromium 路径。

楼栋分组和批量建房的真实 API 验证可在服务运行后执行 `BASE_URL=http://127.0.0.1:8080 python scripts/dormitory_test.py`（PowerShell 先设置 `$env:BASE_URL`）。该脚本创建带唯一名称的验收楼栋，检查分页、床位生成、重复房号事务回滚及角色权限，最后删除本次创建的房间和床位。

## 项目结构

- `src/main/java/cn/edu/dorm/SecurityConfig.java`：登录、角色授权、密码哈希（兼容作业2初始密码格式）和 CSRF。
- `AdminService.java`：账号、学生、宿舍、床位、访客的校验、分页与维护。
- `BusinessService.java`：报修、入住状态变更，事务与行锁、统计查询。
- `ApiController.java`：JSON 接口、参数传递及错误反馈。
- `frontend/src/App.vue`：Vue 页面，使用 Arco Design Vue 组件和 Phosphor 图标；`frontend/src/components/AppNavigation.vue` 为桌面与移动端共用的导航；`frontend/src/style.css` 为界面样式。
- `src/main/resources/static/`：已经构建好的前端文件，随 Java 包一起交付。
- `sql/init.sql`：唯一的初始化 SQL，包含全部七张业务表、约束及示例数据。
- `sql/mysql-client.cnf`：MySQL 客户端字符集配置。
- `docs/`：部署说明、测试与讲解提纲、AI 使用说明草稿。

详细部署及局域网访问见 [部署说明](docs/部署说明.md)，验证方法见 [测试与讲解](docs/测试与讲解.md)。请结合实际操作和个人理解完善最终课程报告，学号、班级、实际截图、AI 使用过程等需本人填写。作业2是否已提交无法从文件判断。
