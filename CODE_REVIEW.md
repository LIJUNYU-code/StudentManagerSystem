# 代码审查报告：demo 学生管理项目

> 审查方式：静态代码阅读（源码 + 配置 + 构建脚本）+ 实际 `mvn compile` 编译验证。
> 构建验证：`mvn -B -ntp -DskipTests compile` 已通过（BUILD SUCCESS），`target/classes` 生成全部 13 个 class 文件，
> 并用 `javap` 确认 Lombok 生成了 `builder()`/`getPassword()`/`setPhone()` 等成员。
> 标注「需确认」的条目需结合实际数据库与运行环境核实（启动需要真实 MySQL）。

---

## 一、项目概况

| 项 | 内容 |
| --- | --- |
| 类型 | Spring Boot 后端练习项目（学生管理：注册 + 按 ID 查询） |
| 框架 | Spring Boot 4.1.1（parent），Spring MVC |
| 语言/构建 | Java 25，Maven Wrapper 3.3.4（Maven 3.10.0） |
| 持久层 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）+ MySQL |
| 其他 | Lombok、Jakarta Validation、spring-security-crypto（仅 BCrypt） |
| 对外接口 | `POST /student/register`、`GET /student/{id}` |
| 版本管理 | 目录下没有 `.git`，未纳入版本控制 |

## 二、项目结构

```text
demo/
├─ pom.xml                          # 依赖与构建配置
├─ mvnw / mvnw.cmd                  # Maven Wrapper 启动脚本
├─ .mvn/wrapper/maven-wrapper.properties
├─ .gitignore                       # 忽略 target/、.idea、HELP.md 等
├─ HELP.md                          # 脚手架生成的说明（已被 gitignore）
├─ .idea/                           # IntelliJ 本地配置（含数据源配置）
├─ src/main/java/com/example/demo/
│  ├─ DemoApplication.java          # 启动类，@MapperScan("com.example.demo.Mapper")
│  ├─ Controller/StudentController.java      # 注册、查询两个接口
│  ├─ Service/StudentService.java            # 服务接口
│  ├─ Service/ServiceImpl/StudentServiceImpl.java  # 服务实现
│  ├─ Mapper/StudentMapper.java              # BaseMapper + selectByName
│  ├─ Entity/Student.java                    # 学生实体（含密码、时间字段）
│  ├─ DTO/StudentDTO.java                    # 注册入参 + 校验注解
│  ├─ Day1/Result.java                       # 统一响应体（放在教学包 Day1 下）
│  ├─ config/PasswordConfig.java             # BCryptPasswordEncoder Bean
│  ├─ Exception/StudentNotFoundException.java
│  ├─ Exception/StudentHasExisit.java        # 注意：类名拼写错误
│  └─ ExceptionHandler/GlobalExceptionHandler.java   # 全局异常处理
├─ src/main/resources/application.yaml       # 端口、数据源配置
├─ src/test/java/com/example/demo/DemoApplicationTests.java  # 仅 contextLoads
└─ target/                                   # 已编译产物（应清理，已被 gitignore）
```

分层为经典 MVC：Controller → Service → Mapper(MyBatis-Plus) → MySQL，
统一用 `Result` 包装响应、BCrypt 存储密码、`@RestControllerAdvice` 兜底异常。

## 三、核心调用链

**注册** `POST /student/register`

1. `StudentController.RegisterStudent` 接收 `StudentDTO`；
2. 调 `studentService.findByName(name)` 查重；
3. 不存在时在 Controller 内 `passwordEncoder.encode(password)`；
4. 调 `studentService.register(dto)`，Service 用 `Student.builder()` 组装
   `age/name/password` 三个字段后 `insert`；
5. 成功返回 `Result.success()`（空数据）；已存在则抛 `StudentHasExisit`。

**查询** `GET /student/{id}`

1. `StudentController.SelectStudent` → `StudentServiceImpl.selectById`；
2. 查不到抛 `StudentNotFoundException`，由 `GlobalExceptionHandler` 转为 404；
3. 查到则把整个 `Student` 实体（含 `password` 哈希）放进 `Result` 返回。

## 四、问题清单

