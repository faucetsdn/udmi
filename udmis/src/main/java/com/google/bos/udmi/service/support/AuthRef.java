package com.google.bos.udmi.service.support;

import java.util.List;

/**
 * Interface for interaction with auth control over broker connections.
 */
public interface AuthRef {

  void authorize(String clientId, String s);

  void revoke(String clientId);
}
