package com.akto.util.visitors;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public abstract class JSONVisitor {
    public void visitObj(BasicDBObject obj) {
        for(String key: obj.keySet()) {
            Object value = obj.get(key);

            this.key(key);
            if (value instanceof BasicDBObject) {   
                this.vObj();
                this.visitObj((BasicDBObject) value);
            } else if (value instanceof BasicDBList) {
                this.visitList((BasicDBList) value);
            } else {
                this.visitTerminal(value);
            }
        }
    }

    public void visitList(BasicDBList list) {
        for(Object o: list) {
            if (o instanceof BasicDBObject) {   
                this.vObj();
                this.visitObj((BasicDBObject) o);
            } else {
                this.visitTerminal(o);
            }
        }
    }

    public void visitTerminal(Object value) {
        this.vVal(value);
    }

    abstract public void key(String key);
    abstract public void vObj();
    abstract public void vList();
    abstract public void vVal(Object val);

}
