package com.akto.dependency.store;

import com.akto.dependency.store.Store;

import java.util.HashSet;
import java.util.Set;

public class HashSetStore extends Store {

    private final Set<String> set = new HashSet<>();
    @Override
    public boolean contains(String val) {
        return set.contains(val);
    }

    @Override
    public boolean add(String val) {
        return set.add(val);
    }
}
