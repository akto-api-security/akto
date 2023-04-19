package com.akto.action;

import com.akto.dao.loaders.LoadersDao;
import com.akto.dto.loaders.Loader;
import org.bson.types.ObjectId;

import java.util.List;

public class LoadersAction extends UserAction {


    private List<Loader> loaderList;
    public String fetchActiveLoaders() {
        loaderList = LoadersDao.instance.findActiveLoaders(getSUser().getId());
        return SUCCESS.toUpperCase();
    }

    private String hexId;
    public String closeLoader() {
        LoadersDao.instance.toggleShow(new ObjectId(hexId), false);
        return SUCCESS.toUpperCase();
    }

    public List<Loader> getLoaderList() {
        return loaderList;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
}
