package webapp.mqtt;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import static webapp.ManagerServlet.logMessage;

public class MqttSecurity {

    public SSLSocketFactory getSocketFactory() {

        Certificate caCert = extractCertificate("/etc/mosquitto/certs/ca.crt");
        Certificate clientCert = extractCertificate("/etc/mosquitto/certs/rsa_private.crt");
        KeyPair clientKey = extractKey("/etc/mosquitto/certs/rsa_private.pem");

        SSLSocketFactory result = null;
        String passwordText = "";

        try {
            // CA certificate is used to authenticate server.
            KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
            caKs.load(null, null);
            caKs.setCertificateEntry("ca-certificate", caCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(caKs);

            // Client key and certificates are sent to server so it can authenticate us.
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("certificate", clientCert);
            ks.setKeyEntry("private-key", clientKey.getPrivate(), passwordText.toCharArray(),
                    new java.security.cert.Certificate[]{clientCert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            kmf.init(ks, passwordText.toCharArray());

            // Create SSL socket factory.
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            result = context.getSocketFactory();

        } catch (Exception tlsException) {
            // noinspection CallToPrintStackTrace
            tlsException.printStackTrace();
        }

        return result;
    }

    private Certificate extractCertificate(String certFilePath) {

        Certificate certObj = null;
        try {
            File certFileInstance = getCertFile(certFilePath);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(certFileInstance));
            if (bis.available() > 0) {
                certObj = certFactory.generateCertificate(bis);
            }
        } catch (IllegalArgumentException e){
            logMessage(e.getMessage());
        } catch (Exception certException) {
            // noinspection CallToPrintStackTrace
            certException.printStackTrace();
        }
        return certObj;
    }

    private KeyPair extractKey(String keyFilePath) {

        KeyPair keyObj = null;
        File keyFileInstance = getCertFile(keyFilePath);

        try {
            PEMParser pemParser = new PEMParser(new FileReader(keyFileInstance));
            Object pemObject = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            PrivateKey privateKey = converter.getPrivateKey((PrivateKeyInfo) pemObject);
            keyObj = new KeyPair(null, privateKey);
            pemParser.close();

        } catch (Exception keyException) {
            logMessage(keyException.getMessage());
        }

        return keyObj;
    }

    private File getCertFile(String certFilePath) {
        if(certFilePath == null || certFilePath.isEmpty()){
            throw new IllegalArgumentException("Certificate file path not provided: " + certFilePath);
        }

        File certFileInstance = new File(certFilePath);

        if (!certFileInstance.exists()) {
            throw new IllegalArgumentException("Certificate file does not exist: " + certFilePath);
        }

        if (!certFileInstance.isFile()) {
            throw new IllegalArgumentException("Certificate path is not a file: " + certFilePath);
        }

        if (!certFileInstance.canRead()) {
            throw new IllegalArgumentException("Certificate file is not readable: " + certFilePath);
        }

        return certFileInstance;
    }
}
