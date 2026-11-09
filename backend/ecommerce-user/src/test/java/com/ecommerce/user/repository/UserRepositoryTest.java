package com.ecommerce.user.repository;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserRepository 单元测试：验证 MyBatis Mapper 调用、审计字段自动填充、
 * Optional 包装以及 SecurityContext 操作人解析等逻辑。
 */
@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 每个测试前清空 SecurityContext，避免用例间互相干扰
        SecurityContextHolder.clearContext();
    }

    // ──────────────────── findAll ────────────────────

    @Nested
    @DisplayName("findAll - 查询全部用户")
    class FindAll {

        @Test
        @DisplayName("正常场景：返回用户列表")
        void shouldReturnAllUsers() {
            // Given
            User user = buildUser(1L, "zhangsan");
            when(userMapper.selectAll()).thenReturn(List.of(user));

            // When
            List<User> result = userRepository.findAll();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("zhangsan");
        }

        @Test
        @DisplayName("空数据：无用户时返回空列表")
        void shouldReturnEmptyList() {
            when(userMapper.selectAll()).thenReturn(Collections.emptyList());
            assertThat(userRepository.findAll()).isEmpty();
        }
    }

    // ──────────────────── findById ────────────────────

    @Nested
    @DisplayName("findById - 按主键查询")
    class FindById {

        @Test
        @DisplayName("用户存在时返回 Optional 包装的用户对象")
        void shouldReturnOptionalWithUser() {
            User user = buildUser(1L, "zhangsan");
            when(userMapper.selectById(1L)).thenReturn(user);

            Optional<User> result = userRepository.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("zhangsan");
        }

        @Test
        @DisplayName("用户不存在时返回空 Optional")
        void shouldReturnEmptyOptional() {
            when(userMapper.selectById(999L)).thenReturn(null);

            Optional<User> result = userRepository.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    // ──────────────────── findByUsername ────────────────────

    @Nested
    @DisplayName("findByUsername - 按认证标识查询")
    class FindByUsername {

        @Test
        @DisplayName("用户名存在时返回 Optional 包装的用户对象")
        void shouldReturnUserByUsername() {
            User user = buildUser(1L, "zhangsan");
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            Optional<User> result = userRepository.findByUsername("zhangsan");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("用户名不存在时返回空 Optional")
        void shouldReturnEmptyWhenNotFound() {
            when(userMapper.selectByUsername("ghost")).thenReturn(null);

            Optional<User> result = userRepository.findByUsername("ghost");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("传入 null 时直接返回空 Optional，不查询数据库")
        void shouldReturnEmptyForNullUsername() {
            Optional<User> result = userRepository.findByUsername(null);

            assertThat(result).isEmpty();
            verify(userMapper, org.mockito.Mockito.never()).selectByUsername(any());
        }
    }

    // ──────────────────── existsByXxx ────────────────────

    @Nested
    @DisplayName("existsByXxx - 存在性检查")
    class ExistsChecks {

        @Test
        @DisplayName("existsByUsername：用户名已存在返回 true")
        void existsByUsernameTrue() {
            when(userMapper.countByUsername("zhangsan")).thenReturn(1);
            assertThat(userRepository.existsByUsername("zhangsan")).isTrue();
        }

        @Test
        @DisplayName("existsByUsername：用户名不存在返回 false")
        void existsByUsernameFalse() {
            when(userMapper.countByUsername("ghost")).thenReturn(0);
            assertThat(userRepository.existsByUsername("ghost")).isFalse();
        }

        @Test
        @DisplayName("existsById：ID 存在返回 true")
        void existsByIdTrue() {
            when(userMapper.countById(1L)).thenReturn(1);
            assertThat(userRepository.existsById(1L)).isTrue();
        }

        @Test
        @DisplayName("existsById：ID 不存在返回 false")
        void existsByIdFalse() {
            when(userMapper.countById(999L)).thenReturn(0);
            assertThat(userRepository.existsById(999L)).isFalse();
        }
    }

    // ──────────────────── save ────────────────────

    @Nested
    @DisplayName("save - 新增/更新用户（含审计字段自动填充）")
    class Save {

        @Test
        @DisplayName("新增用户：自动填充 createdBy / createdDate / lastModifiedBy / lastModifiedDate")
        void shouldFillAuditFieldsOnCreate() {
            // Given
            User newUser = new User();
            newUser.setUsername("wangwu");
            newUser.setName("王五");
            newUser.setEmail("wangwu@example.com");
            newUser.setPhone("13800000003");
            // id == null → 走新增分支

            when(userMapper.insert(any(User.class))).thenReturn(1);

            // When
            User saved = userRepository.save(newUser);

            // Then
            assertThat(saved.getCreatedBy()).isEqualTo("system"); // 无 SecurityContext → system
            assertThat(saved.getCreatedDate()).isNotNull();
            assertThat(saved.getLastModifiedBy()).isEqualTo("system");
            assertThat(saved.getLastModifiedDate()).isNotNull();
            verify(userMapper).insert(newUser);
        }

        @Test
        @DisplayName("更新用户：保留 createdBy / createdDate，刷新 lastModifiedBy / lastModifiedDate")
        void shouldUpdateAuditFieldsOnUpdate() {
            // Given
            User existingUser = buildUser(1L, "zhangsan");
            existingUser.setCreatedBy("original_creator");
            existingUser.setLastModifiedBy("original_creator");

            when(userMapper.updateById(any(User.class))).thenReturn(1);

            // When
            User updated = userRepository.save(existingUser);

            // Then
            assertThat(updated.getCreatedBy()).isEqualTo("original_creator"); // 不覆盖
            assertThat(updated.getLastModifiedBy()).isEqualTo("system"); // 刷新
            assertThat(updated.getLastModifiedDate()).isNotNull();
            verify(userMapper).updateById(existingUser);
        }

        @Test
        @DisplayName("有 SecurityContext 时审计操作人取当前登录用户名")
        void shouldUseAuthenticatedUserAsOperator() {
            // Given：模拟已登录用户（必须用三参构造函数，否则 isAuthenticated() == false）
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null,
                            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setName("新用户");
            newUser.setEmail("new@example.com");
            newUser.setPhone("13800000099");

            when(userMapper.insert(any(User.class))).thenReturn(1);

            // When
            User saved = userRepository.save(newUser);

            // Then
            assertThat(saved.getCreatedBy()).isEqualTo("admin");
            assertThat(saved.getLastModifiedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("anonymousUser 登录时审计操作人回退为 system")
        void shouldFallbackToSystemForAnonymous() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null,
                            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            User newUser = new User();
            newUser.setUsername("anonuser");
            newUser.setName("匿名");
            newUser.setEmail("anon@example.com");
            newUser.setPhone("13800000098");

            when(userMapper.insert(any(User.class))).thenReturn(1);

            User saved = userRepository.save(newUser);

            assertThat(saved.getCreatedBy()).isEqualTo("system");
        }
    }

    // ──────────────────── deleteById / updateXxxPreference ────────────────────

    @Nested
    @DisplayName("deleteById / updatePreference - 删除与偏好更新")
    class DeleteAndPreference {

        @Test
        @DisplayName("deleteById：委托 Mapper 执行删除")
        void shouldDelegateDeleteToMapper() {
            userRepository.deleteById(1L);
            verify(userMapper).deleteById(1L);
        }

        @Test
        @DisplayName("updateThemePreference：委托 Mapper 更新主题偏好")
        void shouldDelegateThemeUpdate() {
            userRepository.updateThemePreference(1L, "dark");
            verify(userMapper).updateThemePreference(1L, "dark");
        }

        @Test
        @DisplayName("updateLanguagePreference：委托 Mapper 更新语言偏好")
        void shouldDelegateLanguageUpdate() {
            userRepository.updateLanguagePreference(1L, "en");
            verify(userMapper).updateLanguagePreference(1L, "en");
        }
    }

    // ──────────────────── helper ────────────────────

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(username);
        user.setEmail(username + "@example.com");
        user.setPhone("1380000000" + id);
        user.setAge(25);
        return user;
    }
}
