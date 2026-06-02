package com.akto.dao;

import com.akto.dto.BlockedToken;
import com.mongodb.client.model.Filters;

public class BlockedTokenDao extends CommonContextDao<BlockedToken> {

    public static final BlockedTokenDao instance = new BlockedTokenDao();

    @Override
    public String getCollName() {
        return "blocked_tokens";
    }

    @Override
    public Class<BlockedToken> getClassT() {
        return BlockedToken.class;
    }

    public BlockedToken findByToken(String token) {
        return instance.findOne(Filters.eq(BlockedToken.TOKEN, token));
    }
}
