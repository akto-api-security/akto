package com.akto.action;

import com.akto.dao.ApiSequencesDao;
import com.akto.dto.ApiSequences;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ApiSequencesAction extends UserAction {

    private List<ApiSequences> apiSequences = new ArrayList<>();

    private int apiCollectionId;


    public String fetchApiSequences() {
        this.apiSequences = ApiSequencesDao.instance.findByApiCollectionId(apiCollectionId);
        return Action.SUCCESS.toUpperCase();
    }

}
