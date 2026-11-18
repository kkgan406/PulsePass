package com.pulsepass.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("inventory")
public class Inventory {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long ticketTypeId;
    public Integer totalStock;
    public Integer availableStock;
}
