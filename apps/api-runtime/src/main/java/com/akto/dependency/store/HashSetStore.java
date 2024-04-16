package com.akto.dependency.store;

import java.util.HashSet;
import java.util.Set;

public class HashSetStore extends Store {

    int maxCount;

    public HashSetStore(int maxCount) {
        this.maxCount = maxCount;
    }

    private final Set<Integer> set = new HashSet<>();
    @Override
    public boolean contains(String val) {
        return set.contains(val.hashCode());
    }

    @Override
    public boolean add(String val) {
        if (set.size() >= maxCount) return false;
        return set.add(val.hashCode());
    }
}
