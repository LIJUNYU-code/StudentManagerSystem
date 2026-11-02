# ClassHub — 学生一体化管理平台

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
| 前端 | Vue 3 · Vite · Element Plus · Pinia · Vue Router · Axios |
| 测试 | JUnit 5 + Mockito（119 个单元用例）+ MockMvc 切片（89 条）+ 51 条端到端接口用例 + 手工验收清单 |

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

## 接口一览（7 组 Controller）

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
| `/admin/user` | 用户审核/禁用/列表 | admin |

完整字段与用例见 [docs/验收测试清单.md](docs/验收测试清单.md)。

## 构建

```bash
# 后端：跑全部后端测试（208 用例，不依赖数据库）
mvnw.cmd clean test

# 后端：打包 fat-jar（阶段6.4）
mvnw.cmd package

# 前端：构建产物到 web/dist
cd web && pnpm build
```

> 本机 Maven 特殊性：命令行仓库为空时请加
> `-s "D:\develop\apache-maven-3.9.15\conf\settings.xml"`（IDEA 同款配置）；
> 离线验证再加 `-o -Daether.syncContext.named.factory=noop`。

## 项目结构

```
demo/
├── src/main/java/com/example/demo/    # 后端（controller/service/mapper/entity/dto/config/…）
├── src/test/java/                     # 单元测试（JwtUtil / StudentServiceImpl）
├── sql/                               # V1–V8 数据库迁移脚本
├── web/                               # Vue 3 前端
│   └── src/{api,layout,router,stores,views,assets}
└── docs/
    ├── project-planning/              # 五份规划文档（现状/方向/技术方案/资料/路线图）
    ├── learning/                      # 前端学习课程
    ├── 测试报告.md                     # 全量测试记录（208 后端 + 51 端到端）
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
