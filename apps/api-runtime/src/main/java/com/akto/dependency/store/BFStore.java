package com.akto.dependency.store;

import com.google.common.hash.BloomFilter;

public class BFStore extends Store{

    BloomFilter<CharSequence> bf;

    public BFStore(BloomFilter<CharSequence> bf) {
        this.bf = bf;
    }

    @Override
    public boolean contains(String val) {
        return bf.mightContain(val);
    }

    @Override
    public boolean add(String val) {
        return bf.put(val);
    }
}
