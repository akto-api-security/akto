package com.akto.types;

import java.util.HashSet;
import java.util.Set;

public class CappedSet<T> {

    public static final int LIMIT = 50;

    public static<T> CappedSet<T> create(T elem) {
        CappedSet<T> ret = new CappedSet<T>();
        ret.add(elem);
        return ret;
    }
    
    Set<T> elements;

    public CappedSet(Set<T> elements) {
        this.elements = elements;
    }

    public CappedSet() {
        this(new HashSet<>());
    }

    public int count() {
        if (this.elements == null) return 0;
        return this.elements.size();
    }

    public boolean add(T t) {
        if (elements.size() >= LIMIT) return false;
        elements.add(t);
        return true;
    }

    public Set<T> getElements() {
        return elements;
    }

    public void setElements(Set<T> elements) {
        this.elements = elements;
    }
}
