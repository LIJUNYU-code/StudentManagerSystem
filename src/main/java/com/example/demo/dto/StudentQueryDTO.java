package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 学生分页查询条件（GET 查询用 @ModelAttribute 绑定，不用 @RequestBody）。
 * GET + 查询参数的接口天然可收藏、可缓存、可重试，符合 REST 语义。
 */
public class StudentQueryDTO {

    /** 关键词：匹配姓名或学号（模糊查询，可为空=不过滤） */
    private String keyword;

    /** 班级过滤（可为空） */
    private Long clazzId;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /** 每页条数，上限 100，防止一次拉全表 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getClazzId() {
        return clazzId;
    }

    public void setClazzId(Long clazzId) {
        this.clazzId = clazzId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
