package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.sha256;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.PubberConfiguration;

/**
 * Manager class for CA-signed SSL certificates.
 */
public class CertManager {

  public static final String TLS_1_2_PROTOCOL = "TLSv1.2";
  private static final String BOUNCY_CASTLE_PROVIDER = "BC";
  private static final String X509_FACTORY = "X.509";
  private static final String X509_ALGORITHM = "X509";
  private static final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(
      BOUNCY_CASTLE_PROVIDER);
  private static final String CA_CERT_ALIAS = "ca-certificate";
  private static final String CLIENT_CERT_ALIAS = "certificate";
  private static final String PRIVATE_KEY_ALIAS = "private-key";
  private final File caCrtFile;
  private final File keyFile;
  private final File crtFile;
  private final char[] password;
  private final EndpointConfiguration endpoint;

  {
    Security.addProvider(new BouncyCastleProvider());
  }

  /**
   * Create a new cert manager for the given site model and configuration.
   */
  public CertManager(File caCrtFile, File clientDir, EndpointConfiguration endpoint) {
    this.endpoint = endpoint;
    this.caCrtFile = caCrtFile;
    crtFile = new File(clientDir, "rsa_private.crt");
    keyFile = new File(clientDir, "rsa_private.pem");
    String keyPassword = sha256((byte[]) endpoint.auth_provider.key_bytes).substring(0, 8);
    password = keyPassword.toCharArray();

    System.err.println("CA cert file: " + caCrtFile);
    System.err.println("Device cert file: " + crtFile);
    System.err.println("Private key file: " + keyFile);
    System.err.println("Client password " + keyPassword);
  }

  /**
   * Get a socket factory appropriate for the configuration.
   */
  public SocketFactory getSocketFactory() {
    try {
      if (!Transport.SSL.equals(endpoint.transport)) {
        return SSLSocketFactory.getDefault();
      }
      return getCertSocketFactory();
    } catch (Exception e) {
      throw new RuntimeException("While creating SSL socket factory", e);
    }
  }

  /**
   * Get a certificate-backed socket factory.
   */
  public SSLSocketFactory getCertSocketFactory() throws Exception {
    CertificateFactory certFactory = CertificateFactory.getInstance(X509_FACTORY);

    final X509Certificate caCert;
    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(caCrtFile))) {
      caCert = (X509Certificate) certFactory.generateCertificate(bis);
    }

    final X509Certificate clientCert;
    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(crtFile))) {
      clientCert = (X509Certificate) certFactory.generateCertificate(bis);
    }

    final KeyPair key;
    try (PEMParser pemParser = new PEMParser(new FileReader(keyFile))) {
      Object pemObject = pemParser.readObject();
      if (pemObject instanceof PEMEncryptedKeyPair) {
        PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password);
        key = converter.getKeyPair(((PEMEncryptedKeyPair) pemObject).decryptKeyPair(decProv));
      } else {
        key = converter.getKeyPair((PEMKeyPair) pemObject);
      }
    }

    KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    caKeyStore.load(null, null);
    caKeyStore.setCertificateEntry(CA_CERT_ALIAS, caCert);
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(X509_ALGORITHM);
    trustManagerFactory.init(caKeyStore);

    KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    clientKeyStore.load(null, null);
    clientKeyStore.setCertificateEntry(CLIENT_CERT_ALIAS, clientCert);
    clientKeyStore.setKeyEntry(PRIVATE_KEY_ALIAS, key.getPrivate(), password,
        new java.security.cert.Certificate[]{clientCert});
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(clientKeyStore, password);

    SSLContext context = SSLContext.getInstance(TLS_1_2_PROTOCOL);
    context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

    return context.getSocketFactory();
  }
}
