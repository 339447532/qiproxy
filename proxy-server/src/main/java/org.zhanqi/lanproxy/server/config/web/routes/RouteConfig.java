package org.zhanqi.qiproxy.server.config.web.routes;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.zhanqi.qiproxy.common.JsonUtil;
import org.zhanqi.qiproxy.server.ProxyChannelManager;
import org.zhanqi.qiproxy.server.captcha.CaptchaManager;
import org.zhanqi.qiproxy.server.config.ProxyConfig;
import org.zhanqi.qiproxy.server.config.ProxyConfig.Client;
import org.zhanqi.qiproxy.server.config.ProxyConfig.ClientProxyMapping;
import org.zhanqi.qiproxy.server.config.web.ApiRoute;
import org.zhanqi.qiproxy.server.config.web.RequestHandler;
import org.zhanqi.qiproxy.server.config.web.RequestMiddleware;
import org.zhanqi.qiproxy.server.config.web.ResponseInfo;
import org.zhanqi.qiproxy.server.config.web.exception.ContextException;
import org.zhanqi.qiproxy.server.log.LogService;
import org.zhanqi.qiproxy.server.log.OperationLog;
import org.zhanqi.qiproxy.server.metrics.Metrics;
import org.zhanqi.qiproxy.server.metrics.MetricsCollector;
import org.zhanqi.qiproxy.server.user.LoginAttemptManager;
import org.zhanqi.qiproxy.server.user.User;
import org.zhanqi.qiproxy.server.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * 接口实现
 *
 * @author zhanqi
 *
 */
public class RouteConfig {

    protected static final String AUTH_COOKIE_KEY = "token";

    private static Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    /** 管理员不能同时在多个地方登录 */
    private static String token;

    /** token 到用户的映射 */
    private static Map<String, String> tokenUserMap = new ConcurrentHashMap<String, String>();

    /** 当前登录用户 - 使用 ThreadLocal 确保线程安全 */
    private static ThreadLocal<String> currentUser = new ThreadLocal<String>();

    /**
     * 获取当前请求的用户
     */
    private static String getCurrentUser() {
        return currentUser.get();
    }

    /**
     * 设置当前请求的用户
     */
    private static void setCurrentUser(String user) {
        currentUser.set(user);
    }

    /**
     * 清除当前请求的用户
     */
    private static void clearCurrentUser() {
        currentUser.remove();
    }

    /**
     * 从请求中获取客户端IP
     */
    private static String getClientIp(FullHttpRequest request) {
        // 优先从 X-Forwarded-For 获取（反向代理场景）
        String xForwardedFor = request.headers().get("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            if (xForwardedFor.contains(",")) {
                return xForwardedFor.split(",")[0].trim();
            }
            return xForwardedFor.trim();
        }
        // 从 X-Real-IP 获取
        String xRealIp = request.headers().get("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return null;
    }

