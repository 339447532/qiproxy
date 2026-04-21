/**
 * Config - Properties file reader (mirrors Java Config class)
 */
const fs = require('fs');
const path = require('path');

class Config {
  constructor() {
    this.properties = {};
    this.loadConfig('config.properties');
  }

  loadConfig(configFile) {
    const configPath = path.join(__dirname, '..', 'conf', configFile);
    try {
      const content = fs.readFileSync(configPath, 'utf8');
      const lines = content.split('\n');
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#')) {
          const idx = trimmed.indexOf('=');
          if (idx > 0) {
            const key = trimmed.substring(0, idx).trim();
            const value = trimmed.substring(idx + 1).trim();
            this.properties[key] = value;
          }
        }
      }
    } catch (err) {
      console.error('Failed to load config:', err.message);
    }
  }

  getStringValue(key, defaultValue = null) {
    const value = this.properties[key];
    return value !== undefined ? value : defaultValue;
  }

  getIntValue(key, defaultValue = 0) {
    const value = this.properties[key];
    if (value === undefined) return defaultValue;
    const parsed = parseInt(value, 10);
    return isNaN(parsed) ? defaultValue : parsed;
  }

  getBooleanValue(key, defaultValue = false) {
    const value = this.properties[key];
    if (value === undefined) return defaultValue;
    return value.toLowerCase() === 'true';
  }
}

module.exports = new Config();
