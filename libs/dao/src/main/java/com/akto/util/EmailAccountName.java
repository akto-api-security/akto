package com.akto.util;

public class EmailAccountName {
    private String email;

    public EmailAccountName(String email) {
        setEmail(email);
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getAccountName()
    {
        if (email == null) {
            throw new IllegalStateException("Email has not been set.");
        }

        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        String domain = email.substring(atIndex + 1);

        return domain.split("\\.")[0];
    }

}