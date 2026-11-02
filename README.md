# ClassHub — 学生一体化管理平台

[![CI](https://github.com/LIJUNYU-code/StudentManagerSystem/actions/workflows/ci.yml/badge.svg)](https://github.com/LIJUNYU-code/StudentManagerSystem/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-42B883)](https://vuejs.org/)
[![Tests](https://img.shields.io/badge/tests-309%20passing-brightgreen)](#测试)

基于 **Spring Boot 4 + Vue 3** 的学生/班级/课程/成绩一体化管理平台。
从最初的"学生管理系统"练习项目逐步演进而来，完整开发过程见
[docs/project-planning/](docs/project-planning/) 与 [docs/项目总结与经验复盘.md](docs/项目总结与经验复盘.md)。

## 功能总览

- **三角色权限（RBAC）**：admin（用户审核/禁用）、teacher（全部业务）、student（仅查自己的成绩）
- **学生管理**：注册（学号 `S`+9 位数字、BCrypt 密文）、CRUD、分页、关键词/班级筛选、逻辑删除
- **班级 / 课程管理**：CRUD、分页、关键词搜索、重名 409
- **选课**：学生↔课程，联合唯一防重复选课
- **成绩**：按课程录入（0-100 分 + 期末/期中/平时）、课程成绩单（两跳 join）、学生自助查分（角色隔离）
- **JWT 无状态认证**：登录返回 token，401/403 语义分离，前端 axios 拦截器统一处理

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17–26 · Spring Boot 4.1.1 · Spring Security(JWT) · MyBatis-Plus 3.5.17 · MySQL 8 · Maven Wrapper |
| 前端 | Vue 3 · Vite · Element Plus（按需引入） · Pinia · Vue Router · Axios · ECharts 6（按需引入） |
| 测试 | JUnit 5 + Mockito（162 个单元用例）+ MockMvc 切片（147 条）+ 106 条端到端接口用例 + 手工验收清单 |

## 快速启动

### 后端

1. MySQL 8 中创建库 `studentmanage`，按顺序执行 `sql/` 下的迁移脚本（V1→V8）；
2. 配置私密信息（密码不入 git）：

   ```bash
   cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
   # 编辑 application-local.yaml，把占位密码换成你的真实数据库密码
   ```

3. 运行：IDEA 启动 `DemoApplication`，或 `mvnw.cmd spring-boot:run`。默认端口 8080。

### 前端

```bash
cd web
pnpm install    # 或 npm install
pnpm dev        # 开发服务器，默认 http://localhost:5173
```

> Vite 已配置代理：前端请求 `/` 下的接口自动转发到 `http://localhost:8080`，无需处理跨域。

### 测试账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin@123456 | 管理员（审核教师注册） |
| teacher_wang | Abc12345 | 教师 |
| S200001234 | Abc12345 | 学生（登录后进学生端首页） |

## 接口一览（10 组 Controller）

| 前缀 | 说明 | 权限 |
| --- | --- | --- |
| `POST /student/register`、`POST /student/login` | 学生注册/登录 | 公开 |
| `POST /auth/register`、`POST /auth/login` | 教师注册（需 admin 审核）/登录 | 公开 |
| `POST /auth/logout` | 登出：当前 token 进 Redis 黑名单（TTL=剩余有效期） | 登录用户 |
| `POST /auth/password` | 修改密码：旧密码校验 + 重新签发 token，该用户所有旧 token 强制下线 | 登录用户 |
| `/student` GET/PUT/DELETE | 学生管理（分页/筛选/逻辑删除） | teacher、admin |
| `POST /student/{id}/reset-password` | 重置学生密码（教师/管理员） | teacher、admin |
| `/clazz` | 班级 CRUD | teacher、admin |
| `/course` | 课程 CRUD | teacher、admin |
| `/enrollments` | 选课/退课 | teacher、admin |
| `/scores`、`GET /scores/my` | 录成绩、课程成绩单、学生自助查分 | teacher / student 自助 |
| `/stats/course/{id}/summary`、`/distribution` | 课程成绩概览、分数段分布 | teacher、admin |
| `/stats/clazz/{id}/compare` | 班级各课平均分对比 | teacher、admin |
| `GET /stats/my/trend` | 学生自己的成绩趋势 | student |
| `/attendance/batch`、`GET /attendance`、`PUT /attendance/{id}` | 批量点名（覆盖语义）、点名名单、单条修改 | teacher、admin |
| `GET /attendance/stats` | 课程出勤统计 | teacher、admin |
| `GET /attendance/my` | 学生自己的考勤明细 | student |
| `POST /leave`、`GET /leave/my`、`PUT /leave/{id}/cancel` | 提交请假、我的请假、撤销待审核申请 | student |
| `GET /leave`、`PUT /leave/{id}/approve`、`PUT /leave/{id}/reject` | 请假列表、审批通过（自动同步考勤）、驳回 | teacher、admin |
| `/admin/user` | 用户审核/禁用/列表 | admin |

完整字段与用例见 [docs/验收测试清单.md](docs/验收测试清单.md)。

## 构建

```bash
# 后端：跑全部后端测试（309 用例，不依赖数据库）
mvnw.cmd clean test

# 后端：打包 fat-jar（阶段6.4）
mvnw.cmd package

# 前端：构建产物到 web/dist
cd web && pnpm build
```

> 本机 Maven 特殊性：命令行仓库为空时请加
> `-s "D:\develop\apache-maven-3.9.15\conf\settings.xml"`（IDEA 同款配置）；
> 离线验证再加 `-o -Daether.syncContext.named.factory=noop`。

## 测试

| 层级 | 条数 | 是否连库 | 说明 |
| --- | --- | --- | --- |
| Service 单元测试 | 162 | ❌ | Mockito 打桩，覆盖业务规则与状态机 |
| Controller 切片 | 147 | ❌ | `@WebMvcTest` + 真实安全过滤链，覆盖 HTTP 状态码与权限矩阵 |
| 端到端接口测试 | 106 | ✅ | 真实 HTTP + MySQL + Redis，见 `docs/测试报告.md` |
| **合计** | **309**（自动化）+ 106（端到端） | | |

**309 条自动化测试全部不依赖 MySQL / Redis**，`./mvnw test` 在干净环境即可全绿——
这一点经过实测验证（把数据源和 Redis 都指向不存在的端口 3999，测试依然全绿），
所以 CI 不需要启动任何数据库服务。

## 持续集成（CI）

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) 在每次 push 到 `main` 或提 PR 时自动执行：

| Job | 内容 | 耗时（实测参考） |
| --- | --- | --- |
| `backend` | JDK 25 + `./mvnw -B -ntp test`，上传 surefire 报告 | 首次 ~2 分钟（含依赖下载），缓存命中 ~40 秒 |
| `frontend` | Node 22 + `npm ci` + `npm run build`，上传 dist 产物 | ~1 分钟 |

设计要点：

1. **不需要 `services: mysql/redis`** —— 因为 309 条测试全是切片/单测，不碰真库。将来若加 `@SpringBootTest` 集成测试才需要。
2. **`npm ci` 而非 `npm install`** —— 严格按 `package-lock.json` 安装，保证 CI 可复现，不会悄悄升级小版本。
3. **Maven 缓存 key 带 `hashFiles('**/pom.xml')`** —— pom 没变直接命中缓存，变了才重新下载。
4. **`concurrency` 取消旧运行** —— 同一分支连续 push 时自动取消上一次，省额度。

> ⚠️ **本地命令不要直接复制进 CI**：本机因为依赖在自定义仓库、走阿里云镜像，
> 需要 `-s <settings.xml> -o`。CI 上没有那个文件、也走中央仓库，加了反而失败。
> 这正是本地验证第 3 步的意义——用**去掉本地参数后**的命令实测。

## 项目结构

```
demo/
├── .github/workflows/ci.yml           # CI：后端测试 + 前端构建（push/PR 自动触发）
├── src/main/java/com/example/demo/    # 后端（controller/service/mapper/entity/dto/vo/config/…）
├── src/test/java/                     # 309 条测试（Service 单测 + Controller 切片）
├── sql/                               # V1–V12 数据库迁移脚本
├── deploy/nginx/                      # 生产部署配置（静态托管 + API 反代，已实测）
├── web/                               # Vue 3 前端
│   └── src/{api,components,layout,router,stores,views,assets}
└── docs/
    ├── project-planning/              # 五份规划文档（现状/方向/技术方案/资料/路线图）
    ├── learning/                      # 前端学习课程
    ├── 测试报告.md                     # 全量测试记录（309 后端 + 106 端到端）
    ├── 部署说明.md                     # 打包与部署
    ├── 项目总结与经验复盘.md
    └── 验收测试清单.md
```

## Roadmap

- [x] 阶段0 环境地基（git/密码出代码/包规范/README）
- [x] 阶段1 数据库重构 + 学生 CRUD
- [x] 阶段2 统一响应与全局异常
- [x] 阶段3 RBAC（user 表/审核/方法级权限）
- [x] 阶段4 课程 + 选课
- [x] 阶段5 成绩模块 + 前端 M3
- [x] 阶段6.1–6.2 单元测试 + 验收清单
- [x] 阶段6.3–6.4 README 收官 + 打包部署验证
- [x] 阶段7 Redis 登出黑名单 + 改密强制下线（JWT 主动作废）
- [x] 阶段8 全量测试（208 后端 + 51 端到端）与 P0/P1 缺陷修复，见 [docs/测试报告.md](docs/测试报告.md)
- [x] 阶段9 Redis 故障降级（熔断 + 本地兜底），Redis 停机不再影响带 token 请求，见测试报告 5.4
- [x] 阶段10 生产部署验证（Nginx 静态托管 + API 反代，16 项实测通过），见 [docs/部署说明.md](docs/部署说明.md)
- [x] 阶段11 成绩统计与考勤（ECharts 图表 / 批量点名 / 出勤率），264 后端 + 76 端到端全绿，见测试报告 6.1
- [x] 阶段12 请假审批流（状态机 / 审批通过自动同步考勤 / 越权防护），309 后端 + 106 端到端全绿，见测试报告 4.2-K 与 5.6
- [x] 阶段13 持续集成（GitHub Actions：后端测试 + 前端构建，push/PR 自动触发）

## 二期可扩展（方向 C）

以下功能已完成数据与接口基础，但**未实现**，属于后续独立阶段：

- ~~请假审批流~~ ✅ 已完成（阶段12）；考勤按课表展开（需 course_schedule 表）
- 成绩趋势预测 / 班级排名（`StatsMapper` 已有聚合能力，加一个 SQL 即可）
- Excel 批量导入导出（学生名单、成绩表）
- 多学期支持（当前 `score.exam_type` 只有 final/midterm/makeup，没有学期维度）
