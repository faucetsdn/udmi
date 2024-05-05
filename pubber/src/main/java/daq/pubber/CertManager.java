package daq.pubber;

import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.SiteModel;
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
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.PubberConfiguration;

public class CertManager {

  private final PubberConfiguration configuration;
  private final SiteModel siteModel;
  private String caCrtFile;
  private String keyFile;
  private String crtFile;
  private char[] password;

  {
    Security.addProvider(new BouncyCastleProvider());
  }

  public CertManager(SiteModel siteModel, PubberConfiguration configuration) {
    this.configuration = configuration;
    this.siteModel = siteModel;
    File reflectorDir = siteModel.getReflectorDir();
    caCrtFile = new File(reflectorDir, "ca.crt").getPath();
    File deviceDir = siteModel.getDeviceDir(configuration.deviceId);
    crtFile = new File(deviceDir, "rsa_private.crt").getPath();
    keyFile = new File(deviceDir, "rsa_private.pem").getPath();
    String keyPassword = GeneralUtils.sha256((byte[]) configuration.keyBytes).substring(0, 8);
    password = keyPassword.toCharArray();

    System.err.println("CA cert file: " + caCrtFile);
    System.err.println("Device cert file: " + crtFile);
    System.err.println("Private key file: " + keyFile);
    System.err.println("Client password " + keyPassword);
  }

  public SocketFactory getSocketFactory() {
    try {
      if (!Transport.SSL.equals(configuration.endpoint.transport)) {
        return SSLSocketFactory.getDefault();
      }
      return getCertSocketFactory();
    } catch (Exception e) {
      throw new RuntimeException("While creating SSL socket factory", e);
    }
  }

  public SSLSocketFactory getCertSocketFactory() throws Exception {

    // load CA certificate
    X509Certificate caCert = null;

    FileInputStream fis = new FileInputStream(caCrtFile);
    BufferedInputStream bis = new BufferedInputStream(fis);
    CertificateFactory cf = CertificateFactory.getInstance("X.509");

    while (bis.available() > 0) {
      caCert = (X509Certificate) cf.generateCertificate(bis);
      // System.out.println(caCert.toString());
    }

    // load client certificate
    bis = new BufferedInputStream(new FileInputStream(crtFile));
    X509Certificate cert = null;
    while (bis.available() > 0) {
      cert = (X509Certificate) cf.generateCertificate(bis);
      // System.out.println(caCert.toString());
    }

    // load client private key
    PEMParser pemParser = new PEMParser(new FileReader(keyFile));
    Object object = pemParser.readObject();
    PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password);
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
        .setProvider("BC");
    KeyPair key;
    if (object instanceof PEMEncryptedKeyPair) {
      System.out.println("Encrypted key - we will use provided password");
      key = converter.getKeyPair(((PEMEncryptedKeyPair) object)
          .decryptKeyPair(decProv));
    } else {
      System.out.println("Unencrypted key - no password needed");
      key = converter.getKeyPair((PEMKeyPair) object);
    }
    pemParser.close();

    // CA certificate is used to authenticate server
    KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
    caKs.load(null, null);
    caKs.setCertificateEntry("ca-certificate", caCert);
    TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
    tmf.init(caKs);

    // client key and certificates are sent to server so it can authenticate us
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    ks.setCertificateEntry("certificate", cert);
    ks.setKeyEntry("private-key", key.getPrivate(), password,
        new java.security.cert.Certificate[] { cert });
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory
        .getDefaultAlgorithm());
    kmf.init(ks, password);

    // finally, create SSL socket factory
    SSLContext context = SSLContext.getInstance("TLSv1.2");
    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    return context.getSocketFactory();
  }
}
