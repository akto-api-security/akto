package com.akto.dto;


import org.bson.types.ObjectId;

public class RBAC {

    private ObjectId id;
    private int userId;
    public static final String USER_ID = "userId";
    private Role role;
    public static final String ROLE = "role";

    public enum Role {
        ADMIN, MEMBER
    }

    public RBAC(int userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public RBAC() {
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}
