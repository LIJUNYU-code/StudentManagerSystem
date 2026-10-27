package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;

    private String role;

    private Integer status; //0待审核 → 1正常 → 2禁用

    /**
     * 最近一次改密时间（阶段8：改密强制下线）。
     * 过滤器比较 token 的 iat 与本列：iat 早于改密时间的 token 一律 401。
     * NULL = 从未改密（此时不做 iat 比较）。
     */
    private LocalDateTime pwdChangeTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
    @TableField(fill =  FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill =  FieldFill.INSERT)
    private LocalDateTime updateTime;





}
