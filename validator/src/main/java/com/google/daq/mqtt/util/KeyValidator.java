package com.google.daq.mqtt.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

/**
 * A helper class to validate cryptographic key pairs.
 */
public class KeyValidator {

  private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(BC_PROVIDER);
    }
  }

  private Object readPemObject(Path path) throws IOException {
    if (!path.toFile().exists()) {
      throw new IOException("Key file not found: " + path);
    }
    try (FileReader fileReader = new FileReader(path.toFile());
        PEMParser pemParser = new PEMParser(fileReader)) {
      Object object = pemParser.readObject();
      if (object == null) {
        throw new IOException("No PEM object found in " + path);
      }
      return object;
    }
  }

  private PrivateKey loadPrivateKey(Path path) throws Exception {
    Object pemObject = readPemObject(path);
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BC_PROVIDER);
    PrivateKey privateKey;

    try {
      if (pemObject instanceof PEMKeyPair) {
        privateKey = converter.getPrivateKey(((PEMKeyPair) pemObject).getPrivateKeyInfo());
      } else if (pemObject instanceof PrivateKeyInfo) {
        privateKey = converter.getPrivateKey((PrivateKeyInfo) pemObject);
      } else if (pemObject instanceof PKCS8EncryptedPrivateKeyInfo) {
        throw new IllegalArgumentException("Private key in " + path.getFileName()
            + " is encrypted. Please provide a decrypted key.");
      } else {
        throw new IllegalArgumentException("Unsupported PEM object type for private key: "
            + pemObject.getClass().getName());
      }
    } catch (Exception e) {
      throw new Exception("Failed to convert PEM object to PrivateKey for " + path.getFileName()
          + ": " + e.getMessage(), e);
    }

    if (privateKey == null) {
      throw new Exception("JcaPEMKeyConverter returned null private key from "
          + path.getFileName());
    }
    return privateKey;
  }

  private PublicKey loadPublicKey(Path path) throws Exception {
    Object pemObject = readPemObject(path);
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BC_PROVIDER);
    PublicKey publicKey;

    try {
      if (pemObject instanceof SubjectPublicKeyInfo) {
        publicKey = converter.getPublicKey((SubjectPublicKeyInfo) pemObject);
      } else if (pemObject instanceof PEMKeyPair) {
        publicKey = converter.getPublicKey(((PEMKeyPair) pemObject).getPublicKeyInfo());
      } else {
        throw new IllegalArgumentException("Unsupported PEM object type for public key: "
            + pemObject.getClass().getName());
      }
    } catch (Exception e) {
      throw new Exception("Failed to convert PEM object to PublicKey for " + path.getFileName()
          + ": " + e.getMessage(), e);
    }

    if (publicKey == null) {
      throw new Exception("JcaPEMKeyConverter returned null public key from " + path.getFileName());
    }
    return publicKey;
  }

  private boolean checkKeyPairMatch(PrivateKey privateKey, PublicKey publicKey, String keyAlgorithm)
      throws Exception {
    String signatureAlgorithm;
    if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
      signatureAlgorithm = "SHA256withRSA";
    } else if ("EC".equalsIgnoreCase(keyAlgorithm)) {
      signatureAlgorithm = "SHA256withECDSA";
    } else {
      throw new IllegalArgumentException("Unsupported key algorithm for signature: "
          + keyAlgorithm);
    }

    byte[] challenge = new byte[1024];
    Signature signature = Signature.getInstance(signatureAlgorithm, BC_PROVIDER);

    signature.initSign(privateKey);
    signature.update(challenge);
    byte[] signatureInBytes = signature.sign();

    signature.initVerify(publicKey);
    signature.update(challenge);
    boolean isValid = signature.verify(signatureInBytes);
    return isValid;
  }

  /**
   * Checks if the provided private and public key files form a valid cryptographic pair
   * for the specified key algorithm.
   *
   * @param privateKeyFile the {@link File} object for the private key PEM file.
   * @param publicKeyFile the {@link File} object for the public key PEM file.
   * @param keyAlgorithm the expected key algorithm, either "RSA" or "EC" (case-insensitive).
   * @return {@code true} if the keys match and are valid for the algorithm, else {@code false}.
   *         Errors during loading or validation also result in {@code false}.
   */
  public boolean keysMatch(File privateKeyFile, File publicKeyFile, String keyAlgorithm) {
    if (privateKeyFile == null || publicKeyFile == null) {
      return false;
    }
    if (!("RSA".equalsIgnoreCase(keyAlgorithm) || "EC".equalsIgnoreCase(keyAlgorithm))) {
      return false;
    }
    return keysMatch(privateKeyFile.toPath(), publicKeyFile.toPath(), keyAlgorithm.toUpperCase());
  }

  private boolean keysMatch(Path privateKeyPath, Path publicKeyPath, String keyAlgorithm) {
    try {
      PrivateKey privateKey = loadPrivateKey(privateKeyPath);
      PublicKey publicKey = loadPublicKey(publicKeyPath);

      return checkKeyPairMatch(privateKey, publicKey, keyAlgorithm);
    } catch (Exception e) {
      return false;
    }
  }
}
