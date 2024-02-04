package com.google.bos.udmi.service.access;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to handling profiling execution through a use of a java proxy.
 */
public class ProfilingProxy<T> implements InvocationHandler {

  private final T provider;

  private ProfilingProxy(T provider) {
    this.provider = provider;
  }

  /**
   * Create a new profiling instance for the given actual provider object.
   */
  public static <T> T create(T provider, int profileSec) {
    Object[] objects = getAllInterfaces(provider.getClass()).toArray();
    Class<?>[] interfaces = Arrays.copyOf(objects, objects.length, Class[].class);
    //noinspection unchecked
    return (T) Proxy.newProxyInstance(provider.getClass().getClassLoader(),
        interfaces, new ProfilingProxy<T>(provider));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] objects) throws Throwable {
    return method.invoke(provider, objects);
  }

  private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
    Class<?>[] interfaces = clazz.getInterfaces();

    Set<Class<?>> result = new HashSet<>(Arrays.asList(interfaces));

    Arrays.asList(interfaces).forEach(iface -> result.addAll(getAllInterfaces(iface)));
    ifNotNullThen(clazz.getSuperclass(), sclass -> result.addAll(getAllInterfaces(sclass)));

    return result;
  }

}