    public static void init() {

        // 公开接口（不需要认证）
        ApiRoute.addPublicRoute("/login");
        ApiRoute.addPublicRoute("/captcha/generate");
        ApiRoute.addPublicRoute("/captcha/verify");

        ApiRoute.addMiddleware(new RequestMiddleware() {

            @Override
            public void preRequest(FullHttpRequest request) {
                String uri = request.getUri();

                // 公开接口不需要认证
                if (ApiRoute.isPublicRoute(uri)) {
                    return;
                }

                String cookieHeader = request.headers().get(HttpHeaders.Names.COOKIE);
                boolean authenticated = false;
                String currentToken = null;
                if (cookieHeader != null) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        String[] cookieArr = cookie.split("=");
                        if (AUTH_COOKIE_KEY.equals(cookieArr[0].trim())) {
                            if (cookieArr.length == 2) {
                                currentToken = cookieArr[1].trim();
                                if (currentToken.equals(token)) {
                                    authenticated = true;
                                }
                            }
                        }
                    }
                }

                String auth = request.headers().get(HttpHeaders.Names.AUTHORIZATION);
                if (!authenticated && auth != null) {
                    String[] authArr = auth.split(" ");
                    if (authArr.length == 2 && authArr[0].equals(ProxyConfig.getInstance().getConfigAdminUsername()) && authArr[1].equals(ProxyConfig.getInstance().getConfigAdminPassword())) {
                        authenticated = true;
                        // 通过 Basic Auth 认证时，设置当前用户为 admin
                        setCurrentUser("admin");
                    }
                }

                if (!authenticated) {
                    throw new ContextException(ResponseInfo.CODE_UNAUTHORIZED);
                }

                // 设置当前用户到 ThreadLocal
                if (currentToken != null) {
                    String user = tokenUserMap.get(currentToken);
                    if (user != null) {
                        setCurrentUser(user);
                    }
                }

                logger.info("handle request for api {}", request.getUri());
            }
        });

        // 获取配置详细信息（支持按当前用户隔离）
        ApiRoute.addRoute("/config/detail", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                List<Client> allClients = ProxyConfig.getInstance().getClients();
                List<Client> result = new ArrayList<Client>();
                boolean isAdmin = UserService.getInstance().isAdmin(getCurrentUser());

                for (Client client : allClients) {
                    if (!isAdmin) {
                        String owner = client.getOwner();
                        if (owner != null && !owner.isEmpty() && !owner.equals(getCurrentUser())) {
                            continue;
                        }
                    }
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    if (channel != null) {
                        client.setStatus(1);
                    } else {
                        client.setStatus(0);
                    }
                    result.add(client);
                }

                return ResponseInfo.build(result);
            }
        });

        // 更新配置（按用户隔离，admin 可编辑全部）
        ApiRoute.addRoute("/config/update", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, Charset.forName("UTF-8"));
                List<Client> incomingClients = JsonUtil.json2object(config, new TypeToken<List<Client>>() {
                });
                if (incomingClients == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
                }

                boolean isAdmin = UserService.getInstance().isAdmin(getCurrentUser());
                String operator = getCurrentUser() != null ? getCurrentUser() : "unknown";

                if (!isAdmin) {
                    List<Client> existing = ProxyConfig.getInstance().getClients();
                    List<Client> merged = new ArrayList<Client>();

                    // 收集 incomingClients 中的 clientKey，用于去重
                    java.util.Set<String> incomingKeys = new java.util.HashSet<String>();
                    for (Client client : incomingClients) {
                        incomingKeys.add(client.getClientKey());
                        client.setOwner(operator);
                    }

                    // 保留不属于当前用户的已有客户端
                    for (Client client : existing) {
                        String owner = client.getOwner();
                        if (owner != null && !owner.equals(getCurrentUser())) {
                            merged.add(client);
                        }
                    }

                    // 添加 incomingClients（前端发送的当前用户的客户端列表）
                    merged.addAll(incomingClients);

                    config = JsonUtil.object2json(merged);
                } else {
                    for (Client client : incomingClients) {
                        if (client.getOwner() == null || client.getOwner().isEmpty()) {
                            client.setOwner(operator);
                        }
                    }
                    config = JsonUtil.object2json(incomingClients);
                }

                try {
                    ProxyConfig.getInstance().update(config);
                    String clientIp = getClientIp(request);
                    OperationLog.logConfigChange(operator, clientIp, "update", "更新了客户端配置");
                } catch (Exception ex) {
                    logger.error("config update error", ex);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, ex.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        // 生成验证码
        ApiRoute.addRoute("/captcha/generate", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                String key = UUID.randomUUID().toString().replace("-", "");
                String[] result = CaptchaManager.generateCaptcha(key);
                // result[0] = base64 image, result[1] = key
                Map<String, String> captchaResult = new HashMap<String, String>();
                captchaResult.put("key", result[1]);
                captchaResult.put("image", "data:image/png;base64," + result[0]);
                return ResponseInfo.build(captchaResult);
            }
        });

        // 验证验证码
        ApiRoute.addRoute("/captcha/verify", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf, Charset.forName("UTF-8"));
                Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                String key = params.get("key");
                String code = params.get("code");
                boolean valid = CaptchaManager.verify(key, code);
                Map<String, Boolean> verifyResult = new HashMap<String, Boolean>();
                verifyResult.put("valid", valid);
                return ResponseInfo.build(verifyResult);
            }
        });

        // 登录
        ApiRoute.addRoute("/login", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf);
                Map<String, String> loginParams = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                if (loginParams == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error login info");
                }

                String username = loginParams.get("username");
                String password = loginParams.get("password");
                String captchaKey = loginParams.get("captchaKey");
                String captchaCode = loginParams.get("captchaCode");
                String clientIp = getClientIp(request);

                if (username == null || password == null || captchaKey == null || captchaCode == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Missing parameters");
                }

                // 检查是否被锁定
                if (LoginAttemptManager.getInstance().isLocked(username)) {
                    long remaining = LoginAttemptManager.getInstance().getRemainingLockTime(username);
                    Map<String, Object> lockData = new HashMap<String, Object>();
                    lockData.put("remaining", remaining);
                    return ResponseInfo.build(ResponseInfo.CODE_LOGIN_LOCKED, "login.locked", lockData);
                }

                // 验证验证码 - 验证码失败不计入登录失败次数
                if (!CaptchaManager.verify(captchaKey, captchaCode)) {
                    OperationLog.logLogin(username, clientIp, false);
                    return ResponseInfo.build(ResponseInfo.CODE_CAPTCHA_ERROR, "captcha.error");
                }

                // 验证码成功后重置失败次数
                LoginAttemptManager.getInstance().clearAttempts(username);

                // 验证用户 - 只有验证码正确后密码错误才计入失败次数
                User user = UserService.getInstance().verifyUser(username, password);
                if (user == null) {
                    LoginAttemptManager.getInstance().recordFailedAttempt(username);
                    OperationLog.logLogin(username, clientIp, false);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Invalid username or password");
                }

                // 登录成功，清除失败记录
                LoginAttemptManager.getInstance().clearAttempts(username);

                token = UUID.randomUUID().toString().replace("-", "");
                tokenUserMap.put(token, username);
                setCurrentUser(username);
                OperationLog.logLogin(username, clientIp, true);
                Map<String, String> loginResult = new HashMap<String, String>();
                loginResult.put("token", token);
                loginResult.put("username", username);
                return ResponseInfo.build(loginResult);
            }
        });

        ApiRoute.addRoute("/logout", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                String clientIp = getClientIp(request);
                String user = getCurrentUser();
                OperationLog.log(user != null ? user : "unknown", clientIp, "LOGOUT", "用户退出登录");
                // 从 tokenUserMap 中移除
                if (token != null) {
                    tokenUserMap.remove(token);
                }
                token = null;
                clearCurrentUser();
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        // 获取所有用户（管理员）或当前用户（非管理员）
        ApiRoute.addRoute("/user/list", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                boolean isAdmin = UserService.getInstance().isAdmin(getCurrentUser());
                String currentUser = getCurrentUser();

                Map<String, Object> result = new HashMap<String, Object>();
                result.put("isAdmin", isAdmin);

                if (isAdmin) {
                    result.put("data", UserService.getInstance().getAllUsers());
                } else if (currentUser != null) {
                    // 非管理员只能看到自己
                    User user = UserService.getInstance().getUser(currentUser);
                    if (user != null) {
                        List<User> userList = new ArrayList<User>();
                        userList.add(user);
                        result.put("data", userList);
                    } else {
                        result.put("data", new ArrayList<User>());
                    }
                } else {
                    result.put("data", new ArrayList<User>());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success", result);
            }
        });

        // 添加用户
        ApiRoute.addRoute("/user/add", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                if (!UserService.getInstance().isAdmin(getCurrentUser())) {
                    return ResponseInfo.build(ResponseInfo.CODE_FORBIDDEN, "unauthorized");
                }
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf, Charset.forName("UTF-8"));
                Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                String username = params.get("username");
                String password = params.get("password");

                if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Username and password are required");
                }

                boolean success = UserService.getInstance().addUser(username, password);
                if (!success) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Username already exists");
                }

                String clientIp = getClientIp(request);
                OperationLog.logUserManagement(getCurrentUser(), clientIp, "add", username);
                return ResponseInfo.build(ResponseInfo.CODE_OK, "User added successfully");
            }
        });

        // 更新用户密码
        ApiRoute.addRoute("/user/updatePassword", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf, Charset.forName("UTF-8"));
                Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                String usernameParam = params.get("username");
                String oldPassword = params.get("oldPassword");
                String newPassword = params.get("newPassword");

                String currentUser = getCurrentUser();
                if (currentUser == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_UNAUTHORIZED, "Not logged in");
                }

                boolean success;
                if (oldPassword != null && !oldPassword.isEmpty()) {
                    // 用户修改自己的密码 - 使用当前登录用户，忽略username参数
                    success = UserService.getInstance().updatePassword(currentUser, oldPassword, newPassword);
                } else {
                    // 管理员强制修改密码 - 使用传入的username参数
                    if (!UserService.getInstance().isAdmin(currentUser)) {
                        return ResponseInfo.build(ResponseInfo.CODE_FORBIDDEN, "unauthorized");
                    }
                    if (usernameParam == null || usernameParam.isEmpty()) {
                        return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Invalid parameters");
                    }
                    success = UserService.getInstance().forceUpdatePassword(usernameParam, newPassword);
                }

                if (!success) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Failed to update password");
                }

                String clientIp = getClientIp(request);
                // 日志记录实际修改的用户
                String targetUser = (oldPassword != null && !oldPassword.isEmpty()) ? currentUser : usernameParam;
                OperationLog.logUserManagement(currentUser, clientIp, "update_password", targetUser);
                return ResponseInfo.build(ResponseInfo.CODE_OK, "Password updated successfully");
            }
        });

        // 删除用户
        ApiRoute.addRoute("/user/delete", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                if (!UserService.getInstance().isAdmin(getCurrentUser())) {
                    return ResponseInfo.build(ResponseInfo.CODE_FORBIDDEN, "unauthorized");
                }
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf, Charset.forName("UTF-8"));
                Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                String username = params.get("username");

                if (username == null || username.isEmpty()) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Username is required");
                }

                boolean success = UserService.getInstance().deleteUser(username);
                if (!success) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Cannot delete user");
                }

                String clientIp = getClientIp(request);
                OperationLog.logUserManagement(getCurrentUser(), clientIp, "delete", username);
                return ResponseInfo.build(ResponseInfo.CODE_OK, "User deleted successfully");
            }
        });

        ApiRoute.addRoute("/metrics/get", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                String clientIp = getClientIp(request);
                OperationLog.logMetrics(getCurrentUser() != null ? getCurrentUser() : "unknown", clientIp, "get");
                return ResponseInfo.build(getMetricsByCurrentUser());
            }
        });

        ApiRoute.addRoute("/metrics/getandreset", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                String clientIp = getClientIp(request);
                OperationLog.logMetrics(getCurrentUser() != null ? getCurrentUser() : "unknown", clientIp, "getandreset");
                return ResponseInfo.build(getMetricsByCurrentUser());
            }
        });

        // 自动分配端口
        ApiRoute.addRoute("/port/allocate", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                Integer port = ProxyConfig.getInstance().allocatePort();
                if (port == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_SYSTEM_ERROR, "No available port");
                }
                String clientIp = getClientIp(request);
                OperationLog.logPortAllocate(getCurrentUser() != null ? getCurrentUser() : "unknown", clientIp, port);
                return ResponseInfo.build(port);
            }
        });

        // 获取日志列表（分页）
        ApiRoute.addRoute("/log/list", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                try {
                    byte[] buf = new byte[request.content().readableBytes()];
                    request.content().readBytes(buf);
                    String body = new String(buf, Charset.forName("UTF-8"));

                    String date = null;
                    String username = null;
                    String startTime = null;
                    String endTime = null;
                    int page = 1;
                    int pageSize = 20;

                    if (body != null && !body.isEmpty()) {
                        Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                        });
                        if (params != null) {
                            date = params.get("date");
                            username = params.get("username");
                            startTime = params.get("startTime");
                            endTime = params.get("endTime");
                            String pageStr = params.get("page");
                            String pageSizeStr = params.get("pageSize");
                            if (pageStr != null && !pageStr.isEmpty()) {
                                page = Integer.parseInt(pageStr);
                            }
                            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                                pageSize = Integer.parseInt(pageSizeStr);
                            }
                        }
                    }

                    // 非admin用户只能查看自己的日志，强制使用当前用户名
                    boolean isAdmin = UserService.getInstance().isAdmin(getCurrentUser());
                    if (!isAdmin && getCurrentUser() != null) {
                        username = getCurrentUser();
                    }

                    // 如果没有指定日期，获取最新的日志文件
                    if (date == null || date.isEmpty()) {
                        List<String> dates = LogService.getAvailableDates();
                        if (!dates.isEmpty()) {
                            date = dates.get(0);
                        }
                    }

                    if (date == null || date.isEmpty()) {
                        return ResponseInfo.build(new LogService.LogPageResult());
                    }

                    if (page < 1) page = 1;
                    if (pageSize < 1) pageSize = 20;
                    if (pageSize > 100) pageSize = 100;

                    LogService.LogPageResult result = LogService.getLogsPage(date, username, startTime, endTime, page, pageSize);
                    return ResponseInfo.build(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    return ResponseInfo.build(ResponseInfo.CODE_SYSTEM_ERROR, e.getMessage());
                }
            }
        });

        // 获取可用日志日期列表
        ApiRoute.addRoute("/log/dates", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                List<String> dates = LogService.getAvailableDates();
                return ResponseInfo.build(dates);
            }
        });

        // 清除指定日期日志
        ApiRoute.addRoute("/log/clear", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String body = new String(buf, Charset.forName("UTF-8"));

                Map<String, String> params = JsonUtil.json2object(body, new TypeToken<Map<String, String>>() {
                });
                String date = params.get("date");
                String clientIp = getClientIp(request);

                if (date != null && !date.isEmpty()) {
                    boolean success = LogService.clearLogs(date);
                    if (success) {
                        OperationLog.log(getCurrentUser() != null ? getCurrentUser() : "unknown", clientIp, "LOG_CLEAR", "清除日期 " + date + " 的日志");
                    }
                    return ResponseInfo.build(ResponseInfo.CODE_OK, "日志已清除");
                }
                return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "日期不能为空");
            }
        });

        // 清除全部日志
        ApiRoute.addRoute("/log/clearAll", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                String clientIp = getClientIp(request);
                int count = LogService.clearAllLogs();
                OperationLog.log(getCurrentUser() != null ? getCurrentUser() : "unknown", clientIp, "LOG_CLEAR_ALL", "清除全部日志，共 " + count + " 个日志文件");
                return ResponseInfo.build(ResponseInfo.CODE_OK, "已清除 " + count + " 个日志文件");
            }
        });
    }

    /**
     * 获取当前用户的统计数据（按用户隔离）
     */
    private static List<Metrics> getMetricsByCurrentUser() {
        List<Metrics> userMetrics = new ArrayList<Metrics>();
        boolean isAdmin = UserService.getInstance().isAdmin(getCurrentUser());

        // 如果是admin用户，返回所有统计
        if (isAdmin) {
            return MetricsCollector.getAllMetrics();
        }

        // 普通用户：只返回自己客户端的端口统计
        List<Client> allClients = ProxyConfig.getInstance().getClients();
        List<Integer> userPorts = new ArrayList<Integer>();

        for (Client client : allClients) {
            String owner = client.getOwner();
            if (owner != null && owner.equals(getCurrentUser())) {
                List<ClientProxyMapping> mappings = client.getProxyMappings();
                if (mappings != null) {
                    for (ClientProxyMapping mapping : mappings) {
                        userPorts.add(mapping.getInetPort());
                    }
                }
            }
        }

        // 获取这些端口的统计
        List<Metrics> allMetrics = MetricsCollector.getAllMetrics();
        for (Metrics m : allMetrics) {
            if (userPorts.contains(m.getPort())) {
                userMetrics.add(m);
            }
        }

        return userMetrics;
    }

}
