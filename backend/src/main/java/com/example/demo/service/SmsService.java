package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.config.properties.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云片网短信发送服务封装：
 * - apiKey / accountSid 等敏感配置只在本服务内部读取并使用，不随接口响应 / 异常信息暴露给前端
 * - sms.enabled=false 时不调用真实云片网接口，仅打印日志（本地开发、自动化测试默认关闭，避免误发短信）
 * - 内置同一手机号发送频率限制（rateLimitSeconds 秒内不允许重复发送，避免短信滥发）
 * - 内置重试机制（maxRetry 次，仅对网络异常 / 超时 / 5xx 可恢复错误重试，
 *   4xx 客户端错误（如 apiKey 错误、参数非法）不重试直接失败；重试间隔支持线性或指数退避）
 * - 发送失败统一转换为 {@link BusinessException}（code=502），失败信息清晰但不泄露敏感配置
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /** 短信验证码位数 */
    private static final int CODE_LENGTH = 6;

    private final SmsProperties properties;
    private final RestTemplate smsRestTemplate;
    private final SecureRandom random = new SecureRandom();

    /** 记录同一手机号最近一次发送时间戳（毫秒），用于频率控制；进程内短时状态，不落库 */
    private final Map<String, Long> lastSendTimeStore = new ConcurrentHashMap<>();

    public SmsService(SmsProperties properties, RestTemplate smsRestTemplate) {
        this.properties = properties;
        this.smsRestTemplate = smsRestTemplate;
    }

    /** 生成 6 位数字验证码并发送到指定手机号 */
    public void sendVerifyCode(String phone) {
        send(phone, randomNumericCode(CODE_LENGTH));
    }

    /**
     * 发送短信：先做频率控制，未超频才调用云片网接口（含重试）。
     * 频率窗口内重复发送会抛出 code=429 的业务异常。
     */
    public void send(String phone, String code) {
        checkRateLimit(phone);

        if (!properties.isEnabled()) {
            log.info("[SMS] 发送开关未开启（sms.enabled=false），跳过真实云片网调用: phone={}", phone);
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile", phone);
        form.add("tpl_id", properties.getTemplateId());
        form.add("tpl_value", properties.getTemplateCodeParamName() + "=" + code);
        form.add("apikey", properties.getApiKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(form, headers);

        Exception lastError = null;
        int maxRetry = Math.max(1, properties.getMaxRetry());
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                String response = smsRestTemplate.postForObject(
                        properties.getApiUrl(), requestEntity, String.class);
                log.info("[SMS] 云片网接口调用成功: phone={}, attempt={}", phone, attempt);
                log.debug("[SMS] 云片网接口原始响应: {}", response);
                return;
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // 网络异常、超时、5xx 属于可恢复错误，按配置重试
                lastError = e;
                log.warn("[SMS] 第 {} 次调用云片网接口失败（可恢复错误）: {}", attempt, e.getMessage());
                if (attempt < maxRetry) {
                    sleepBeforeRetry(attempt);
                }
            } catch (Exception e) {
                // 4xx 等不可恢复错误（如 apiKey 错误、参数非法）不重试，直接失败
                log.error("[SMS] 调用云片网接口失败（不可恢复错误，不重试）", e);
                throw new BusinessException(502, "短信发送失败，请稍后重试");
            }
        }

        log.error("[SMS] 重试 {} 次后仍然失败: phone={}", maxRetry, phone, lastError);
        throw new BusinessException(502, "短信发送失败，请稍后重试");
    }

    private String randomNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 频率控制：同一手机号 rateLimitSeconds 秒内不允许重复发送。
     * 使用 compute 保证「检查 + 记录」的原子性，检查失败时不更新记录。
     */
    private void checkRateLimit(String phone) {
        long intervalMillis = properties.getRateLimitSeconds() * 1000L;
        long now = System.currentTimeMillis();
        lastSendTimeStore.compute(phone, (key, lastTime) -> {
            if (lastTime != null && now - lastTime < intervalMillis) {
                throw new BusinessException(429, "验证码发送过于频繁，请稍后再试");
            }
            return now;
        });
    }

    private void sleepBeforeRetry(int attempt) {
        long base = properties.getRetryIntervalMs();
        long sleepMillis = "exponential".equalsIgnoreCase(properties.getRetryBackoff())
                ? base * (1L << (attempt - 1))
                : base * attempt;
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
