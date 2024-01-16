package com.akto.dependency.store;

public abstract class Store {

    public abstract boolean contains(String val);

    public abstract boolean add(String val);
}
