package com.pulsepass.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<TicketOrder> {
    @Select("SELECT COUNT(*) FROM ticket_order WHERE user_id=#{userId} AND activity_id=#{activityId} AND status IN ('PENDING','CONFIRMED')")
    int countActive(@Param("userId") long userId, @Param("activityId") long activityId);
    @Select("SELECT id FROM ticket_order WHERE user_id=#{userId} AND activity_id=#{activityId} AND active_order_marker=1 LIMIT 1 FOR UPDATE")
    Long findActiveForUpdate(@Param("userId") long userId, @Param("activityId") long activityId);
}
