package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clazz {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String grade;

    private Integer teacherId;

    @TableLogic
    private Integer deleted;
    @TableField(fill =  FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill =  FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
