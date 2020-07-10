package daq.pubber;

import java.util.Arrays;

public class JwtAuthorization {
  public String authorization;

  public JwtAuthorization(String jwtToken) {
    authorization = jwtToken;
  }
}
