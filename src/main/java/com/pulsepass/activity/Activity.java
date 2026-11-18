package com.pulsepass.activity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public String description;
    public LocalDateTime saleStartTime;
    public LocalDateTime saleEndTime;
    public LocalDateTime eventStartTime;
    public String status;
    public LocalDateTime createdAt;
}
