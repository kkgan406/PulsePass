package com.pulsepass.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
    @Update("UPDATE inventory SET available_stock = available_stock - 1 WHERE ticket_type_id = #{id} AND available_stock > 0")
    int decrementAvailable(@Param("id") long ticketTypeId);
}
