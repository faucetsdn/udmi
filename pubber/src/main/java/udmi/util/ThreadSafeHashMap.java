package udmi.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A thread-safe HashMap implementation that returns copies of collections for read/iteration
 * operations (entrySet, keySet, values) to prevent ConcurrentModificationException during Jackson
 * serialization/conversion in concurrent environments.
 */
public class ThreadSafeHashMap<K, V> extends HashMap<K, V> {

  public ThreadSafeHashMap() {
    super();
  }

  public ThreadSafeHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ThreadSafeHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public ThreadSafeHashMap(Map<? extends K, ? extends V> m) {
    super(m);
  }

  @Override
  public synchronized V put(K key, V value) {
    return super.put(key, value);
  }

  @Override
  public synchronized V remove(Object key) {
    return super.remove(key);
  }

  @Override
  public synchronized boolean remove(Object key, Object value) {
    return super.remove(key, value);
  }

  @Override
  public synchronized void clear() {
    super.clear();
  }

  @Override
  public synchronized void putAll(Map<? extends K, ? extends V> m) {
    super.putAll(m);
  }

  @Override
  public synchronized V get(Object key) {
    return super.get(key);
  }

  @Override
  public synchronized boolean containsKey(Object key) {
    return super.containsKey(key);
  }

  @Override
  public synchronized boolean containsValue(Object value) {
    return super.containsValue(value);
  }

  @Override
  public synchronized int size() {
    return super.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  public synchronized Set<Entry<K, V>> entrySet() {
    return new HashSet<>(super.entrySet());
  }

  @Override
  public synchronized Set<K> keySet() {
    return new HashSet<>(super.keySet());
  }

  @Override
  public synchronized Collection<V> values() {
    return new ArrayList<>(super.values());
  }

  // --- Java 8+ Modern Map Methods ---

  @Override
  public synchronized V putIfAbsent(K key, V value) {
    return super.putIfAbsent(key, value);
  }

  @Override
  public synchronized boolean replace(K key, V oldValue, V newValue) {
    return super.replace(key, oldValue, newValue);
  }

  @Override
  public synchronized V replace(K key, V value) {
    return super.replace(key, value);
  }

  @Override
  public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return super.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public synchronized V computeIfPresent(K key,
      BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return super.computeIfPresent(key, remappingFunction);
  }

  @Override
  public synchronized V compute(K key,
      BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return super.compute(key, remappingFunction);
  }

  @Override
  public synchronized V merge(K key, V value,
      BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return super.merge(key, value, remappingFunction);
  }

  @Override
  public synchronized void forEach(BiConsumer<? super K, ? super V> action) {
    super.forEach(action);
  }

  @Override
  public synchronized void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    super.replaceAll(function);
  }
}
