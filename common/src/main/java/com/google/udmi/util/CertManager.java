package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.sha256;
import static java.lang.String.format;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import udmi.schema.EndpointConfiguration.Transport;

/**
 * Manager class for CA-signed SSL certificates.
 */
public class CertManager {

  public static final String TLS_1_2_PROTOCOL = "TLSv1.2";
  public static final String CA_CERT_FILE = "ca.crt";
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
  private final boolean isSsl;

  {
    Security.addProvider(new BouncyCastleProvider());
  }

  /**
   * Create a new cert manager for the given site model and configuration.
   */
  public CertManager(File caCrtFile, File clientDir, Transport transport,
      String passString, Consumer<String> logging) {
    this.caCrtFile = caCrtFile;
    isSsl = Transport.SSL.equals(transport);

    if (isSsl) {
      String prefix = keyPrefix(clientDir);
      crtFile = new File(clientDir, prefix + "_private.crt");
      keyFile = new File(clientDir, prefix + "_private.pem");
      this.password = passString.toCharArray();
      logging.accept("CA cert file: " + caCrtFile);
      logging.accept("Device cert file: " + crtFile);
      logging.accept("Private key file: " + keyFile);
      logging.accept("Password sha256 " + sha256(passString).substring(0, 8));
    } else {
      crtFile = null;
      keyFile = null;
      password = null;
    }
  }

  private String keyPrefix(File clientDir) {
    File rsaCrtFile = new File(clientDir, "rsa_private.crt");
    File ecCrtFile = new File(clientDir, "ec_private.crt");
    checkState(rsaCrtFile.exists() || ecCrtFile.exists(),
        "no .crt found for device in " + clientDir.getAbsolutePath());
    return rsaCrtFile.exists() ? "rsa" : "ec";
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

    final PrivateKey privateKey;
    try (PEMParser pemParser = new PEMParser(new FileReader(keyFile))) {
      Object pemObject = pemParser.readObject();
      if (pemObject instanceof PEMEncryptedKeyPair keyPair) {
        PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password);
        privateKey = converter.getKeyPair(keyPair.decryptKeyPair(decProv)).getPrivate();
      } else if (pemObject instanceof PEMKeyPair keyPair) {
        privateKey = converter.getKeyPair(keyPair).getPrivate();
      } else if (pemObject instanceof PrivateKeyInfo keyPair) {
        privateKey = converter.getPrivateKey(keyPair);
      } else {
        throw new RuntimeException(format("Unknown pem file type %s from %s",
            pemObject.getClass().getSimpleName(), keyFile.getAbsolutePath()));
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
    clientKeyStore.setKeyEntry(PRIVATE_KEY_ALIAS, privateKey, password,
        new java.security.cert.Certificate[]{clientCert});
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(clientKeyStore, password);

    SSLContext context = SSLContext.getInstance(TLS_1_2_PROTOCOL);
    context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

    return context.getSocketFactory();
  }

  /**
   * Get a socket factory appropriate for the configuration.
   */
  public SocketFactory getSocketFactory() {
    try {
      if (!isSsl) {
        return SSLSocketFactory.getDefault();
      }
      return getCertSocketFactory();
    } catch (Exception e) {
      throw new RuntimeException("While creating SSL socket factory", e);
    }
  }
}
