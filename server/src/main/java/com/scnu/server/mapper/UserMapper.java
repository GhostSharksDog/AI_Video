package com.scnu.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.scnu.server.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface UserMapper extends BaseMapper<User>{
    
}
