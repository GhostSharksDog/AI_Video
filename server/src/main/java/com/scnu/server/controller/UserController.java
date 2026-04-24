package com.scnu.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scnu.server.entity.User;
import com.scnu.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class UserController {

    @Autowired(required = false)
    private UserMapper userMapper;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userMapper == null) {
                throw new RuntimeException("UserMapper is not injected");
            }

            if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()
                    || user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                result.put("code", 400);
                result.put("msg", "username/password is required");
                return result;
            }

            String username = user.getUsername().trim();
            QueryWrapper<User> query = new QueryWrapper<>();
            query.eq("username", username);

            if (userMapper.selectCount(query) > 0) {
                result.put("code", 400);
                result.put("msg", "用户已存在");
                return result;
            }

            if (user.getNickname() == null || user.getNickname().isEmpty()) {
                user.setNickname("user" + System.currentTimeMillis());
            }

            user.setUsername(username);
            user.setRole("USER");
            userMapper.insert(user);

            result.put("code", 200);
            result.put("msg", "注册成功");
            result.put("data", user);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务端错误: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User loginUser) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (loginUser == null || loginUser.getUsername() == null || loginUser.getUsername().trim().isEmpty()
                    || loginUser.getPassword() == null || loginUser.getPassword().trim().isEmpty()) {
                result.put("code", 400);
                result.put("msg", "username/password is required");
                return result;
            }

            QueryWrapper<User> query = new QueryWrapper<>();
            query.eq("username", loginUser.getUsername().trim());
            query.eq("password", loginUser.getPassword());

            User dbUser = userMapper.selectOne(query);
            if (dbUser == null) {
                result.put("code", 401);
                result.put("msg", "用户名或密码错误");
            } else {
                result.put("code", 200);
                result.put("msg", "登录成功");
                result.put("token", "user_" + dbUser.getId());
                result.put("userInfo", dbUser);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "登录报错: " + e.getMessage());
        }
        return result;
    }
}
