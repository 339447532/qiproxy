package org.zhanqi.qiproxy.server.user;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login attempt manager to prevent brute force attacks.
 * Locks user after 3 failed attempts for 5 minutes.
 */
public class LoginAttemptManager {

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private static LoginAttemptManager instance = new LoginAttemptManager();

    // Map of username -> {attempts, lockTime}
    private Map<String, AttemptInfo> attemptMap = new ConcurrentHashMap<>();

    private LoginAttemptManager() {
    }

    public static LoginAttemptManager getInstance() {
        return instance;
    }

    /**
     * Record a failed login attempt
     */
    public void recordFailedAttempt(String username) {
        AttemptInfo info = attemptMap.get(username);
        if (info == null) {
            info = new AttemptInfo();
            attemptMap.put(username, info);
        }
        info.attempts++;
        info.lockTime = System.currentTimeMillis() + LOCK_DURATION_MS;
    }

    /**
     * Check if user is locked out
     */
    public boolean isLocked(String username) {
        AttemptInfo info = attemptMap.get(username);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() > info.lockTime) {
            // Lock expired, reset
            attemptMap.remove(username);
            return false;
        }
        return info.attempts >= MAX_ATTEMPTS;
    }

    /**
     * Get remaining lock time in seconds
     */
    public long getRemainingLockTime(String username) {
        AttemptInfo info = attemptMap.get(username);
        if (info == null) {
            return 0;
        }
        long remaining = info.lockTime - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    /**
     * Clear failed attempts on successful login
     */
    public void clearAttempts(String username) {
        attemptMap.remove(username);
    }

    /**
     * Get number of failed attempts
     */
    public int getFailedAttempts(String username) {
        AttemptInfo info = attemptMap.get(username);
        return info == null ? 0 : info.attempts;
    }

    private static class AttemptInfo {
        int attempts = 0;
        long lockTime = 0;
    }
}
