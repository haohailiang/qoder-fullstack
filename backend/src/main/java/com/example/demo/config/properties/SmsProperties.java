package com.example.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 云片网短信配置，对应 application.yml 中的 sms.* 配置项。
 * apiKey / accountSid 属于敏感配置，仓库中只保留占位符，本地开发按需替换，不要提交真实密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    /** 短信发送开关；false 时不调用真实云片网接口，仅打印日志（本地开发 / 自动化测试默认关闭，避免误发短信） */
    private boolean enabled = false;

    /** 云片网短信接口地址 */
    private String apiUrl = "https://sms.yunpian.com/v2/sms/tpl_send.json";

    /** 云片网账号 */
    private String accountSid;

    /** 云片网 API Key（敏感配置，占位符见 README） */
    private String apiKey;

    /** 短信模板 ID */
    private String templateId;

    /** 模板变量名称，用于拼装 tpl_value，默认云片网验证码模板常用变量名 #code# */
    private String templateCodeParamName = "#code#";

    /** 请求超时时间（毫秒），同时用于连接超时和读取超时 */
    private int timeoutMs = 5000;

    /** 最大重试次数（含首次请求），仅对网络异常、超时、5xx 可恢复错误生效 */
    private int maxRetry = 3;

    /** 重试基础间隔（毫秒） */
    private long retryIntervalMs = 1000L;

    /** 重试退避策略：linear（线性，间隔 = retryIntervalMs * 第几次重试）或 exponential（指数，间隔 = retryIntervalMs * 2^(第几次重试-1)） */
    private String retryBackoff = "linear";

    /** 同一手机号发送频率限制窗口（秒），窗口内不允许重复发送 */
    private int rateLimitSeconds = 60;
}