### P0 — 功能/安全缺陷（建议优先修复）

| # | 位置 | 问题 | 影响 | 建议 |
| --- | --- | --- | --- | --- |
| 1 | `src/main/java/com/example/demo/ExceptionHandler/GlobalExceptionHandler.java:20` | `handleStudentHasExisit` **漏写 `@ExceptionHandler(StudentHasExisit.class)`**，方法只是普通 Bean 方法，不会被调用 | 重复注册时抛出的 `StudentHasExisit` 没有任何处理器兜底，返回 500，前端拿不到「学生已存在」提示 | 补注解；且「已存在」应返回 **409 Conflict** 而非 404（当前方法内部写的是 404） |
| 2 | `src/main/java/com/example/demo/Controller/StudentController.java:26` | `@RequestBody StudentDTO studentDTO` 前**缺少 `@Valid`** | `StudentDTO` 上的 `@NotBlank/@Size/@Pattern` 全部失效：空姓名、空密码、错误手机号都能进业务；`password` 为 null 时 `passwordEncoder.encode(null)` 抛 `IllegalArgumentException` → 500 | 加 `@Valid`；并在全局异常处理器中处理 `MethodArgumentNotValidException`，返回统一的 `Result` + 400 |
| 3 | `src/main/java/com/example/demo/Service/ServiceImpl/StudentServiceImpl.java:35` | `register` 组装实体时**只拷贝 age/name/password，丢弃了 phone 和 gender** | 接口校验了手机号、DTO 也传了性别，但落库永远是 null/0，数据丢失且难以察觉 | builder 中补 `.phone(...)`、`.gender(...)`；或改用 `BeanUtils.copyProperties` 后显式覆盖密码 |
| 4 | `src/main/java/com/example/demo/Controller/StudentController.java:39` | `GET /student/{id}` 直接返回 `Student` 实体，**响应体包含 `password`（BCrypt 哈希）**；`@Data` 生成的 `toString()` 也会把密码打进日志 | 密码哈希泄露给任意调用方；日志泄露风险 | 返回 `StudentVO`（不含 password），实体字段加 `@JsonIgnore`，并避免整对象 `toString` 打日志 |
| 5 | `src/main/resources/application.yaml:7` | 数据库账号/密码**明文硬编码**（具体值已从本报告中移除，避免文档本身泄露凭据） | 凭据随代码扩散，无法区分环境 | 改用环境变量（`${DB_USERNAME}`）或配置中心，仓库中只留占位符 |
| 6 | `src/main/resources/application.yaml:4` | 端口写成了 `spring.server.port`，**正确键是顶层 `server.port`** | 该配置被 Spring 静默忽略；碰巧默认端口就是 8080，所以「看起来能用」，一旦改端口则不生效 | 改为 `server.port: 8080` |

### P1 — 健壮性/设计问题

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 7 | `src/main/java/com/example/demo/Controller/StudentController.java:27-34` | **先查后插**（check-then-act），并发下两个请求可同时通过查重；数据库若无唯一索引必然产生重复学生 | 在 `student.name`（或业务唯一键）上建唯一索引；把查重+编码+插入收敛到 `StudentService.register`，加 `@Transactional`，并捕获 `DuplicateKeyException` |
| 8 | `src/main/java/com/example/demo/Mapper/StudentMapper.java:11` | `@Select("select * from student where name = #{name}")`：`select *` 脆弱，且返回单个对象**没有 LIMIT 1**；一旦重名会抛 `TooManyResultsException` | 显式列名 + `limit 1`；返回 `List` 再取首个更稳妥；根本上靠唯一索引 |
| 9 | `src/main/java/com/example/demo/Entity/Student.java:22-23` | `createTime` / `updateTime` **无任何自动填充**（无 `@TableField(fill=...)`、无 `MetaObjectHandler`） | 加自动填充处理器，或在 SQL/DB 默认值层兜底；否则入库即为 null |
| 10 | `src/main/java/com/example/demo/Controller/StudentController.java:29-30` | 密码加密发生在 Controller，属于业务逻辑外泄；注册流程（查重、加密、入库）被拆在 Controller 与 Service 两处 | 全部下沉到 `StudentService.register`，Controller 只做参数接收与响应 |
| 11 | 全局 | 存了 BCrypt 密码，但**没有任何登录/校验接口**，也没有鉴权；查询接口对所有人开放 | 明确项目定位：若只是练习可接受；否则补登录（`passwordEncoder.matches`）、会话/JWT 与接口鉴权 |
| 12 | `src/main/java/com/example/demo/Service/ServiceImpl/StudentServiceImpl.java:40` | `insert` 返回值被忽略，未校验影响行数 | 判断 `rows > 0`，失败抛异常；注册成功建议返回新 id |

