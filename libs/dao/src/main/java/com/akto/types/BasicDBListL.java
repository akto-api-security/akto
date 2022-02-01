package com.akto.types;

import com.mongodb.BasicDBList;

public class BasicDBListL extends BasicDBList {

    public BasicDBListL() {
        super();
    }

    public BasicDBListL(Object... args){ 
        super();
        for(Object arg: args) {
            this.add(arg);
        }
    }
    
}
