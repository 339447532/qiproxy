/**
 * SslContextCreator - Creates SSL context for TLS connections
 * Supports PEM format certificates for Node.js
 */

const fs = require('fs');
const path = require('path');
const tls = require('tls');
const config = require('./config');

/**
 * Create SSL context from PEM certificates
 */
function createSSLContext() {
  const certPath = config.getStringValue('ssl.certPath');
  const keyPath = config.getStringValue('ssl.keyPath');
  const keyPassword = config.getStringValue('ssl.keyPassword');

  if (!certPath || !keyPath) {
    console.warn('SSL certificate or key path is null or empty. SSL context won\'t be initialized.');
    return null;
  }

  try {
    // Resolve paths relative to conf directory
    const certFile = path.isAbsolute(certPath) ? certPath : path.join(__dirname, '..', 'conf', certPath);
    const keyFile = path.isAbsolute(keyPath) ? keyPath : path.join(__dirname, '..', 'conf', keyPath);

    if (!fs.existsSync(certFile)) {
      console.warn(`Certificate file does not exist: ${certFile}`);
      return null;
    }

    if (!fs.existsSync(keyFile)) {
      console.warn(`Key file does not exist: ${keyFile}`);
      return null;
    }

    // Read certificates
    const cert = fs.readFileSync(certFile);
    const key = fs.readFileSync(keyFile);

    const options = {
      cert: cert,
      key: key,
      passphrase: keyPassword || undefined
    };

    // Create TLS context
    const sslContext = tls.createSecureContext(options);
    console.info('SSL context initialized successfully');
    return sslContext;

  } catch (err) {
    console.error(`Unable to initialize SSL context. Cause: ${err.message}`);
    return null;
  }
}

module.exports = {
  createSSLContext
};
