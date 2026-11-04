package com.example.demo.controller;

import com.example.demo.dto.SmsRecord;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.service.SmsService;
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
 * 短信发送记录查询接口（GET /sms/records，需 JWT 认证）集成测试。
 * <p>
 * @MockBean 关闭真实 SmsService，聚焦接口的认证与响应结构：
 * - 无 Token：命中安全过滤链返回 HTTP 401（统一 Result 结构）
 * - 有效 Token：返回内存记录列表（手机号 / 验证码 / 时间 / 状态），按时间倒序
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
    @DisplayName("未认证：无 Token 访问 /sms/records 返回 HTTP 401")
    void recordsRequireAuth() throws Exception {
        mockMvc.perform(get("/sms/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("有效 Token：返回短信发送记录列表，字段与倒序一致")
    void recordsReturnListWithToken() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<SmsRecord> mockRecords = List.of(
                new SmsRecord("13800000002", "222222", now, "SUCCESS"),
                new SmsRecord("13800000001", "111111", now.minusSeconds(30), "FAILED"));
        when(smsService.getRecords()).thenReturn(mockRecords);

        String token = tokenProvider.generateToken("zhangsan", 1L);

        mockMvc.perform(get("/sms/records").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].phone").value("13800000002"))
                .andExpect(jsonPath("$.data[0].code").value("222222"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"));
    }
}
