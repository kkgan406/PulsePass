package com.pulsepass.activity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Select("SELECT * FROM activity WHERE id = #{id} FOR UPDATE")
    Activity lockById(@Param("id") long id);

    @Select("SELECT * FROM activity ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Activity> findPage(@Param("size") int size, @Param("offset") long offset);
}
