package org.zhanqi.qiproxy.server.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 操作日志记录器
 */
public class OperationLog {

    private static final String LOG_DIR;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    static {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isEmpty()) {
            userHome = "/tmp";
        }
        LOG_DIR = userHome + "/" + ".qiproxy/logs/";
        File dir = new File(LOG_DIR);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    /**
     * 记录操作日志（包含IP）
     */
    public static void log(final String username, final String ip, final String action, final String details) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String timestamp = DATE_FORMAT.format(new Date());
                String logFile = LOG_DIR + "operation-" + FILE_FORMAT.format(new Date()) + ".log";
                String ipInfo = (ip != null && !ip.isEmpty()) ? " [" + ip + "]" : "";
                String logLine = String.format("[%s]%s [%s] [%s] %s\n", timestamp, ipInfo, username, action, details);

                try {
                    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(logFile, true), Charset.forName("UTF-8"));
                    writer.write(logLine);
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 记录操作日志（不包含IP）
     */
    public static void log(String username, String action, String details) {
        log(username, null, action, details);
    }

    /**
     * 记录登录日志
     */
    public static void logLogin(String username, String ip, boolean success) {
        String action = "LOGIN";
        String details = success ? "登录成功" : "登录失败";
        log(username, ip, action, details);
    }

    /**
     * 记录配置变更
     */
    public static void logConfigChange(String username, String ip, String action, String details) {
        log(username, ip, "CONFIG_" + action.toUpperCase(), details);
    }

    /**
     * 记录用户管理操作
     */
    public static void logUserManagement(String operator, String ip, String action, String targetUser) {
        log(operator, ip, "USER_" + action.toUpperCase(), "目标用户: " + targetUser);
    }

    /**
     * 记录指标操作
     */
    public static void logMetrics(String username, String ip, String action) {
        log(username, ip, "METRICS_" + action.toUpperCase(), action);
    }

    /**
     * 记录端口分配操作
     */
    public static void logPortAllocate(String username, String ip, int port) {
        log(username, ip, "PORT_ALLOCATE", "分配端口: " + port);
    }
}
