package org.zhanqi.qiproxy.server.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 验证码生成器
 */
public class CaptchaManager {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;

    private static final Map<String, String> captchaMap = new HashMap<>();
    private static final Random random = new Random();

    /**
     * 生成验证码图片
     */
    public static String[] generateCaptcha(String key) {
        // 生成随机验证码
        String code = generateCode();
        captchaMap.put(key, code);

        // 生成图片
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 白色背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘制干扰线
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 5; i++) {
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine(x1, y1, x2, y2);
        }

        // 绘制验证码
        g.setColor(Color.BLACK);
        Font font = new Font("Arial", Font.BOLD, 24);
        g.setFont(font);
        int x = 15;
        for (int i = 0; i < code.length(); i++) {
            g.drawString(String.valueOf(code.charAt(i)), x, 28);
            x += 25;
        }

        // 绘制噪点
        for (int i = 0; i < 30; i++) {
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.fillRect(x1, y1, 2, 2);
        }

        g.dispose();

        // 转换为base64
        String base64 = encodeToBase64(image);

        return new String[] { base64, key };
    }

    /**
     * 验证验证码
     */
    public static boolean verify(String key, String code) {
        String stored = captchaMap.remove(key);
        if (stored == null) {
            return false;
        }
        return stored.equalsIgnoreCase(code);
    }

    private static String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String encodeToBase64(BufferedImage image) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return Base64Encoder.encode(bytes);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Simple Base64 encoder for Java 7
     */
    private static class Base64Encoder {
        private static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        private static final int[] IA = new int[256];

        static {
            java.util.Arrays.fill(IA, -1);
            for (int i = 0, iS = CA.length; i < iS; i++) {
                IA[CA[i]] = i;
            }
            IA['='] = 0;
        }

        public static String encode(byte[] octets) {
            int len = octets.length;
            int se = (len % 3 != 0) ? 3 - (len % 3) : 0;
            int len3 = len + se;
            char[] ca = new char[len3 / 3 * 4];
            int a, b, c;
            for (int i = 0, j = 0; i < len3; i += 3) {
                a = (i < len) ? octets[i] : 0;
                b = (i + 1 < len) ? octets[i + 1] : 0;
                c = (i + 2 < len) ? octets[i + 2] : 0;
                int idx = (a & 0xFF) << 16 | (b & 0xFF) << 8 | (c & 0xFF);
                ca[j++] = CA[(idx >> 18) & 0x3F];
                ca[j++] = CA[(idx >> 12) & 0x3F];
                ca[j++] = (i + 1 < len) ? CA[(idx >> 6) & 0x3F] : '=';
                ca[j++] = (i + 2 < len) ? CA[idx & 0x3F] : '=';
            }
            return new String(ca);
        }
    }
}
