package com.scnu.server.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO) //指定为主键、自增
    private Long id;
    private String username;

    private String password;

    private String nickname; //昵称

    private String avatar;   //头像

    private String role;     //角色
}
