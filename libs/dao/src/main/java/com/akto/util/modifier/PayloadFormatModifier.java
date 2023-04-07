package com.akto.util.modifier;

public abstract class PayloadFormatModifier {
    
    abstract public String toJSON(String orig);

    abstract public String fromJSON(String orig);

}
