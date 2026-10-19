package com.example.demo.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 分页结果统一包装。
 * <p>
 * 为什么不直接返回 MyBatis-Plus 的 Page 对象：
 * Page 序列化后自带 orders / optimizeCountSql / searchCount 等十几个框架内部字段，
 * 前端真正需要的只有“总数 + 当前页数据”。字段越少，接口契约越稳定，
 * 将来换分页实现（比如改游标分页）前端也不用改。
 */
public class PageResult<T> {

    /** 满足条件的总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    /** 当前页码（从 1 开始） */
    private long page;

    /** 每页大小 */
    private long size;

    public PageResult(long total, List<T> records, long page, long size) {
        this.total = total;
        this.records = records;
        this.page = page;
        this.size = size;
    }

    /** 从 MyBatis-Plus 的 IPage 转换（元素已在 Service 层转成 VO） */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public long getTotal() {
        return total;
    }

    public List<T> getRecords() {
        return records;
    }

    // 以下两个 getter 加 @JsonProperty 只是为了 JSON 输出字段顺序稳定，便于阅读
    @JsonProperty
    public long getPage() {
        return page;
    }

    @JsonProperty
    public long getSize() {
        return size;
    }
}
