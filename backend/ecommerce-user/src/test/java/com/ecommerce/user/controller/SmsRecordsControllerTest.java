package com.ecommerce.user.controller;

import com.ecommerce.user.dto.SmsRecord;
import com.ecommerce.user.security.JwtTokenProvider;
import com.ecommerce.user.service.SmsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 短信发送记录查询接口（GET /sms/records，需登录）集成测试。
 * <p>
 * @MockBean 关闭真实 SmsService，聚焦接口的认证与响应结构：
 * - 无任何身份头：方法级 @PreAuthorize 拒绝，按本项目的统一错误模型返回 code 403
 *   （浏览器侧看到的 401 / 40101 由网关先行返回，服务层只保证「无身份即拒绝」）
 * - 网关已注入身份：返回内存记录列表（手机号 / 验证码 / 时间 / 状态），按时间倒序
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmsRecordsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private SmsService smsService;

    @Test
    @DisplayName("未认证：无身份头访问 /sms/records 被方法级权限拦截")
    void recordsRequireAuth() throws Exception {
        mockMvc.perform(get("/sms/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("网关已注入身份：返回短信发送记录列表，字段与倒序一致")
    void recordsReturnListWithToken() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<SmsRecord> mockRecords = List.of(
                new SmsRecord("13800000002", "222222", now, "SUCCESS"),
                new SmsRecord("13800000001", "111111", now.minusSeconds(30), "FAILED"));
        when(smsService.getRecords()).thenReturn(mockRecords);

        String token = tokenProvider.generateToken("zhangsan", 1L);

        // 服务层不解析 JWT，登录态由网关校验后注入的 X-User-* 头体现（Bearer 头仅作请求上下文模拟）
        mockMvc.perform(get("/sms/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "1")
                        .header("X-User-Name", "zhangsan")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].phone").value("13800000002"))
                .andExpect(jsonPath("$.data[0].code").value("222222"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"));
    }
}
