package com.akto.action.threat_detection.utils;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ThreatsUtils {

    public static List<String> getTemplates(List<String> latestAttack) {
        Set<String> contextTemplatesForAccount = FilterYamlTemplateDao.getContextTemplatesForAccount(Context.accountId.get(), Context.contextSource.get());

        if(latestAttack == null || latestAttack.isEmpty()) {
            return new ArrayList<>(contextTemplatesForAccount);
        }


        return latestAttack.stream().filter(contextTemplatesForAccount::contains).collect(Collectors.toList());
    }
}
