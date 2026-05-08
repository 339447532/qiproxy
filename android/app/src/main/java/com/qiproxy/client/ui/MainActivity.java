package com.qiproxy.client.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.qiproxy.client.R;
import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.config.ConfigManager;
import com.qiproxy.client.core.ProxyClientContainer;
import com.qiproxy.client.service.ProxyForegroundService;
import com.qiproxy.client.util.ProxyLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MainActivity - Primary UI for the proxy client.
 */
public class MainActivity extends AppCompatActivity {

    private Button btnStartStop;
    private TextView tvStatus;
    private TextView tvStats;
    private RecyclerView rvLogs;
    private LogViewAdapter logAdapter;
    private List<String> logBuffer = new ArrayList<>();

    private ProxyForegroundService proxyService;
    private boolean serviceBound = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ProxyClientContainer.StateListener activityStateListener;
    private ProxyLogger.LogListener logListener;
    private final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();
    private final Runnable logFlushRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingLogs.isEmpty()) {
                uiHandler.postDelayed(this, 200);
                return;
            }
            List<String> batch = new ArrayList<>();
            String line;
            while ((line = pendingLogs.poll()) != null && batch.size() < 100) {
                batch.add(line);
            }
            if (!batch.isEmpty()) {
                logAdapter.addLogs(batch);
                rvLogs.scrollToPosition(logAdapter.getItemCount() - 1);
            }
            uiHandler.postDelayed(this, 200);
        }
    };

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ProxyForegroundService.LocalBinder binder = (ProxyForegroundService.LocalBinder) service;
            proxyService = binder.getService();
            serviceBound = true;
            attachStateListener();
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            proxyService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ConfigManager.getInstance().init(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } else if (id == R.id.nav_github) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/339447532/qiproxy"));
                startActivity(intent);
            }
            drawerLayout.closeDrawers();
            return true;
        });

        btnStartStop = findViewById(R.id.btn_start_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvStats = findViewById(R.id.tv_stats);
        rvLogs = findViewById(R.id.rv_logs);

        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogViewAdapter(logBuffer);
        rvLogs.setAdapter(logAdapter);

        btnStartStop.setOnClickListener(v -> toggleService());

        // Register log listener with batch flush to prevent UI handler queue bloat
        logListener = (level, line) -> pendingLogs.offer(line);
        ProxyLogger.addListener(logListener);
        uiHandler.postDelayed(logFlushRunnable, 200);

        // Bind to service
        Intent intent = new Intent(this, ProxyForegroundService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Check battery optimization
        checkBatteryOptimization();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        attachStateListener();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(logFlushRunnable);
        if (proxyService != null && activityStateListener != null) {
            proxyService.removeContainerStateListener(activityStateListener);
        }
        if (logListener != null) {
            ProxyLogger.removeListener(logListener);
        }
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void attachStateListener() {
        if (proxyService == null) return;
        if (activityStateListener != null) {
            proxyService.removeContainerStateListener(activityStateListener);
        }
        activityStateListener = (state, message) -> {
            uiHandler.post(() -> {
                tvStatus.setText(message);
                if (state == ProxyClientContainer.State.CONNECTED) {
                    btnStartStop.setText(R.string.action_stop);
                    tvStatus.setText(R.string.status_running);
                } else if (state == ProxyClientContainer.State.IDLE || state == ProxyClientContainer.State.DISCONNECTED || state == ProxyClientContainer.State.ERROR) {
                    btnStartStop.setText(R.string.action_start);
                    if (state == ProxyClientContainer.State.IDLE) {
                        tvStatus.setText(R.string.status_stopped);
                    }
                }
                updateStats();
            });
        };
        proxyService.addContainerStateListener(activityStateListener);
        // Sync current state immediately if container already exists
        ProxyClientContainer client = proxyService.getClientContainer();
        if (client != null && client.getState() == ProxyClientContainer.State.CONNECTED) {
            uiHandler.post(() -> {
                btnStartStop.setText(R.string.action_stop);
                tvStatus.setText(R.string.status_running);
                updateStats();
            });
        }
    }

    private void toggleService() {
        if (!ConfigManager.getInstance().isConfigured()) {
            Toast.makeText(this, "请先配置服务器地址和客户端密钥", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (proxyService != null && proxyService.getClientContainer() != null
                && proxyService.getClientContainer().isRunning()) {
            try {
                proxyService.stopClient();
            } catch (Exception e) {
                ProxyLogger.e("Stop client failed: " + e.getMessage());
            }
            btnStartStop.setText(R.string.action_start);
            tvStatus.setText(R.string.status_stopped);
            tvStats.setText("");
        } else {
            ClientConfig config = ConfigManager.getInstance().loadConfig();
            Intent intent = new Intent(this, ProxyForegroundService.class);
            startForegroundService(intent);
            btnStartStop.setText(R.string.action_stop);
            tvStatus.setText(R.string.status_starting);
            // Re-attach listener in case service created a new client container
            attachStateListener();
        }
    }

    private void updateUI() {
        if (proxyService != null && proxyService.getClientContainer() != null
                && proxyService.getClientContainer().isRunning()) {
            btnStartStop.setText(R.string.action_stop);
            ProxyClientContainer client = proxyService.getClientContainer();
            if (client.getState() == ProxyClientContainer.State.CONNECTED) {
                tvStatus.setText(R.string.status_running);
            } else {
                tvStatus.setText(R.string.status_starting);
            }
            updateStats();
        } else {
            btnStartStop.setText(R.string.action_start);
            tvStatus.setText(R.string.status_stopped);
            tvStats.setText("");
        }
    }

    private void updateStats() {
        if (proxyService != null && proxyService.getClientContainer() != null) {
            ProxyClientContainer client = proxyService.getClientContainer();
            tvStats.setText("Pool: " + client.getPoolSize() + " | Active: " + client.getActiveCount());
        }
    }

    private void checkBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean shown = prefs.getBoolean("battery_dialog_shown", false);
            if (shown) {
                return;
            }
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                new AlertDialog.Builder(this)
                        .setTitle("电池优化")
                        .setMessage("请为 QiProxy 关闭电池优化，以保持后台连接存活。")
                        .setPositiveButton("打开设置", (dialog, which) -> {
                            prefs.edit().putBoolean("battery_dialog_shown", true).apply();
                            startActivity(intent);
                        })
                        .setNegativeButton("稍后", (dialog, which) -> {
                            prefs.edit().putBoolean("battery_dialog_shown", true).apply();
                        })
                        .setOnCancelListener(dialog -> {
                            prefs.edit().putBoolean("battery_dialog_shown", true).apply();
                        })
                        .show();
            }
        }
    }
}
