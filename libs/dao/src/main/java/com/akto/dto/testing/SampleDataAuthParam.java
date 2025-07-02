package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.CookieTransformer;
import com.akto.util.JSONUtils;
import com.akto.util.TokenPayloadModifier;
import com.mongodb.BasicDBObject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SampleDataAuthParam extends AuthParam {
    private Location where;
    private String key;
    private String value;
    private Boolean showHeader;

    @Override
    public boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, this.value, this.where);
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, null, this.where);
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        if (this.key == null) return false;
        String k = this.key.toLowerCase().trim();

        if (where.toString().equals(AuthParam.Location.BODY.toString())) {
            BasicDBObject basicDBObject =  BasicDBObject.parse(request.getBody());
            BasicDBObject data = JSONUtils.flattenWithDots(basicDBObject);
            return data.keySet().contains(this.key);
        } else {
            Map<String, List<String>> headers = request.getHeaders();
            List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
            return headers.containsKey(k) || CookieTransformer.isKeyPresentInCookie(cookieList, k);
        }
    }

}
