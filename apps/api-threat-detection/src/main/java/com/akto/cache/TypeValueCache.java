package com.akto.cache;

import java.util.Optional;

public interface TypeValueCache<V> {

    Optional<V> get(String key);

    V getOrDefault(String key, V defaultValue);

    boolean containsKey(String key);

    void put(String key, V value);

    long size();

    void destroy();
}
