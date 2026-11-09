package com.ecommerce.user.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试：覆盖用户 CRUD 全部业务分支，
 * 包括正常流程与用户不存在等异常边界场景。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("zhangsan");
        sampleUser.setName("张三");
        sampleUser.setEmail("zhangsan@example.com");
        sampleUser.setPhone("13800000001");
        sampleUser.setAge(25);
        sampleUser.setPassword("encoded-pwd");
    }

    // ──────────────────── listUsers ────────────────────

    @Nested
    @DisplayName("listUsers - 查询用户列表")
    class ListUsers {

        @Test
        @DisplayName("正常场景：返回全部用户列表")
        void shouldReturnAllUsers() {
            // Given
            User anotherUser = new User();
            anotherUser.setId(2L);
            anotherUser.setUsername("lisi");
            anotherUser.setName("李四");
            anotherUser.setEmail("lisi@example.com");
            anotherUser.setPhone("13800000002");
            anotherUser.setAge(30);

            when(userRepository.findAll()).thenReturn(List.of(sampleUser, anotherUser));

            // When
            List<User> result = userService.listUsers();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUsername()).isEqualTo("zhangsan");
            assertThat(result.get(1).getUsername()).isEqualTo("lisi");
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("空数据：数据库无用户时返回空列表")
        void shouldReturnEmptyListWhenNoUsers() {
            // Given
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<User> result = userService.listUsers();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ──────────────────── getUser ────────────────────

    @Nested
    @DisplayName("getUser - 根据 ID 查询用户")
    class GetUser {

        @Test
        @DisplayName("正常场景：用户存在时返回用户详情")
        void shouldReturnUserWhenExists() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // When
            User result = userService.getUser(1L);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("zhangsan");
            assertThat(result.getName()).isEqualTo("张三");
        }

        @Test
        @DisplayName("异常场景：用户不存在时抛出 404 业务异常")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getUser(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("用户不存在: id=999");
        }
    }

    // ──────────────────── createUser ────────────────────

    @Nested
    @DisplayName("createUser - 创建用户")
    class CreateUser {

        @Test
        @DisplayName("正常场景：忽略客户端传入的 ID，由仓储层自动生成")
        void shouldIgnoreClientIdAndCreate() {
            // Given
            User newUser = new User();
            newUser.setId(100L); // 客户端传入的 ID 应被忽略
            newUser.setUsername("wangwu");
            newUser.setName("王五");
            newUser.setEmail("wangwu@example.com");
            newUser.setPhone("13800000003");
            newUser.setAge(28);
        
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(10L); // 数据库自增回填
                return saved;
            });
        
            // When
            User result = userService.createUser(newUser);
        
            // Then
            assertThat(result.getId()).isEqualTo(10L);
            // createUser 内部会将 id 置空后再 save，save 的 mock 又回填了新 id
            verify(userRepository).save(newUser);
        }
    }

    // ──────────────────── updateUser ────────────────────

    @Nested
    @DisplayName("updateUser - 更新用户")
    class UpdateUser {

        @Test
        @DisplayName("正常场景：更新已有用户的全部字段")
        void shouldUpdateAllFields() {
            // Given
            User updatePayload = new User();
            updatePayload.setUsername("zhangsan_updated");
            updatePayload.setName("张三（更新）");
            updatePayload.setEmail("updated@example.com");
            updatePayload.setPhone("13900000001");
            updatePayload.setAge(26);
            updatePayload.setPassword("new-encoded-pwd");

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.updateUser(1L, updatePayload);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("zhangsan_updated");
            assertThat(result.getName()).isEqualTo("张三（更新）");
        }

        @Test
        @DisplayName("部分更新：username 为空时保留原值")
        void shouldKeepOriginalUsernameWhenBlank() {
            // Given
            User updatePayload = new User();
            updatePayload.setUsername(null);
            updatePayload.setPassword(null);
            updatePayload.setName("新名字");
            updatePayload.setEmail("new@example.com");
            updatePayload.setPhone("13900000001");

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.updateUser(1L, updatePayload);

            // Then
            assertThat(result.getUsername()).isEqualTo("zhangsan");
            assertThat(result.getPassword()).isEqualTo("encoded-pwd");
        }

        @Test
        @DisplayName("部分更新：username 为空白字符串时保留原值")
        void shouldKeepOriginalUsernameWhenWhitespace() {
            // Given
            User updatePayload = new User();
            updatePayload.setUsername("   ");
            updatePayload.setPassword("  ");
            updatePayload.setName("新名字");
            updatePayload.setEmail("new@example.com");
            updatePayload.setPhone("13900000001");

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.updateUser(1L, updatePayload);

            // Then
            assertThat(result.getUsername()).isEqualTo("zhangsan");
            assertThat(result.getPassword()).isEqualTo("encoded-pwd");
        }

        @Test
        @DisplayName("异常场景：用户不存在时抛出 404 业务异常")
        void shouldThrowWhenUserNotFoundForUpdate() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(999L, new User()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("用户不存在: id=999");
        }
    }

    // ──────────────────── deleteUser ────────────────────

    @Nested
    @DisplayName("deleteUser - 删除用户")
    class DeleteUser {

        @Test
        @DisplayName("正常场景：用户存在时执行删除")
        void shouldDeleteExistingUser() {
            // Given
            when(userRepository.existsById(1L)).thenReturn(true);
            doNothing().when(userRepository).deleteById(1L);

            // When
            userService.deleteUser(1L);

            // Then
            verify(userRepository).existsById(1L);
            verify(userRepository).deleteById(1L);
        }

        @Test
        @DisplayName("异常场景：用户不存在时抛出 404 业务异常，不执行删除")
        void shouldThrowWhenUserNotFoundForDelete() {
            // Given
            when(userRepository.existsById(999L)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> userService.deleteUser(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 404)
                    .hasMessage("用户不存在: id=999");

            verify(userRepository).existsById(eq(999L));
            verify(userRepository, org.mockito.Mockito.never()).deleteById(any());
        }
    }
}
