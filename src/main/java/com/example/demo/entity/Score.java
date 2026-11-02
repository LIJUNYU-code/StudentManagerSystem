package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enrollmentId;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal value;

    private String examType;

    /**
     * 逻辑删除标志。
     * <p>
     * <b>为什么成绩必须是逻辑删除，不能像 enrollment 那样物理删除：</b>
     * 成绩是历史档案（"这个学生这门课考了多少分"），删掉就永久消失了；
     * 而 enrollment 是当前状态表（"我现在选不选这门课"），退课让它消失是合理的。
     * 两者性质不同，V6 对 enrollment 物理删除的决定不能照搬到 score 上。
     * <p>
     * <b>为什么这个字段在 2026-11-02 才补上：</b>
     * V2 建表语句里写了 deleted，但用的是 {@code CREATE TABLE IF NOT EXISTS}——
     * 表已存在时整条语句被跳过，于是凡是在 V2 之前手动建过 score 表的环境，
     * 这一列永远是缺的。而 StatsMapper/ScoreMapper 的手写 SQL 里写着
     * {@code s.deleted = 0}，MySQL 直接报 ERROR 1054，导致「成绩分析」页全部 500。
     * 补救脚本是 V13__score_deleted_restore.sql。
     * <p>
     * <b>@TableLogic 的作用范围：</b>只影响 MyBatis-Plus 自动生成的 SQL
     * （selectById / selectList / selectPage / deleteById 等），
     * 手写的 {@code @Select} 完全绕过这一层——所以 StatsMapper 里的
     * {@code s.deleted = 0} 必须手写，两条路各自负责，缺一不可。
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
