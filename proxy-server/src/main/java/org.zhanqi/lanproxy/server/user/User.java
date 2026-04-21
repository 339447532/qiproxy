package org.zhanqi.qiproxy.server.user;

import java.io.Serializable;

/**
 * 登录用户
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String role;
    private long createTime;

    public User() {
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = "user";
        this.createTime = System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
