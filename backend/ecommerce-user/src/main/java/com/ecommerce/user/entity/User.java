package com.ecommerce.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 * <p>
 * 对应 MySQL users 表（见 resources/db/schema.sql 与 mapper/UserMapper.xml）。
 * createdBy / createdDate / lastModifiedBy / lastModifiedDate 为审计字段，
 * 数据库采用下划线命名，通过 UserMapper.xml 的 resultMap 映射为下述驼峰字段；
 * 字段赋值统一在 UserRepository.save() 中自动完成（见该方法注释），业务代码无需手动设置。
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

    /** 手机号：注册、用户新增/编辑均必填，用于接收短信验证码 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Min(value = 0, message = "年龄不能小于 0")
    private Integer age;

    /**
     * 角色：USER（普通用户）/ ADMIN（管理员），未设置时由持久层默认为 USER。
     * 登录时写入 JWT 的 role claim，网关据此注入 X-User-Role，
     * 业务服务用 @PreAuthorize("hasRole('ADMIN')") 保护管理接口。
     */
    private String role;

    /** 创建人：当前登录用户标识，未登录或系统操作时为 "system"（新增时写入，之后不再变化） */
    private String createdBy;

    /** 创建时间（服务端时间，新增时写入） */
    private LocalDateTime createdDate;

    /** 最后修改人：当前登录用户标识，未登录或系统操作时为 "system"（每次新增/更新都会刷新） */
    private String lastModifiedBy;

    /** 最后修改时间（服务端时间，每次新增/更新都会刷新） */
    private LocalDateTime lastModifiedDate;

    /** 主题偏好：dark（暗黑模式）或 light（亮色模式），默认 dark */
    private String themePreference;

    /** 语言偏好：zh（中文）或 en（英文），默认 zh */
    private String languagePreference;
}
