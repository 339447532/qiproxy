package com.qiproxy.client.ui;

import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.qiproxy.client.R;
import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.config.ConfigManager;
import com.qiproxy.client.util.ProxyLogger;

/**
 * SettingsFragment - Preference screen for proxy client configuration.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        loadCurrentConfig();

        findPreference("save_config").setOnPreferenceClickListener(preference -> {
            saveConfig();
            return true;
        });
    }

    private void loadCurrentConfig() {
        ClientConfig config = ConfigManager.getInstance().loadConfig();

        EditTextPreference clientKeyPref = findPreference("client.key");
        if (clientKeyPref != null) clientKeyPref.setText(config.getClientKey());

        SwitchPreferenceCompat sslPref = findPreference("ssl.enable");
        if (sslPref != null) sslPref.setChecked(config.isSslEnabled());

        EditTextPreference hostPref = findPreference("server.host");
        if (hostPref != null) hostPref.setText(config.getServerHost());

        EditTextPreference portPref = findPreference("server.port");
        if (portPref != null) portPref.setText(String.valueOf(config.getServerPort()));

        EditTextPreference logLevelPref = findPreference("log.level");
        if (logLevelPref != null) logLevelPref.setText(config.getLogLevel());
    }

    private void saveConfig() {
        ClientConfig config = new ClientConfig();

        EditTextPreference clientKeyPref = findPreference("client.key");
        if (clientKeyPref != null) config.setClientKey(clientKeyPref.getText());

        SwitchPreferenceCompat sslPref = findPreference("ssl.enable");
        if (sslPref != null) config.setSslEnabled(sslPref.isChecked());

        EditTextPreference hostPref = findPreference("server.host");
        if (hostPref != null) config.setServerHost(hostPref.getText());

        EditTextPreference portPref = findPreference("server.port");
        if (portPref != null) {
            try {
                config.setServerPort(Integer.parseInt(portPref.getText()));
            } catch (NumberFormatException e) {
                config.setServerPort(4900);
            }
        }

        EditTextPreference logLevelPref = findPreference("log.level");
        if (logLevelPref != null) config.setLogLevel(logLevelPref.getText());

        ConfigManager.getInstance().saveConfig(config);
        ProxyLogger.setLevel(config.getLogLevel());

        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), "配置已保存", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
