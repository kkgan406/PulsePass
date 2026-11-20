package com.pulsepass.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ticket_order")
public class TicketOrder {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long activityId;
    public Long ticketTypeId;
    public Integer quantity;
    public Long amountCents;
    public String status;
    public LocalDateTime createdAt;
}
