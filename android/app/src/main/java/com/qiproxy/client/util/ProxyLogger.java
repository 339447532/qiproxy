package com.qiproxy.client.util;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ProxyLogger - Android Log wrapper with configurable level and UI callback support
 * Mirrors Node.js logger.js
 */
public class ProxyLogger {
    private static final String TAG = "QiProxy";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    public interface LogListener {
        void onLog(String level, String message);
    }

    private static Level currentLevel = Level.INFO;
    private static final List<LogListener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public static void setLevel(String levelName) {
        try {
            currentLevel = Level.valueOf(levelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            currentLevel = Level.INFO;
        }
    }

    private static void notifyListeners(String level, String msg) {
        String time = TIME_FORMAT.format(new Date());
        String line = "[" + time + "] " + level + ": " + msg;
        for (LogListener listener : listeners) {
            try {
                listener.onLog(level, line);
            } catch (Exception ignored) {
            }
        }
    }

    public static void d(String msg) {
        if (currentLevel.ordinal() <= Level.DEBUG.ordinal()) {
            Log.d(TAG, msg);
            notifyListeners("D", msg);
        }
    }

    public static void i(String msg) {
        if (currentLevel.ordinal() <= Level.INFO.ordinal()) {
            Log.i(TAG, msg);
            notifyListeners("I", msg);
        }
    }

    public static void w(String msg) {
        if (currentLevel.ordinal() <= Level.WARN.ordinal()) {
            Log.w(TAG, msg);
            notifyListeners("W", msg);
        }
    }

    public static void w(String msg, Throwable tr) {
        if (currentLevel.ordinal() <= Level.WARN.ordinal()) {
            Log.w(TAG, msg, tr);
            notifyListeners("W", msg + " - " + tr.getMessage());
        }
    }

    public static void e(String msg) {
        if (currentLevel.ordinal() <= Level.ERROR.ordinal()) {
            Log.e(TAG, msg);
            notifyListeners("E", msg);
        }
    }

    public static void e(String msg, Throwable tr) {
        if (currentLevel.ordinal() <= Level.ERROR.ordinal()) {
            Log.e(TAG, msg, tr);
            notifyListeners("E", msg + " - " + tr.getMessage());
        }
    }
}
