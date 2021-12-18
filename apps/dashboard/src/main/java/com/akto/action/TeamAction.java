package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamAction extends UserAction {

    int id;
    BasicDBList users;

    public String fetchTeamData() {
        List<RBAC> allRoles = RBACDao.instance.findAll(new BasicDBObject());

        Map<Integer, RBAC> userToRBAC = new HashMap<>();
        for(RBAC rbac: allRoles) {
            userToRBAC.put(rbac.getUserId(), rbac);
        }   

        users = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
        for(Object obj: users) {
            BasicDBObject userObj = (BasicDBObject) obj;
            RBAC rbac = userToRBAC.get(userObj.getInt("id"));
            String status = rbac == null ? "Member" : rbac.getRole().name();
            userObj.append("role", status);
        }

        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(new BasicDBObject());

        for(PendingInviteCode pendingInviteCode: pendingInviteCodes) {
            users.add(
                new BasicDBObject("id", pendingInviteCode.getIssuer())
                .append("login", pendingInviteCode.getInviteeEmailId())
                .append("name", "-")
                .append("role", "Invitation sent")
            );
        }

        return SUCCESS.toUpperCase();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public BasicDBList getUsers() {
        return users;
    }

    public void setUsers(BasicDBList users) {
        this.users = users;
    }

}
