package com.ecommerce.user.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 集成测试：通过 @SpringBootTest + @AutoConfigureMockMvc
 * 验证 HTTP 请求/响应（状态码、返回格式），使用 @MockBean 模拟 Service 层依赖。
 * 注意：GlobalExceptionHandler 未标注 @ResponseStatus，HTTP 状态恒为 200，
 * 真正的错误码在 Result.body.code 中体现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";

    /**
     * 用户 CRUD 已标注 @PreAuthorize("hasRole('ADMIN')")：
     * 测试通过网关同款身份头（X-User-Role）由 TrustedAuthenticationFilter 转为 ROLE_ADMIN。
     */
    private static RequestPostProcessor asAdmin() {
        return request -> {
            request.addHeader(HEADER_USER_ID, "1");
            request.addHeader(HEADER_USER_NAME, "admin");
            request.addHeader("X-User-Role", "ADMIN");
            return request;
        };
    }

    /** 普通登录用户：用于验证管理接口对非 ADMIN 返回 403 */
    private static RequestPostProcessor asPlainUser() {
        return request -> {
            request.addHeader(HEADER_USER_ID, "100");
            request.addHeader(HEADER_USER_NAME, "zhangsan");
            request.addHeader("X-User-Role", "USER");
            return request;
        };
    }

    // ──────────────────── GET /users ────────────────────

    @Nested
    @DisplayName("GET /users - 查询用户列表")
    class ListUsers {

        @Test
        @DisplayName("正常场景：返回用户列表，HTTP 200 + Result 结构")
        void shouldReturnUserList() throws Exception {
            // Given
            User user = buildUser(1L, "zhangsan", "张三");
            when(userService.listUsers()).thenReturn(List.of(user));

            // When & Then
            mockMvc.perform(get("/users")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].username").value("zhangsan"));
        }

        @Test
        @DisplayName("空列表：返回空数组")
        void shouldReturnEmptyArray() throws Exception {
            when(userService.listUsers()).thenReturn(List.of());

            mockMvc.perform(get("/users")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 查询用户列表返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(get("/users")
                            .with(asPlainUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ──────────────────── GET /users/{id} ────────────────────

    @Nested
    @DisplayName("GET /users/{id} - 查询用户详情")
    class GetUser {

        @Test
        @DisplayName("正常场景：用户存在时返回详情")
        void shouldReturnUser() throws Exception {
            User user = buildUser(1L, "zhangsan", "张三");
            when(userService.getUser(1L)).thenReturn(user);

            mockMvc.perform(get("/users/1")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value("zhangsan"))
                    .andExpect(jsonPath("$.data.name").value("张三"));
        }

        @Test
        @DisplayName("异常场景：用户不存在返回 404 业务码")
        void shouldReturn404WhenNotFound() throws Exception {
            when(userService.getUser(999L))
                    .thenThrow(new BusinessException(404, "用户不存在: id=999"));

            mockMvc.perform(get("/users/999")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("用户不存在: id=999"));
        }
    }

    // ──────────────────── POST /users ────────────────────

    @Nested
    @DisplayName("POST /users - 新增用户")
    class CreateUser {

        @Test
        @DisplayName("正常场景：创建用户成功")
        void shouldCreateUser() throws Exception {
            User created = buildUser(10L, "wangwu", "王五");
            when(userService.createUser(any(User.class))).thenReturn(created);

            String body = """
                    {"username":"wangwu","name":"王五","email":"wangwu@example.com","phone":"13800000010","age":28}
                    """;

            mockMvc.perform(post("/users")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.username").value("wangwu"));
        }

        @Test
        @DisplayName("参数校验：姓名不能为空")
        void shouldFailWhenNameBlank() throws Exception {
            String body = """
                    {"username":"test","name":"","email":"test@example.com","phone":"13800000010","age":25}
                    """;

            mockMvc.perform(post("/users")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("姓名不能为空"));
        }

        @Test
        @DisplayName("参数校验：邮箱格式错误")
        void shouldFailWhenEmailInvalid() throws Exception {
            String body = """
                    {"username":"test","name":"测试","email":"not-email","phone":"13800000010","age":25}
                    """;

            mockMvc.perform(post("/users")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
        }

        @Test
        @DisplayName("参数校验：手机号格式错误")
        void shouldFailWhenPhoneInvalid() throws Exception {
            String body = """
                    {"username":"test","name":"测试","email":"test@example.com","phone":"12345","age":25}
                    """;

            mockMvc.perform(post("/users")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("手机号格式不正确"));
        }

        @Test
        @DisplayName("参数校验：年龄不能为负数")
        void shouldFailWhenAgeNegative() throws Exception {
            String body = """
                    {"username":"test","name":"测试","email":"test@example.com","phone":"13800000010","age":-1}
                    """;

            mockMvc.perform(post("/users")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("年龄不能小于 0"));
        }
    }

    // ──────────────────── PUT /users/{id} ────────────────────

    @Nested
    @DisplayName("PUT /users/{id} - 更新用户")
    class UpdateUser {

        @Test
        @DisplayName("正常场景：更新用户成功")
        void shouldUpdateUser() throws Exception {
            User updated = buildUser(1L, "zhangsan", "张三（更新）");
            when(userService.updateUser(eq(1L), any(User.class))).thenReturn(updated);

            String body = """
                    {"username":"zhangsan","name":"张三（更新）","email":"zhangsan@example.com","phone":"13800000001","age":26}
                    """;

            mockMvc.perform(put("/users/1")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("张三（更新）"));
        }
    }

    // ──────────────────── DELETE /users/{id} ────────────────────

    @Nested
    @DisplayName("DELETE /users/{id} - 删除用户")
    class DeleteUser {

        @Test
        @DisplayName("正常场景：删除成功")
        void shouldDeleteUser() throws Exception {
            doNothing().when(userService).deleteUser(1L);

            mockMvc.perform(delete("/users/1")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("权限场景：非 ADMIN 删除用户返回 403")
        void shouldReturn403WhenNotAdmin() throws Exception {
            mockMvc.perform(delete("/users/1")
                            .with(asPlainUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("异常场景：删除不存在的用户返回 404")
        void shouldReturn404WhenDeleteNotFound() throws Exception {
            doThrow(new BusinessException(404, "用户不存在: id=999"))
                    .when(userService).deleteUser(999L);

            mockMvc.perform(delete("/users/999")
                            .with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("用户不存在: id=999"));
        }
    }

    // ──────────────────── GET /users/theme ────────────────────

    @Nested
    @DisplayName("GET /users/theme - 获取主题偏好")
    class GetTheme {

        @Test
        @DisplayName("正常场景：返回用户主题偏好")
        void shouldReturnThemePreference() throws Exception {
            User user = buildUser(1L, "zhangsan", "张三");
            user.setThemePreference("light");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/users/theme")
                            .header(HEADER_USER_ID, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.theme").value("light"));
        }

        @Test
        @DisplayName("默认主题：未设置时返回 dark")
        void shouldReturnDarkAsDefault() throws Exception {
            User user = buildUser(1L, "zhangsan", "张三");
            user.setThemePreference(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/users/theme")
                            .header(HEADER_USER_ID, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.theme").value("dark"));
        }

        @Test
        @DisplayName("异常场景：缺少 X-User-Id 请求头返回 401")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/users/theme"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("未授权访问"));
        }
    }

    // ──────────────────── PUT /users/theme ────────────────────

    @Nested
    @DisplayName("PUT /users/theme - 更新主题偏好")
    class UpdateTheme {

        @Test
        @DisplayName("正常场景：更新主题为 light")
        void shouldUpdateThemeToLight() throws Exception {
            doNothing().when(userRepository).updateThemePreference(eq(1L), eq("light"));

            mockMvc.perform(put("/users/theme")
                            .header(HEADER_USER_ID, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"theme":"light"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：无效主题值返回 400")
        void shouldRejectInvalidTheme() throws Exception {
            mockMvc.perform(put("/users/theme")
                            .header(HEADER_USER_ID, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"theme":"invalid"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("主题值无效，仅支持 dark 或 light"));
        }
    }

    // ──────────────────── GET /users/language ────────────────────

    @Nested
    @DisplayName("GET /users/language - 获取语言偏好")
    class GetLanguage {

        @Test
        @DisplayName("正常场景：返回用户语言偏好")
        void shouldReturnLanguagePreference() throws Exception {
            User user = buildUser(1L, "zhangsan", "张三");
            user.setLanguagePreference("en");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/users/language")
                            .header(HEADER_USER_ID, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.language").value("en"));
        }

        @Test
        @DisplayName("默认语言：未设置时返回 zh")
        void shouldReturnZhAsDefault() throws Exception {
            User user = buildUser(1L, "zhangsan", "张三");
            user.setLanguagePreference(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/users/language")
                            .header(HEADER_USER_ID, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.language").value("zh"));
        }
    }

    // ──────────────────── PUT /users/language ────────────────────

    @Nested
    @DisplayName("PUT /users/language - 更新语言偏好")
    class UpdateLanguage {

        @Test
        @DisplayName("正常场景：更新语言为 en")
        void shouldUpdateLanguageToEn() throws Exception {
            doNothing().when(userRepository).updateLanguagePreference(eq(1L), eq("en"));

            mockMvc.perform(put("/users/language")
                            .header(HEADER_USER_ID, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"language":"en"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：无效语言值返回 400")
        void shouldRejectInvalidLanguage() throws Exception {
            mockMvc.perform(put("/users/language")
                            .header(HEADER_USER_ID, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"language":"fr"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("语言值无效，仅支持 zh 或 en"));
        }
    }

    // ──────────────────── helper ────────────────────

    private User buildUser(Long id, String username, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        user.setEmail(username + "@example.com");
        user.setPhone("1380000000" + id);
        user.setAge(25);
        return user;
    }
}
