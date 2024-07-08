package com.akto.dao.settings;

import com.akto.dao.CommonContextDao;
import com.akto.dto.settings.DataControlSettings;

public class DataControlSettingsDao extends CommonContextDao<DataControlSettings> {

    public static final DataControlSettingsDao instance = new DataControlSettingsDao();

    @Override
    public String getCollName() {
        return "data_control_settings";
    }

    @Override
    public Class<DataControlSettings> getClassT() {
        return DataControlSettings.class;
    }
}
