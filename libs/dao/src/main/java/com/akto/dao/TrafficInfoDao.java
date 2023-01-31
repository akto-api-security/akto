package com.akto.dao;

import com.akto.dto.traffic.TrafficInfo;

public class TrafficInfoDao extends AccountsContextDao<TrafficInfo> {

    public static final TrafficInfoDao instance = new TrafficInfoDao();

    @Override
    public String getCollName() {
        return "traffic_info";
    }

    @Override
    public Class<TrafficInfo> getClassT() {
        return TrafficInfo.class;
    }
}
