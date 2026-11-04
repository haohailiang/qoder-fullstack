package com.example.demo.service;

import com.example.demo.entity.User;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    List<User> listUsers();

    User getUser(Long id);

    User createUser(User user);

    User updateUser(Long id, User user);

    void deleteUser(Long id);
}
