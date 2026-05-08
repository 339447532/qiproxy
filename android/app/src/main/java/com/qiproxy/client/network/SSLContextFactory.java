package com.qiproxy.client.network;

import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.util.ProxyLogger;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSLContextFactory - Builds SSLContext from PEM certificates
 * Mirrors Node.js sslContext.js
 */
public class SSLContextFactory {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static SSLContext createSSLContext(ClientConfig config) throws Exception {
        String password = config.getSslKeyPassword();

        // Try embedded bytes first
        if (config.getSslCertBytes() != null && config.getSslKeyBytes() != null) {
            try {
                return createSSLContextFromBytes(config.getSslCertBytes(), config.getSslKeyBytes(), password);
            } catch (Exception e) {
                ProxyLogger.w("Failed to create SSLContext from embedded certs, falling back to files", e);
            }
        }

        String certPath = config.getSslCertPath();
        String keyPath = config.getSslKeyPath();

        if (certPath == null || certPath.isEmpty() || keyPath == null || keyPath.isEmpty()) {
            return createTrustAllContext();
        }

        File certFile = new File(certPath);
        File keyFile = new File(keyPath);
        if (!certFile.exists() || !keyFile.exists()) {
            ProxyLogger.w("Certificate files not found, falling back to trust-all context");
            return createTrustAllContext();
        }

        return createSSLContextFromFiles(certFile, keyFile, password);
    }

    private static SSLContext createSSLContextFromBytes(byte[] certBytes, byte[] keyBytes, String password) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (InputStream is = new ByteArrayInputStream(certBytes)) {
            cert = (X509Certificate) cf.generateCertificate(is);
        }

        Object keyObject;
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(keyBytes)))) {
            keyObject = pemParser.readObject();
        }

        java.security.PrivateKey privateKey = convertPrivateKey(keyObject);
        return buildSSLContext(cert, privateKey, password);
    }

    private static SSLContext createSSLContextFromFiles(File certFile, File keyFile, String password) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (FileInputStream fis = new FileInputStream(certFile)) {
            cert = (X509Certificate) cf.generateCertificate(fis);
        }

        Object keyObject;
        try (PEMParser pemParser = new PEMParser(new FileReader(keyFile))) {
            keyObject = pemParser.readObject();
        }

        java.security.PrivateKey privateKey = convertPrivateKey(keyObject);
        return buildSSLContext(cert, privateKey, password);
    }

    private static java.security.PrivateKey convertPrivateKey(Object keyObject) throws Exception {
        if (keyObject instanceof PrivateKeyInfo) {
            // Use default JCA provider (not BC) for RSA on Android P+
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(
                    ((PrivateKeyInfo) keyObject).getEncoded());
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } else if (keyObject instanceof PEMKeyPair) {
            PEMKeyPair keyPair = (PEMKeyPair) keyObject;
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(
                    keyPair.getPrivateKeyInfo().getEncoded());
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } else {
            throw new IOException("Unsupported key format: " + keyObject.getClass().getName());
        }
    }

    private static SSLContext buildSSLContext(X509Certificate cert, java.security.PrivateKey privateKey, String password) throws Exception {
        char[] keyPassword = (password != null && !password.isEmpty()) ? password.toCharArray() : new char[0];
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, keyPassword, new java.security.cert.Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{createTrustAllManager()}, null);

        ProxyLogger.i("SSLContext created successfully");
        return sslContext;
    }

    private static SSLContext createTrustAllContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{createTrustAllManager()}, new java.security.SecureRandom());
        return sslContext;
    }

    private static X509TrustManager createTrustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        };
    }
}
