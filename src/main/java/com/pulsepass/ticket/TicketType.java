package com.pulsepass.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ticket_type")
public class TicketType {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long activityId;
    public String name;
    public Long priceCents;
    public String status;
    public LocalDateTime createdAt;
}
