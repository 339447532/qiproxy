package org.zhanqi.qiproxy.server.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 日志服务 - 读取和解析操作日志
 */
public class LogService {

    private static final String LOG_DIR;
    private static final SimpleDateFormat FILE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isEmpty()) {
            userHome = "/tmp";
        }
        LOG_DIR = userHome + "/" + ".qiproxy/logs/";
    }

    /**
     * 日志条目
     */
    public static class LogEntry {
        private String time;
        private String username;
        private String ip;
        private String action;
        private String details;
        private long timestamp;

        public String getTime() { return time; }
        public String getUsername() { return username; }
        public String getIp() { return ip; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }

        public void setTime(String time) { this.time = time; }
        public void setUsername(String username) { this.username = username; }
        public void setIp(String ip) { this.ip = ip; }
        public void setAction(String action) { this.action = action; }
        public void setDetails(String details) { this.details = details; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 分页结果
     */
    public static class LogPageResult {
        private List<LogEntry> data;
        private int total;
        private int page;
        private int pageSize;

        public List<LogEntry> getData() { return data; }
        public int getTotal() { return total; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }

        public void setData(List<LogEntry> data) { this.data = data; }
        public void setTotal(int total) { this.total = total; }
        public void setPage(int page) { this.page = page; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    /**
     * 解析单行日志
     * 新格式: [2026-04-20 11:21:19] [192.168.1.1] [admin] [LOGIN] 登录成功
     * 旧格式: [2026-04-20 11:21:19] [admin] [LOGIN] 登录成功
     */
    private static LogEntry parseLogLine(String line) {
        LogEntry entry = new LogEntry();
        try {
            // 解析时间 [2026-04-20 11:21:19]
            int timeEnd = line.indexOf("]");
            String timeStr = line.substring(1, timeEnd);
            entry.setTime(timeStr);
            try {
                entry.setTimestamp(TIME_FORMAT.parse(timeStr).getTime());
            } catch (ParseException e) {
                entry.setTimestamp(0);
            }

            // 解析第二个字段，判断是IP还是用户名
            // 新格式: [IP] [username] [action] details
            // 旧格式: [username] [action] details
            int secondStart = timeEnd + 2; // skip "] "
            int secondBracketEnd = line.indexOf("]", secondStart);
            if (secondBracketEnd == -1) {
                throw new Exception("Invalid log format");
            }
            // secondField是[xxx]，需要去掉[和]
            String secondField = line.substring(secondStart + 1, secondBracketEnd);

            // 判断是否是IP地址（包含.或:）
            if (secondField.contains(".") || secondField.contains(":")) {
                // 是IP，下一个字段是用户名
                entry.setIp(secondField);
                int usernameStart = secondBracketEnd + 2;
                int usernameEnd = line.indexOf("]", usernameStart);
                entry.setUsername(line.substring(usernameStart + 1, usernameEnd));

                // 解析操作
                int actionStart = usernameEnd + 2;
                int actionEnd = line.indexOf("]", actionStart);
                entry.setAction(line.substring(actionStart + 1, actionEnd));

                // 解析详情
                entry.setDetails(line.substring(actionEnd + 2).trim());
            } else {
                // 不是IP（是用户名），没有IP信息
                entry.setIp("");
                entry.setUsername(secondField);

                // 解析操作
                int actionStart = secondBracketEnd + 2;
                int actionEnd = line.indexOf("]", actionStart);
                entry.setAction(line.substring(actionStart + 1, actionEnd));

                // 解析详情
                entry.setDetails(line.substring(actionEnd + 2).trim());
            }
        } catch (Exception e) {
            entry.setTime("");
            entry.setUsername("");
            entry.setIp("");
            entry.setAction("");
            entry.setDetails(line);
        }
        return entry;
    }

    /**
     * 获取过滤后的日志列表
     */
    public static List<LogEntry> getLogs(String date, String username, String startTime, String endTime) {
        List<LogEntry> allLogs = new ArrayList<LogEntry>();
        String logFile = LOG_DIR + "operation-" + date + ".log";
        File file = new File(logFile);
        if (!file.exists()) {
            return allLogs;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLogLine(line);
                allLogs.add(entry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 过滤
        List<LogEntry> filteredLogs = new ArrayList<LogEntry>();
        for (LogEntry entry : allLogs) {
            boolean match = true;

            // 用户名过滤
            if (username != null && !username.isEmpty()) {
                if (!entry.getUsername().toLowerCase().contains(username.toLowerCase())) {
                    match = false;
                }
            }

            // 开始时间过滤
            if (match && startTime != null && !startTime.isEmpty()) {
                try {
                    long start = TIME_FORMAT.parse(startTime).getTime();
                    if (entry.getTimestamp() < start) {
                        match = false;
                    }
                } catch (ParseException e) {
                    // 忽略
                }
            }

            // 结束时间过滤
            if (match && endTime != null && !endTime.isEmpty()) {
                try {
                    long end = TIME_FORMAT.parse(endTime).getTime();
                    if (entry.getTimestamp() > end) {
                        match = false;
                    }
                } catch (ParseException e) {
                    // 忽略
                }
            }

            if (match) {
                filteredLogs.add(entry);
            }
        }

        // 按时间倒序排列
        Collections.sort(filteredLogs, new Comparator<LogEntry>() {
            @Override
            public int compare(LogEntry o1, LogEntry o2) {
                return Long.compare(o2.getTimestamp(), o1.getTimestamp());
            }
        });

        return filteredLogs;
    }

    /**
     * 获取分页日志列表
     */
    public static LogPageResult getLogsPage(String date, String username, String startTime, String endTime, int page, int pageSize) {
        List<LogEntry> allLogs = getLogs(date, username, startTime, endTime);

        LogPageResult result = new LogPageResult();
        result.setTotal(allLogs.size());
        result.setPage(page);
        result.setPageSize(pageSize);

        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= allLogs.size()) {
            result.setData(new ArrayList<LogEntry>());
            return result;
        }

        int toIndex = Math.min(fromIndex + pageSize, allLogs.size());
        List<LogEntry> pageData = allLogs.subList(fromIndex, toIndex);
        result.setData(pageData);

        return result;
    }

    /**
     * 获取指定日期的原始日志列表（兼容旧接口）
     */
    public static List<String> getLogs(String date) {
        List<String> logs = new ArrayList<String>();
        String logFile = LOG_DIR + "operation-" + date + ".log";
        File file = new File(logFile);
        if (!file.exists()) {
            return logs;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                logs.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 按时间倒序排列
        Collections.sort(logs, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o2.compareTo(o1);
            }
        });

        return logs;
    }

    /**
     * 获取最近可用的日志日期列表
     */
    public static List<String> getAvailableDates() {
        List<String> dates = new ArrayList<String>();
        File dir = new File(LOG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return dates;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("operation-") && name.endsWith(".log")) {
                    String date = name.substring("operation-".length(), name.length() - ".log".length());
                    dates.add(date);
                }
            }
        }

        Collections.sort(dates, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o2.compareTo(o1);
            }
        });

        return dates;
    }

    /**
     * 清除指定日期的日志
     */
    public static boolean clearLogs(String date) {
        String logFile = LOG_DIR + "operation-" + date + ".log";
        File file = new File(logFile);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * 清除所有日志
     */
    public static int clearAllLogs() {
        int count = 0;
        File dir = new File(LOG_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("operation-") && name.endsWith(".log")) {
                    if (file.delete()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
