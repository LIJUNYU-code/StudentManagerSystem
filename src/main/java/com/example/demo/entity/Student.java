package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生表实体（对应 student 表）。
 * 注意：password 是 BCrypt 哈希，任何接口都不允许把它返回给前端，
 * 这里用 @JsonIgnore 在序列化层面兜底。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Student {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号，唯一，学生登录身份（格式 S + 9位数字） */
    private String studentNo;

    /** 班级 id，逻辑关联 clazz.id（不建物理外键） */
    private Long clazzId;

    /** 姓名，允许重名 */
    private String name;

    /** 性别：0-男 1-女 null-未填写 */
    private Integer gender;

    /** 年龄 */
    private Integer age;

    /** 手机号（不唯一：允许家庭共用） */
    private String phone;

    /** BCrypt 密码哈希，绝不外发 */
    @JsonIgnore
    private String password;

    /** 逻辑删除：0-正常 1-已删除。查询/更新由 MyBatis-Plus 自动追加 deleted=0 条件 */
    @TableLogic
    @JsonIgnore
    private Integer deleted;

    /** 创建时间，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
