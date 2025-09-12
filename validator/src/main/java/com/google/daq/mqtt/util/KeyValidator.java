package com.google.daq.mqtt.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;


/**
 * KeyValidator class holds the logic for validating the keys.
 */
public class KeyValidator {
  private static final String PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
  private static final String PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";
  private static final String RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";
  private static final String PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----";
  private static final String PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----";

  /**
   * Extracts the Base64 encoded key material from a string based on header and footer.
   */
  private Optional<byte[]> extractKeyMaterial(String content, String header, String footer) {
    int start = content.indexOf(header);
    if (start == -1) {
      return Optional.empty();
    }
    start += header.length();

    int end = content.indexOf(footer, start);
    if (end == -1) {
      System.err.println("Warning: Found header '" + header + "' but no matching footer.");
      return Optional.empty();
    }

    String base64Content = content.substring(start, end);
    String cleanedContent = base64Content.replaceAll("\\s+", "");
    if (cleanedContent.isEmpty()) {
      System.err.println("Warning: No Base64 content between " + header + " and " + footer);
      return Optional.empty();
    }
    try {
      return Optional.of(Base64.getDecoder().decode(cleanedContent));
    } catch (IllegalArgumentException e) {
      System.err.println("Warning: Invalid Base64 encoding between " + header + " and " + footer);
      return Optional.empty();
    }
  }

  /**
   * Loads a PrivateKey from decoded bytes. Handles PKCS#8.
   */
  private PrivateKey loadPrivateKeyFromBytes(byte[] keyBytes) throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
    return keyFactory.generatePrivate(keySpec);
  }

  /**
   * Loads a PublicKey from decoded bytes. Handles X.509.
   */
  private PublicKey loadPublicKeyFromBytes(byte[] keyBytes) throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
    return keyFactory.generatePublic(keySpec);
  }

  /**
   * Loads a PrivateKey from a PEM file path.
   */
  private PrivateKey loadPrivateKey(Path path) throws Exception {
    if (!Files.exists(path)) {
      throw new IOException("Private key file not found: " + path);
    }
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    Optional<byte[]> keyBytes = extractKeyMaterial(content, PRIVATE_KEY_HEADER, PRIVATE_KEY_FOOTER);
    boolean isPkcs1 = false;

    if (!keyBytes.isPresent()) {
      keyBytes = extractKeyMaterial(content, RSA_PRIVATE_KEY_HEADER, RSA_PRIVATE_KEY_FOOTER);
      isPkcs1 = true;
      if (!keyBytes.isPresent()) {
        throw new IllegalArgumentException("No private key block found in " + path);
      }
    }

    try {
      return loadPrivateKeyFromBytes(keyBytes.get());
    } catch (InvalidKeySpecException e) {
      if (isPkcs1) {
        throw new InvalidKeySpecException(
            "Failed to load private key from " + path, e);
      } else {
        throw new InvalidKeySpecException("Failed to parse private key from " + path, e);
      }
    }
  }

  /**
   * Loads a PublicKey from a PEM file path.
   */
  private PublicKey loadPublicKey(Path path) throws Exception {
    if (!Files.exists(path)) {
      throw new IOException("Public key file not found: " + path);
    }
    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    Optional<byte[]> keyBytes = extractKeyMaterial(content, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);

    if (!keyBytes.isPresent()) {
      throw new IllegalArgumentException("No public key block found in " + path);
    }
    return loadPublicKeyFromBytes(keyBytes.get());
  }

  /**
   * Performs the signature test to verify key match.
   */
  private boolean checkKeyPairMatch(PrivateKey privateKey, PublicKey publicKey) throws Exception {
    byte[] challenge = new byte[1024];
    Signature sig = Signature.getInstance("SHA256withRSA");

    sig.initSign(privateKey);
    sig.update(challenge);
    byte[] signature = sig.sign();

    sig.initVerify(publicKey);
    sig.update(challenge);
    return sig.verify(signature);
  }

  /**
   * Checks if the private key and public key in SEPARATE PEM files form a valid pair.
   * Accepts File objects.
   *
   * @param privateKeyFile File object for the private key PEM file.
   * @param publicKeyFile  File object for the public key PEM file.
   * @return true if the keys match, false otherwise.
   */
  public boolean keysMatch(File privateKeyFile, File publicKeyFile) {
    if (privateKeyFile == null || publicKeyFile == null) {
      System.err.println("Error: File objects cannot be null.");
      return false;
    }
    return keysMatch(privateKeyFile.toPath(), publicKeyFile.toPath());
  }

  /**
   * Internal helper to check keysMatch using Path objects.
   */
  private boolean keysMatch(Path privateKeyPath, Path publicKeyPath) {
    try {
      PrivateKey privateKey = loadPrivateKey(privateKeyPath);
      PublicKey publicKey = loadPublicKey(publicKeyPath);
      return checkKeyPairMatch(privateKey, publicKey);
    } catch (Exception e) {
      System.err.println("Error during key matching: " + e.getClass().getSimpleName() + " - "
          + e.getMessage());
      return false;
    }
  }
}
