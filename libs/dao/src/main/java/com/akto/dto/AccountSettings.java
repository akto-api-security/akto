package com.akto.dto;

import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

public class AccountSettings {
    private int id;
    private List<String> privateCidrList;

    public AccountSettings() {
    }

    public AccountSettings(int id, List<String> privateCidrList) {
        this.id = id;
        this.privateCidrList = privateCidrList;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<String> getPrivateCidrList() {
        return privateCidrList;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }
}
