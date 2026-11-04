package com.example.demo.controller;

import com.example.demo.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过真实 /auth 接口验证异常统一处理：
 * - 业务异常（认证标识已存在 409、确认密码不一致 400）
 * - 参数校验异常（@Valid 触发 MethodArgumentNotValidException → code 400，
 *   注册/登录新增的 phone、captchaKey、captchaValue 字段校验失败也走这一分支）
 * - 登录凭证错误（BusinessException 401）
 *
 * register / login / captcha 均为 permitAll，且请求不携带 Authorization 头，
 * JwtAuthenticationFilter 会直接放行，因此可命中控制器 + GlobalExceptionHandler。
 * 验证码校验本身由 CaptchaServiceTest 覆盖，这里通过 @MockBean 关闭校验逻辑
 * （stub validate 为无操作），使断言聚焦于注册 / 登录自身的参数与业务异常。
 * 注意：GlobalExceptionHandler 未标注 @ResponseStatus，处理后的 HTTP 状态恒为 200，
 * 真正的错误码在 Result.body.code 中体现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        // 关闭验证码校验，聚焦注册 / 登录参数校验与业务异常本身
        doNothing().when(captchaService).validate(any(), any());
    }

    /**
     * 构造注册请求体并发起请求：除 username 外的其余字段（含 phone、captchaKey、captchaValue）
     * 均传入合法值，便于精确断言某一个校验分支。
     */
    private ResultActions registerJson(String username, String password, String confirm,
                                       String name, String email, String phone, String age) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\","
                        + "\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"age\":%s,"
                        + "\"captchaKey\":\"test-captcha-key\",\"captchaValue\":\"test-captcha-value\"}",
                username, password, confirm, name, email, phone, age);
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 构造登录请求体（附带合法验证码占位值）并发起请求 */
    private ResultActions loginJson(String username, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\","
                        + "\"captchaKey\":\"test-captcha-key\",\"captchaValue\":\"test-captcha-value\"}",
                username, password);
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("业务异常：认证标识已存在返回 409")
    void registerDuplicateUsername() throws Exception {
        // zhangsan 为 MySQL 演示数据初始化预置账号
        registerJson("zhangsan", "123456", "123456", "张三", "zhangsan@example.com", "13800000001", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("认证标识已存在: zhangsan"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("业务异常：确认密码与密码不一致返回 400")
    void registerConfirmPasswordMismatch() throws Exception {
        registerJson("brandnew_user", "123456", "654321", "新人", "new@example.com", "13800000003", "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("确认密码与密码不一致"));
    }

    @Test
    @DisplayName("参数校验：username 为空（同时违反 @NotBlank 与 @Size，命中 400）")
    void registerUsernameBlank() throws Exception {
        // 空字符串同时触发 @NotBlank 与 @Size(min=3)，findFirst 返回顺序不保证，
        // 故只断言触发了参数校验（code=400）且为认证标识相关提示
        registerJson("", "123456", "123456", "张三", "zhangsan2@example.com", "13800000004", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.equalTo("认证标识（用户名）不能为空"),
                                org.hamcrest.Matchers.equalTo("认证标识长度需在 3-32 个字符之间"))));
    }

    @Test
    @DisplayName("参数校验：密码长度不合法（小于 6）")
    void registerPasswordTooShort() throws Exception {
        registerJson("valid_user_01", "123", "123", "张三", "zhangsan3@example.com", "13800000005", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度需在 6-64 个字符之间"));
    }

    @Test
    @DisplayName("参数校验：确认密码为空")
    void registerConfirmPasswordBlank() throws Exception {
        registerJson("valid_user_02", "123456", "", "张三", "zhangsan4@example.com", "13800000006", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("确认密码不能为空"));
    }

    @Test
    @DisplayName("参数校验：邮箱格式错误")
    void registerEmailInvalid() throws Exception {
        registerJson("valid_user_03", "123456", "123456", "张三", "not-an-email", "13800000007", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }

    @Test
    @DisplayName("参数校验：手机号格式错误")
    void registerPhoneInvalid() throws Exception {
        registerJson("valid_user_06", "123456", "123456", "张三", "zhangsan7@example.com", "12345", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号格式不正确"));
    }

    @Test
    @DisplayName("参数校验：年龄小于 0")
    void registerAgeNegative() throws Exception {
        registerJson("valid_user_04", "123456", "123456", "张三", "zhangsan5@example.com", "13800000008", "-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("年龄不能小于 0"));
    }

    @Test
    @DisplayName("参数校验：姓名缺失")
    void registerNameBlank() throws Exception {
        registerJson("valid_user_05", "123456", "123456", "", "zhangsan6@example.com", "13800000009", "25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("姓名不能为空"));
    }

    @Test
    @DisplayName("认证异常：登录时认证标识不存在返回 401")
    void loginUnknownUsername() throws Exception {
        loginJson("no_such_user", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误：认证标识不存在"));
    }

    @Test
    @DisplayName("认证异常：登录时密码错误返回 401")
    void loginWrongPassword() throws Exception {
        loginJson("zhangsan", "wrongpwd")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误：密码不正确"));
    }

    @Test
    @DisplayName("参数校验：登录 username 为空")
    void loginUsernameBlank() throws Exception {
        loginJson("", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("认证标识（用户名）不能为空"));
    }
}
