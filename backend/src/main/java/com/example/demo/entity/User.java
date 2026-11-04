package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户实体
 */
@Data
public class User {

    private Long id;

    /** 登录认证标识（用户名），注册时必填，旧 CRUD 接口不强制 */
    private String username;

    /** BCrypt 加密后的密码；@JsonIgnore 保证任何接口响应都不返回密码 */
    @JsonIgnore
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Min(value = 0, message = "年龄不能小于 0")
    private Integer age;
}
