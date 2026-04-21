package org.zhanqi.qiproxy.server.user;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户管理服务 - SQLite 存储
 */
public class UserService {

    private static Logger logger = LoggerFactory.getLogger(UserService.class);

    private static final String DB_FILE;
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_PASSWORD = "admin";

    private static final String JDBC_URL;

    private static UserService instance;

    static {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isEmpty()) {
            userHome = "/tmp";
        }
        String dataPath = userHome + "/" + ".qiproxy/";
        DB_FILE = dataPath + "/users.db";
        JDBC_URL = "jdbc:sqlite:" + DB_FILE;

        // 显式注册 SQLite JDBC 驱动
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SQLite JDBC driver", e);
        }
    }

    private UserService() {
        initDatabase();
    }

    static {
        instance = new UserService();
    }

    public static UserService getInstance() {
        return instance;
    }

    /**
     * 初始化数据库
     */
    private void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建用户表
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "role TEXT NOT NULL DEFAULT 'user', " +
                    "create_time INTEGER NOT NULL" +
                    ")");

            // 检查是否需要创建默认管理员
            long userCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next()) {
                    userCount = rs.getInt(1);
                }
            }

            if (userCount == 0) {
                long now = System.currentTimeMillis();
                String encryptedPassword = encryptPassword(DEFAULT_PASSWORD);
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO users (username, password, role, create_time) VALUES (?, ?, ?, ?)")) {
                    pstmt.setString(1, DEFAULT_ADMIN);
                    pstmt.setString(2, encryptedPassword);
                    pstmt.setString(3, "admin");
                    pstmt.setLong(4, now);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * 获取数据库连接
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    /**
     * 检查是否有用户
     */
    private boolean hasUsers() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 验证用户密码
     */
    public User verifyUser(String username, String password) {
        String encryptedPassword = encryptPassword(password);
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, encryptedPassword);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setRole(rs.getString("role"));
                    user.setCreateTime(rs.getLong("create_time"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify user", e);
        }
        return null;
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY create_time";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                User user = new User();
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("password"));
                user.setRole(rs.getString("role"));
                user.setCreateTime(rs.getLong("create_time"));
                users.add(user);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all users", e);
        }
        return users;
    }

    /**
     * 获取用户
     */
    public User getUser(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setRole(rs.getString("role"));
                    user.setCreateTime(rs.getLong("create_time"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get user", e);
        }
        return null;
    }

    /**
     * 检查是否为管理员
     */
    public boolean isAdmin(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        return "admin".equalsIgnoreCase(user.getRole());
    }

    /**
     * 添加用户
     */
    public boolean addUser(String username, String password) {
        if (getUser(username) != null) {
            return false; // 用户已存在
        }

        String sql = "INSERT INTO users (username, password, role, create_time) VALUES (?, ?, ?, ?)";
        String encryptedPassword = encryptPassword(password);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, encryptedPassword);
            pstmt.setString(3, "user");
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add user", e);
        }
    }

    /**
     * 更新用户密码
     */
    public boolean updatePassword(String username, String oldPassword, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
        String oldEncrypted = encryptPassword(oldPassword);
        String newEncrypted = encryptPassword(newPassword);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newEncrypted);
            pstmt.setString(2, username);
            pstmt.setString(3, oldEncrypted);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update password", e);
        }
    }

    /**
     * 强制更新密码（管理员操作）
     */
    public boolean forceUpdatePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        String encryptedPassword = encryptPassword(newPassword);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, encryptedPassword);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to force update password", e);
        }
    }

    /**
     * 删除用户
     */
    public boolean deleteUser(String username) {
        if (DEFAULT_ADMIN.equals(username)) {
            return false; // 不能删除默认管理员
        }

        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    /**
     * 密码加密
     */
    private String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }
}
