# demo — 学生管理系统（ClassHub 前身）

基于 Spring Boot 的学生管理系统后端，正在按 `docs/project-planning/开发路线图.md` 逐步演进为
学生/班级/课程/成绩一体化管理平台（内部代号 ClassHub）。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Java | 17–26（本机实际使用 JDK 26 构建） |
| Spring Boot | 4.1.1 |
| MyBatis-Plus | 3.5.17（spring-boot4-starter） |
| MySQL | 8.0.x |
| 构建工具 | Maven Wrapper（推荐配合 `D:\develop\apache-maven-3.9.15\conf\settings.xml`） |

## 快速启动

1. **准备数据库**：MySQL 8 中创建库名 `studentmanage`，建表脚本与结构见
   `docs/project-planning/技术方案与数据库设计.md`。
2. **配置本地私密信息**（密码不入 git）：
   ```bash
   # 复制模板
   cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
   # 然后编辑 application-local.yaml，把占位密码替换为你的真实数据库密码
   ```
   也可以用环境变量 `DB_PASSWORD` 代替 local 文件。
3. **运行**：IDEA 中直接运行 `DemoApplication`，或：

   ```bash
   mvnw.cmd spring-boot:run
   ```

4. 默认端口 `8080`，接口前缀 `/student`（规划中会迁移到 `/api` 并扩充模块）。

## 已有接口（阶段0 之前的基础功能）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/student/register` | 注册学生（BCrypt 加密存储） |
| PUT | `/student/login` | 登录并返回 JWT |
| GET | `/student/{id}` | 查询学生（需 `Authorization: Bearer <token>`） |

> 注意：当前唯一约束在 `phone` 上，`name` 无唯一索引；`gender` 在数据库中为 `char(1)`，
> 与实体 `Integer` 不一致。完整问题清单见 `docs/project-planning/项目现状分析.md`。

## 构建

```bash
mvnw.cmd test
```

> 本机若命令行仓库为空，请加 `-s "D:\develop\apache-maven-3.9.15\conf\settings.xml"`
> 使用 IDEA 同款配置；离线验证可再加 `-o` 与
> `-Daether.syncContext.named.factory=noop`。

## 相关文档

- [项目现状分析](docs/project-planning/项目现状分析.md)
- [项目方向与需求分析](docs/project-planning/项目方向与需求分析.md)
- [技术方案与数据库设计](docs/project-planning/技术方案与数据库设计.md)
- [资料检索清单](docs/project-planning/资料检索清单.md)
- [开发路线图](docs/project-planning/开发路线图.md)
