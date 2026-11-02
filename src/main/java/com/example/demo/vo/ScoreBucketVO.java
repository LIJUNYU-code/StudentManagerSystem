package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分数段分布的一项（柱状图的一根柱子）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBucketVO {

    /** 分数段标签，如 "0-59"、"60-69" */
    private String bucket;

    /** 该分数段的成绩条数 */
    private Long count;
}
