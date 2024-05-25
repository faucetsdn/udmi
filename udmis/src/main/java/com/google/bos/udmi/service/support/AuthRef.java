package com.google.bos.udmi.service.support;

public interface AuthRef {

  void authorize(String clientId, String s);

  void revoke(String clientId);
}
