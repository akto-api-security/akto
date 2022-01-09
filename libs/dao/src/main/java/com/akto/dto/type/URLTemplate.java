package com.akto.dto.type;

import java.util.UUID;

import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo.SuperType;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.apache.commons.lang3.math.NumberUtils;

@BsonDiscriminator
public class URLTemplate {

    @BsonId
    String id;
    int creationTs;
    int lastUpdateTs;
    String[] tokens;
    SuperType[] types;

    public URLTemplate() {
    }

    public URLTemplate(String[] tokens, SuperType[] types) {
        this.tokens = tokens;
        this.types = types;
        this.id = UUID.randomUUID().toString();
        this.creationTs = Context.now();
        this.lastUpdateTs = creationTs;
    }

    public boolean match(String url) {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        if (url.endsWith("/")) url = url.substring(0, url.length()-1);
        String[] thatTokens = url.split("/");

        return match(thatTokens);
    }

    public boolean match(String[] url) {
        String[] thatTokens = url;
        if (thatTokens.length != this.tokens.length) return false;

        for (int i = 0; i < thatTokens.length; i++) {
            String thatToken = thatTokens[i];
            String thisToken = this.tokens[i];

            if (thisToken == null) {
                SuperType type = types[i];

                switch(type) {
                    case BOOLEAN:
                        if (!"true".equals(thatToken.toLowerCase()) && !"false".equals(thatToken.toLowerCase())) return false;
                        break;
                    case INTEGER:
                        if (!NumberUtils.isParsable(thatToken) || thatToken.contains(".")) return false;
                        break;
                    case FLOAT:
                        if (!NumberUtils.isParsable(thatToken) || !thatToken.contains(".")) return false;
                        break;
                    default:
                        return true;

                }

            } else {
                if (!thisToken.equals(thatToken)) {
                    return false;
                }
            }
        }

        return true;
    }

    public String[] getTokens() {
        return this.tokens;
    }

    public void setTokens(String[] tokens) {
        this.tokens = tokens;
    }

    public SuperType[] getTypes() {
        return this.types;
    }

    public void setTypes(SuperType[] types) {
        this.types = types;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCreationTs() {
        return this.creationTs;
    }

    public void setCreationTs(int creationTs) {
        this.creationTs = creationTs;
    }

    public int getLastUpdateTs() {
        return this.lastUpdateTs;
    }

    public void setLastUpdateTs(int lastUpdateTs) {
        this.lastUpdateTs = lastUpdateTs;
    }

    public String getTemplateString() {
        String str = "";
        for(int i = 0;i < tokens.length; i++) {
            if (i > 0) {
                str += "/";
            }
            if (tokens[i] == null) {
                str += types[i].name();
            } else {
                str += tokens[i];
            }
        }
        return str;
    }

    @Override
    public String toString() {
        return "{" +
            " tokens='" + getTokens() + "'" +
            ", types='" + getTypes() + "'" +
            ", id='" + getId() + "'" +
            ", creationTs='" + getCreationTs() + "'" +
            ", lastUpdateTs='" + getLastUpdateTs() + "'" +                        
            "}";
    }


    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof URLTemplate)) {
            return false;
        }
        URLTemplate that = (URLTemplate) o;
        
        for(int i = 0; i < tokens.length; i ++) {
            if(that.tokens[i] == null ? this.types[i] != that.types[i] : !this.tokens[i].equals(that.tokens[i])){
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int ret = 0;
        for(int i = 0; i < tokens.length; i ++) {
            if(this.tokens[i] == null) {
                ret += this.types[i].hashCode();
            } else {
                ret += this.tokens[i].hashCode();
            }
        }

        return ret;
    }
}
