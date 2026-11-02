package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户审核列表查询参数（管理员）。
 * <p>
 * <b>page / pageSize 必须有默认值</b>：Controller 用 {@code @ModelAttribute} 绑定，
 * 客户端不传时字段就是 {@code null}，而 Service 里 {@code new Page<>(null, null)}
 * 会直接抛 NullPointerException → 500。
 * <p>
 * 这个问题在 2026-11-02 实测中暴露：{@code GET /admin/user}（不带任何参数）
 * 和只传 page 或只传 pageSize 都返回 500，只有两个都传才 200。
 * 其他 QueryDTO（Student/Course/Score/Enrollment/Leave/Clazz）都有默认值，
 * 唯独这个漏了——写代码时"照抄一个旧 DTO"漏掉默认值是很容易发生的事，
 * 而它只在"不传参数"时才炸，正常翻页测试永远撞不上。
 */
@Data
public class UserReviewQueryDTO {

    private String keyword;

    /** 状态筛选：0 待审核 / 1 正常 / 2 禁用，null = 全部 */
    private Integer status;

    @Min(value = 1, message = "页码最小数为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大数为100")
    private Integer pageSize = 10;
}
