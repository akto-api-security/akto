package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.TokenPayloadModifier;

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
    boolean addAuthTokens(OriginalHttpRequest request) {
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
        return Utils.isRequestKeyPresent(this.key, request, where);
    }

}
