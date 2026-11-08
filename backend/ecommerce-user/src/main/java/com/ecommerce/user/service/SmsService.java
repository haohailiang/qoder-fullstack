package com.ecommerce.user.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.config.properties.SmsProperties;
import com.ecommerce.user.dto.SmsRecord;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 云片网短信发送服务封装：
 * - apiKey / accountSid 等敏感配置只在本服务内部读取并使用，不随接口响应 / 异常信息暴露给前端
 * - sms.enabled=false 时不调用真实云片网接口，仅打印日志（本地开发、自动化测试默认关闭，避免误发短信），
 *   但 6 位验证码仍会生成并存储在内存中（key = 手机号），供登录 / 注册接口本地联调校验
 * - 内置验证码存储与校验：{@link #sendVerifyCode(String)} 生成并存储验证码，
 *   {@link #validateCode(String, String)} 校验（有效期 {@code sms.code-expire-seconds} 秒、一次性使用），
 *   校验逻辑与图片验证码一致（无论成功失败都消费同一 key）
 * - 内置同一手机号发送频率限制（rateLimitSeconds 秒内不允许重复发送，避免短信滥发）
 * - 内置重试机制（maxRetry 次，仅对网络异常 / 超时 / 5xx 可恢复错误重试，
 *   4xx 客户端错误（如 apiKey 错误、参数非法）不重试直接失败；重试间隔支持线性或指数退避）
 * - 发送失败统一转换为 {@link BusinessException}（code=502），失败信息清晰但不泄露敏感配置
 * - 每次实际发送（成功或失败）都会记录一条 {@link SmsRecord}（手机号 / 验证码 / 时间 / 状态），
 *   保存在进程内内存（{@link ConcurrentLinkedDeque}，容量有上限，重启即清空），供用户管理页查询
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /** 短信验证码位数 */
    private static final int CODE_LENGTH = 6;

    /** 内存发送记录的最大保留条数，超出后淘汰最旧记录，避免进程内内存无限增长 */
    private static final int MAX_RECORDS = 500;

    /** 发送状态：成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 发送状态：失败 */
    private static final String STATUS_FAILED = "FAILED";

    private final SmsProperties properties;
    private final RestTemplate smsRestTemplate;
    private final SecureRandom random = new SecureRandom();

    /** 记录同一手机号最近一次发送时间戳（毫秒），用于频率控制；进程内短时状态，不落库 */
    private final Map<String, Long> lastSendTimeStore = new ConcurrentHashMap<>();

    /** 记录手机号 -> 当前有效验证码（含过期时间戳）；进程内短时状态，一次性使用 */
    private final Map<String, SmsCodeEntry> verifyCodeStore = new ConcurrentHashMap<>();

    /** 短信发送记录，队首为最新；进程内短时状态，不落库 */
    private final ConcurrentLinkedDeque<SmsRecord> records = new ConcurrentLinkedDeque<>();

    public SmsService(SmsProperties properties, RestTemplate smsRestTemplate) {
        this.properties = properties;
        this.smsRestTemplate = smsRestTemplate;
    }

    /** 生成 6 位数字验证码并发送到指定手机号 */
    public void sendVerifyCode(String phone) {
        send(phone, randomNumericCode(CODE_LENGTH));
    }

    /**
     * 发送短信：先做频率控制（超频抛 429，此时不生成验证码也不记录），
     * 未超频则存储验证码并调用云片网接口（含重试）；无论真实发送成功与否都会记录一条发送记录。
     * <p>
     * sms.enabled=false 时不触达真实接口，但仍存储验证码并记为成功（便于本地联调查看明文验证码）。
     */
    public void send(String phone, String code) {
        checkRateLimit(phone);

        // 验证码先入内存存储，供登录 / 注册校验；即便 sms.enabled=false 也生成，方便本地联调
        log.info("[SMS] 生成验证码: phone={}, code={}", phone, code);
        storeVerifyCode(phone, code);

        if (!properties.isEnabled()) {
            log.info("[SMS] 发送开关未开启（sms.enabled=false），跳过真实云片网调用: phone={}", phone);
            record(phone, code, STATUS_SUCCESS);
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
                record(phone, code, STATUS_SUCCESS);
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
                record(phone, code, STATUS_FAILED);
                throw new BusinessException(502, "短信发送失败，请稍后重试");
            }
        }

        log.error("[SMS] 重试 {} 次后仍然失败: phone={}", maxRetry, phone, lastError);
        record(phone, code, STATUS_FAILED);
        throw new BusinessException(502, "短信发送失败，请稍后重试");
    }

    /**
     * 校验短信验证码：不存在 / 已过期 / 已被使用 / 不匹配均视为失败，抛出统一业务异常；
     * 与图片验证码一致，无论成功失败都会立即移除该手机号的验证码，保证一次性使用。
     * 即使 sms.enabled=false 也会校验（验证码仍会生成存储）。
     */
    public void validateCode(String phone, String code) {
        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            throw new BusinessException(400, "请输入短信验证码");
        }
        SmsCodeEntry entry = verifyCodeStore.remove(phone);
        if (entry == null || entry.isExpired() || !entry.code().equals(code.trim())) {
            throw new BusinessException(400, "短信验证码错误或已过期，请重新获取");
        }
    }

    /** 返回全部短信发送记录，按发送时间倒序（最新在前） */
    public List<SmsRecord> getRecords() {
        return new ArrayList<>(records);
    }

    /** 便于同包单元测试直接构造指定手机号 / 内容 / 过期时间的验证码条目（不对外提供 HTTP 入口调用） */
    void saveCodeForTest(String phone, String code, long expireAt) {
        verifyCodeStore.put(phone, new SmsCodeEntry(code, expireAt));
    }

    private void storeVerifyCode(String phone, String code) {
        long expireAt = System.currentTimeMillis() + properties.getCodeExpireSeconds() * 1000L;
        verifyCodeStore.put(phone, new SmsCodeEntry(code, expireAt));
    }

    private void record(String phone, String code, String status) {
        records.addFirst(new SmsRecord(phone, code, LocalDateTime.now(), status));
        while (records.size() > MAX_RECORDS) {
            records.pollLast();
        }
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

    private record SmsCodeEntry(String code, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
