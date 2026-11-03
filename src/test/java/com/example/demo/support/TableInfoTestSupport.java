package com.example.demo.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Clazz;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单测的 MyBatis-Plus 实体元数据初始化器。
 *
 * <h2>为什么需要它</h2>
 * MyBatis-Plus 把"实体 → 表名/列名"的映射关系（{@code TableInfo}）放在一个
 * <b>静态全局缓存</b>里，正常由 Spring 启动时扫描实体来填充。
 * 但 {@code @ExtendWith(MockitoExtension.class)} 的单元测试<b>没有 Spring 上下文</b>，
 * 缓存是空的；一旦被测 Service 里出现 {@code LambdaQueryWrapper}（如
 * {@code .eq(Score::getEnrollmentId, id)}），MyBatis-Plus 要把方法引用翻译成列名，
 * 找不到元数据就抛：
 * <pre>
 * com.baomidou.mybatisplus.core.exceptions.MybatisPlusException:
 *   can not find lambda cache for this entity [com.example.demo.entity.Score]
 * </pre>
 *
 * <h2>为什么问题会"本地绿、CI 红"（真实踩过的坑）</h2>
 * 这个缓存是<b>整个 JVM 共享</b>的。如果同一个 JVM 里先跑过任何一个
 * {@code @SpringBootTest}（例如 {@code DemoApplicationTests.contextLoads}），
 * Spring 会把全部实体元数据都初始化好，后面的纯 Mockito 测试就"搭便车"通过了。
 * 于是测试是否通过取决于<b>执行顺序</b>，这是典型的全局状态依赖 ——
 * 本地 Maven 与 GitHub Actions 的 surefire 执行顺序不同，才会一边绿一边红。
 * <p>
 * 解决办法就是：每个纯 Mockito 测试类都在 {@code @BeforeAll} 里<b>自己</b>把元数据
 * 注册好，不依赖别人先跑。注册是幂等的（已存在会直接返回），重复调用无害。
 *
 * <h2>怎么用</h2>
 * <pre>{@code
 * @BeforeAll
 * static void initTableInfo() {
 *     TableInfoTestSupport.initAll();
 * }
 * }</pre>
 * 必须 {@code @BeforeAll} + {@code static}：每个测试方法都初始化属于浪费
 * （虽然幂等，但没必要反复建 MapperBuilderAssistant）。
 */
public final class TableInfoTestSupport {

    private TableInfoTestSupport() {
    }

    /** 一次注册项目里全部实体的元数据，任何服务测试都能安全调用。 */
    public static void initAll() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");

        TableInfoHelper.initTableInfo(assistant, Student.class);
        TableInfoHelper.initTableInfo(assistant, Clazz.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
        TableInfoHelper.initTableInfo(assistant, Score.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Attendance.class);
        TableInfoHelper.initTableInfo(assistant, LeaveRequest.class);
    }
}
