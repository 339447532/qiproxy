package com.qiproxy.client.network;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * PEM parsing utilities
 */
public class PemUtils {

    private PemUtils() {}

    public static X509Certificate parseCertificate(String pemContent) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new java.io.ByteArrayInputStream(pemContent.getBytes()));
    }

    public static PrivateKey parsePrivateKey(String pemContent) throws IOException {
        try (PEMParser parser = new PEMParser(new StringReader(pemContent))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (obj instanceof PEMKeyPair) {
                return converter.getKeyPair((PEMKeyPair) obj).getPrivate();
            } else if (obj instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) obj);
            }
            throw new IOException("Unsupported PEM object: " + (obj != null ? obj.getClass().getName() : "null"));
        }
    }
}
