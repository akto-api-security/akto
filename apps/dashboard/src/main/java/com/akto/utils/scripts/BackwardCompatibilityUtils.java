package com.akto.utils.scripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class BackwardCompatibilityUtils {

    public static void moveUserDataFromModuleInfoToAgenticUsers() {
        List<ModuleInfo> moduleInfos = ModuleInfoDao.instance.findAll(Filters.eq(
            ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD
        ), Projections.include(
            ModuleInfo.NAME,
            ModuleInfo.ADDITIONAL_DATA
        ));

        Map<String,Set<String>> userNameToDevicesMap = new HashMap<>();
        Map<String,Pair<String,String>> userNameToRoleAndTeamMap = new HashMap<>();

        for (ModuleInfo moduleInfo : moduleInfos) {
            String name = moduleInfo.getName();
            String userName = "";
            if(name == null || name.isEmpty() || !name.contains("-") || moduleInfo.getAdditionalData() == null) continue;
            if(moduleInfo.getAdditionalData().containsKey("username")) {
                userName = (String) moduleInfo.getAdditionalData().get("username");
            }
            
            if(!userNameToDevicesMap.containsKey(userName)) {
                userNameToDevicesMap.put(userName, new HashSet<>());
            }
            if(moduleInfo.getAdditionalData().containsKey("deviceId")) {
                String deviceId = (String) moduleInfo.getAdditionalData().get("deviceId");
                userNameToDevicesMap.get(userName).add(deviceId);
            }

            if(moduleInfo.getAdditionalData().containsKey("userRole") && moduleInfo.getAdditionalData().containsKey("team")) {
                String userRole = (String) moduleInfo.getAdditionalData().get("userRole");
                String team = (String) moduleInfo.getAdditionalData().get("team");
                userNameToRoleAndTeamMap.put(userName, new Pair<>(userRole, team));
            }   
        }

        List<AgenticUsers> agenticUsers = new ArrayList<>();

        for (Map.Entry<String,Set<String>> entry : userNameToDevicesMap.entrySet()) {
            Set<String> devices = entry.getValue();
            Pair<String,String> roleAndTeam = userNameToRoleAndTeamMap.getOrDefault(entry.getKey(), new Pair<>("", ""));
            String userRole = roleAndTeam.getFirst();
            String team = roleAndTeam.getSecond();

            AgenticUsers agenticUser = new AgenticUsers();
            agenticUser.setUserName(entry.getKey());
            agenticUser.setDevices(new ArrayList<>(devices));
            agenticUser.setUserRole(userRole);
            agenticUser.setTeamName(team);
            agenticUser.setLastUpdatedAt(Context.now());
            agenticUsers.add(agenticUser);
        }
        if(agenticUsers.isEmpty()) return;
        AgentUsersDao.instance.insertMany(agenticUsers);
    }

}
