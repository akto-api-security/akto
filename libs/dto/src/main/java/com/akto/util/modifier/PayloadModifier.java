package com.akto.util.modifier;

public abstract class PayloadModifier {

    // modify will return null if condition doesn't satisfy or it fails to modify it
    public abstract Object modify(String key, Object value);

}
