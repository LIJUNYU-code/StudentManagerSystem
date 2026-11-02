package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请假申请 Mapper。
 * <p>
 * 全部用 MyBatis-Plus 的 Wrapper 查询即可，没有复杂聚合，不需要手写 SQL。
 * 这一点和 StatsMapper / AttendanceMapper 不同——那两个的 LEFT JOIN 和
 * CASE WHEN 聚合在 Wrapper 里写不出来（或者写出来极难读）。
 */
@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
}
