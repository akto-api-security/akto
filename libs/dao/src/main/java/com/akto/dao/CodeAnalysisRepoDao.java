package com.akto.dao;

import com.akto.dto.CodeAnalysisRepo;

public class CodeAnalysisRepoDao extends AccountsContextDao<CodeAnalysisRepo>{

    public static final CodeAnalysisRepoDao instance = new CodeAnalysisRepoDao();

    @Override
    public String getCollName() {
        return "code_analysis_repos";
    }

    @Override
    public Class<CodeAnalysisRepo> getClassT() {
        return CodeAnalysisRepo.class;
    }
}
