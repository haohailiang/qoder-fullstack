package com.ecommerce.user.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.user.config.properties.CaptchaProperties;
import com.ecommerce.user.dto.CaptchaResponse;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码服务：
 * - generate()：用纯 JDK AWT 绘制随机字符图片（干扰线数量、字符个数、图片尺寸均由
 *   {@link CaptchaProperties} 配置），输出 Base64 PNG Data URL；同时生成唯一 captchaKey，
 *   验证码原文与过期时间戳保存在内存 Map 中（{@code ConcurrentHashMap}，不落库，属于短时状态）。
 * - validate()：消耗式校验，用于登录 / 注册等最终校验场景；校验是否存在、是否过期、是否匹配，
 *   无论成功失败都会移除该 key（一次性使用规则，同一个 key 不能复用）。
 * - verify()：非消耗式校验，用于发送短信等前置校验场景；不判断是否过期，校验通过后保留该 key，
 *   便于用户沿用同一张图片验证码完成后续登录 / 注册；校验失败仍会移除该 key。
 *   captcha.enabled=false 时跳过校验（便于本地调试 / 自动化测试关闭该功能）。
 * - 生成新验证码前顺带清理已过期条目，避免内存无限增长（无需引入定时任务）。
 */
@Service
public class CaptchaService {

    /** 排除容易混淆的字符（I/O/0/1），降低用户识别成本 */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final CaptchaProperties properties;
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(CaptchaProperties properties) {
        this.properties = properties;
    }

    /** 生成一张新的图片验证码，返回 captchaKey 与 Base64 图片 */
    public CaptchaResponse generate() {
        evictExpired();
        String code = randomCode();
        String key = UUID.randomUUID().toString();
        long expireAt = System.currentTimeMillis() + properties.getExpireSeconds() * 1000L;
        store.put(key, new CaptchaEntry(code, expireAt));
        return new CaptchaResponse(key, renderImage(code));
    }

    /**
     * 消耗式校验（登录 / 注册等最终校验场景）：
     * 不存在 / 已过期 / 不匹配均视为失败并抛出统一业务异常；
     * 校验成功或失败都会立即移除该 key，保证一次性使用（失败后前端需重新获取验证码）。
     */
    public void validate(String key, String value) {
        check(key, value, true, false);
    }

    /**
     * 非消耗式校验（发送短信等前置校验场景）：
     * 只看 key 是否存在、内容是否匹配，**不判断是否过期**（用户填完信息再点发送时可能已超过有效期）；
     * 校验通过后保留该 key，使用户可沿用同一张图片验证码继续登录 / 注册；
     * 校验失败仍会移除该 key，避免同一 key 被反复尝试。
     */
    public void verify(String key, String value) {
        check(key, value, false, true);
    }

    private void check(String key, String value, boolean consume, boolean ignoreExpiry) {
        if (!properties.isEnabled()) {
            return;
        }
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }
        CaptchaEntry entry = store.get(key);
        boolean matched = entry != null
                && (ignoreExpiry || !entry.isExpired())
                && entry.code().equalsIgnoreCase(value.trim());
        // 消耗场景（登录 / 注册）或校验失败：立即失效；非消耗场景校验通过：保留供后续使用
        if (consume || !matched) {
            store.remove(key);
        }
        if (!matched) {
            throw new BusinessException(400, "验证码错误或已过期，请重新获取");
        }
    }

    /** 便于同包单元测试直接构造指定有效期 / 内容的验证码条目（不对外提供 HTTP 入口调用） */
    void saveForTest(String key, String code, long expireAt) {
        store.put(key, new CaptchaEntry(code, expireAt));
    }

    private String randomCode() {
        int length = properties.getCodeLength();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    private String renderImage(String code) {
        int width = properties.getWidth();
        int height = properties.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // 干扰线
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < properties.getLineCount(); i++) {
            g.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }

        // 逐字符绘制，带轻微随机倾斜与颜色变化
        g.setFont(new Font("Arial", Font.BOLD, Math.max(12, height * 3 / 4)));
        int charWidth = Math.max(1, (width - 20) / code.length());
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120)));
            double angle = (random.nextDouble() - 0.5) * 0.4;
            int x = 10 + i * charWidth;
            int y = height / 2 + random.nextInt(6);
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(500, "验证码图片生成失败");
        }
    }

    private void evictExpired() {
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private record CaptchaEntry(String code, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
