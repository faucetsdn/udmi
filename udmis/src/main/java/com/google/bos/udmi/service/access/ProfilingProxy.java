package com.google.bos.udmi.service.access;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.bos.udmi.service.pod.ContainerProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to handling profiling execution through a use of a java proxy.
 */
public class ProfilingProxy<T> implements InvocationHandler {

  private final T provider;
  private final ContainerProvider container;
  private String providerName;

  private ProfilingProxy(ContainerProvider container, T provider) {
    this.provider = provider;
    this.container = container;
    providerName = provider.getClass().getSimpleName();
  }

  /**
   * Create a new profiling instance for the given actual provider object.
   */
  public static <T> T create(ContainerProvider container, T provider, int profileSec) {
    checkArgument(profileSec >= 0, "Illegal profile period " + profileSec);
    if (profileSec == 0) {
      return provider;
    }

    Object[] objects = getAllInterfaces(provider.getClass()).toArray();
    Class<?>[] interfaces = Arrays.copyOf(objects, objects.length, Class[].class);
    //noinspection unchecked
    return (T) Proxy.newProxyInstance(provider.getClass().getClassLoader(),
        interfaces, new ProfilingProxy<T>(container, provider));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] objects) throws Throwable {
    Instant start = Instant.now();
    try {
      return method.invoke(provider, objects);
    } finally {
      double durationSec = Duration.between(start, Instant.now()).toMillis() / 1000.0;
      container.debug("Method %s#%s took %.03f", providerName, method.getName(), durationSec);
    }
  }

  private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
    Class<?>[] interfaces = clazz.getInterfaces();

    Set<Class<?>> result = new HashSet<>(Arrays.asList(interfaces));

    Arrays.asList(interfaces).forEach(iface -> result.addAll(getAllInterfaces(iface)));
    ifNotNullThen(clazz.getSuperclass(), sclass -> result.addAll(getAllInterfaces(sclass)));

    return result;
  }

}
