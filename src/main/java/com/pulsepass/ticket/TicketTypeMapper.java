package com.pulsepass.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketTypeMapper extends BaseMapper<TicketType> {
    @Select("""
        SELECT t.id AS ticket_type_id, t.activity_id, t.name, t.price_cents, t.status,
               i.total_stock, i.available_stock
        FROM ticket_type t JOIN inventory i ON i.ticket_type_id = t.id
        WHERE t.activity_id = #{activityId} ORDER BY t.id ASC
        """)
    List<TicketTypeResponse> findWithInventory(@Param("activityId") long activityId);
}
