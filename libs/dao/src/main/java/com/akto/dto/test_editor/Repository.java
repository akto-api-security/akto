package com.akto.dto.test_editor;

public class Repository {
    String name;
    String branch;

    public Repository() {
    }

    public Repository(String name, String branch) {
        this.name = name;
        this.branch = branch;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public String toString() {
        String ret = "";

        if(name!=null)
            ret += name;

        if(branch!=null)
            ret += " " + branch;

        return ret;
    }

}
