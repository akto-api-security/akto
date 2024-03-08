package com.akto.types;

import java.util.ArrayList;

public class CappedList<T> {

    int limit;
    ArrayList<T> elements;
    boolean forceLatestEntry;
    int nextPos;

    public CappedList() {
    }

    public CappedList(int limit, boolean forceLatestEntry) {
        this.limit = limit;
        this.forceLatestEntry = forceLatestEntry;
        this.elements = new ArrayList<T>();
        this.nextPos = 0;
    }

    public boolean add(T t) {
        boolean isInserted = false;

        if (elements.indexOf(t) != -1) {
            return false;
        }

        if (elements.size() < limit) {
            this.elements.add(t);
            isInserted = true;
        } else if (forceLatestEntry) {
            this.elements.set(nextPos, t);
            isInserted = true;
        }

        if (isInserted) {
            this.nextPos = ((this.nextPos + 1) % limit) % limit;
        }

        return isInserted;
    }

    public ArrayList<T> get() {
        return this.elements;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public ArrayList<T> getElements() {
        return elements;
    }

    public void setElements(ArrayList<T> elements) {
        this.elements = elements;
    }

    public boolean isForceLatestEntry() {
        return forceLatestEntry;
    }

    public void setForceLatestEntry(boolean forceLatestEntry) {
        this.forceLatestEntry = forceLatestEntry;
    }

    public int getNextPos() {
        return nextPos;
    }

    public void setNextPos(int nextPos) {
        this.nextPos = nextPos;
    }
}