### P2 — 规范/可维护性

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 13 | `src/main/java/com/example/demo/` 各级包 | 包名大写：`Controller`、`DTO`、`Entity`、`Mapper`、`Service`、`Service.ServiceImpl`、`Exception`、`ExceptionHandler` | 按 Java 规范改为全小写（`controller`、`dto`、`entity`、`mapper`、`service`、`service.impl`、`exception`、`handler`）；`ServiceImpl` 作为包名也与类名重复 |
| 14 | `src/main/java/com/example/demo/Day1/Result.java:1` | 统一响应体放在 `Day1` 教学包中，生产 Controller 和异常处理器都依赖它 | 移到 `common`/`web`/`response` 包；`Day1` 目录整体清理或归档 |
| 15 | `src/main/java/com/example/demo/Day1/Result.java:10` | 字段名 `object`，且 `Result.error(400,...)` 与 `ResponseEntity.status(404)` 双轨并存 | 字段改 `data`；统一「HTTP 状态码 + 业务码」映射规则，避免 body.code 与 HTTP status 不一致 |
| 16 | `src/main/java/com/example/demo/Exception/StudentHasExisit.java:3` | 类名拼写错误：`Exisit` → `Exist`；且两个异常类无共同基类、无 `serialVersionUID` | 重命名 `StudentAlreadyExistsException`；抽 `BusinessException` 基类并携带错误码 |
| 17 | `src/main/java/com/example/demo/Controller/StudentController.java:26,38` | 方法名首字母大写：`RegisterStudent`、`SelectStudent` | 改为 `register`、`selectById`（小驼峰） |
| 18 | `src/main/java/com/example/demo/Controller/StudentController.java:20-23`、`StudentServiceImpl.java:13-14` | `@Autowired` 字段注入 | 改为构造器注入（可配合 `@RequiredArgsConstructor`），便于测试与发现循环依赖 |
| 19 | `src/main/java/com/example/demo/Service/ServiceImpl/StudentServiceImpl.java:10` | 使用全限定名 `@org.springframework.stereotype.Service` | 正常 `import` 后使用 `@Service` |
| 20 | `src/main/java/com/example/demo/DemoApplication.java:6` + `Mapper/StudentMapper.java:9` | `@MapperScan` 与接口上的 `@Mapper` 重复声明 | 保留一种即可 |
| 21 | `src/main/java/com/example/demo/Entity/Student.java:22-23` | 使用过时的 `java.util.Date` | 改用 `LocalDateTime`（配合 Jackson 时间格式） |
| 22 | `src/main/java/com/example/demo/DTO/StudentDTO.java:15,19` | 注册 DTO 里混入 `id`、`gender`（客户端可控），`age` 无任何校验 | 拆分 `RegisterRequest`（不含 id）与响应 `StudentVO`；`age` 加 `@Min/@Max`，`gender` 加 `@Min(0)@Max(1)` |

