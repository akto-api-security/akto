package com.akto.action;

import com.akto.dao.TeamsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Team;
import com.akto.dto.User;

import java.util.Map;
import java.util.HashMap;

public class TeamAction extends UserAction {

    int id;
    String name;
    Map<Integer, User> users;
    Team team;

    public String fetchTeamData() {

        team = TeamsDao.instance.findOne("_id", id);

        users = new HashMap<>();

        return SUCCESS.toUpperCase();
    }

    public String saveNewTeam() {
        this.id = Context.getId();
        int userId = getSUser().getId();
        Team team = new Team(id, name, userId);
        TeamsDao.instance.insertOne(team);

        return SUCCESS.toUpperCase();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, User> getUsers() {
        return users;
    }

    public void setUsers(Map<Integer, User> users) {
        this.users = users;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