### P3 — 配置/工程化

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 23 | `src/main/resources/application.yaml:7` | JDBC URL 为 `useSSL=false`，且未加 `allowPublicKeyRetrieval=true`；MySQL 8 使用 `caching_sha2_password` 时极易报 `Public Key Retrieval is not allowed`（需确认） | 本地可加 `allowPublicKeyRetrieval=true`，或改用 `sslMode=DISABLED`；生产环境不要禁用 SSL |
| 24 | `src/test/java/com/example/demo/DemoApplicationTests.java` | 只有一个空的 `contextLoads`，无任何 Controller/Service/Mapper 测试；测试依赖真实 MySQL | 补单元测试（Mockito）与集成测试（Testcontainers/H2），至少覆盖注册、重复注册、查询不存在 |
| 25 | 全局 | 无建表 SQL（Flyway/Liquibase）、无 README 运行说明（`HELP.md` 是脚手架模板且被 gitignore） | 增加 schema 迁移脚本与 README（启动方式、环境变量、接口示例） |
| 26 | 全局 | 无日志（SLF4J）、无接口文档（springdoc）、无分页/列表/更新/删除接口 | 按需补齐；至少加关键业务日志与 springdoc |
| 27 | `src/main/java/com/example/demo/Entity/Student.java:16` | 主键无 `@TableId`；MyBatis-Plus 默认雪花算法生成 `Long` 型 id | 若数据库 `id` 是 `INT`，插入会溢出/类型不匹配（需确认表结构）；显式声明 `@TableId(type = IdType.AUTO)` 更直观 |
| 28 | `src/main/resources/application.yaml` | 无多环境 profile、无连接池/时区等显式配置 | 拆分 `application-dev.yaml`/`application-prod.yaml`；`serverTimezone` 在新驱动中已建议改用 `connectionTimeZone` |
| 29 | `target/` | 编译产物留在工程目录（虽已被 gitignore） | 清理；确认构建可复现 |

## 五、待确认问题（需项目负责人答复）

1. **数据库表结构**：`student` 表字段是否与实体一致？`id` 是 `BIGINT` 还是 `INT`？`name`/`phone` 上是否有唯一索引？
2. **项目定位**：这是练习项目还是要上线？若是练习，P0-3/P0-4 可以接受为「待补」；若要上线，鉴权与凭据管理必须补。
3. **是否需要登录功能**：目前只存密码、不校验，是否为下一步计划？
4. **运行环境**：是否必须 Java 25 + Spring Boot 4.1.1？Java 25 较新，Lombok/IDE 工具链兼容性需实测。
5. **`Day1` 目录**：是学习过程遗留还是需要保留？
6. **前端形态**：是否独立前端（需要 CORS 配置）？

## 六、建议修复顺序

1. 补 `@Valid` + 全局参数校验异常处理（#2），补 `@ExceptionHandler`（#1）；
2. 注册字段补全 + 业务下沉 + 唯一索引（#3、#7、#10）；
3. 查询接口改 VO，隐藏密码（#4）；
4. 配置修正：`server.port`、凭据外置、JDBC 参数（#5、#6、#23）；
5. 时间字段自动填充（#9）；
6. 统一包名/命名/响应体（#13–#22）；
7. 测试、迁移脚本与 README（#24、#25）。

## 七、构建验证记录（mvn compile）

实测编译曾失败，根因已定位并修复：

- **现象**：`mvn compile` 报大量「找不到符号」—— `getName()`、`setPassword()`、`builder()`、`getAge()` 等
  全是 Lombok 生成的成员缺失；`target/generated-sources/annotations` 为空，整个日志没有任何 Lombok 处理痕迹。
- **根因**：本机实际 JDK 为 **JDK 26**（`JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`），javac 命令带 `--release 25`、
  仅 `-classpath …lombok.jar`，**没有 `-processorpath`**。JDK 23 起 javac 不再从 `-classpath` 自动发现注解处理器
  （JDK 22 及以前会自动扫描 classpath 上的 `META-INF/services`），因此 Lombok 处理器被静默跳过、注解全部不生效。
  （用 `-processorpath` 显式指定后，JDK 26 + Lombok 1.18.46 下的最小样例编译通过，证明兼容性本身没问题。）
- **修复**：在 `pom.xml` 的 maven-compiler-plugin 中显式声明 `annotationProcessorPaths`，把 lombok 加入处理器路径。
- **结果**：修复后 `mvn compile` **BUILD SUCCESS**，13 个 class 全部生成，`javap` 确认 `builder()`/`getPassword()`/`setPhone()` 存在。
- 附带提醒：若换成常规的 stable JDK（如 25），同样建议保留该显式处理器声明，与 IDE 工具链行为一致、可移植性更好。
