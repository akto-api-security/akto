package com.akto.dto.type;

import java.util.Arrays;
import java.util.UUID;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;

import com.akto.util.filter.DictionaryFilter;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.types.ObjectId;

@BsonDiscriminator
public class URLTemplate {

    /**
     * Result of template matching indicating URL pattern and method match status
     */
    public enum MatchResult {
        NO_MATCH,                    // URL pattern doesn't match
        URL_MATCH_METHOD_MISMATCH,   // URL pattern matches but method doesn't
        FULL_MATCH                   // Both URL pattern and method match
    }

    @BsonId
    String id;
    int creationTs;
    int lastUpdateTs;
    String[] tokens;
    SuperType[] types;
    Method method;

    public URLTemplate() {
    }

    public URLTemplate(String[] tokens, SuperType[] types, Method method) {
        this.tokens = tokens;
        this.types = types;
        this.id = UUID.randomUUID().toString();
        this.creationTs = Context.now();
        this.lastUpdateTs = creationTs;
        this.method = method;
    }

    /**
     * Helper to check exact string match between URL and template (optimization).
     * @param url The URL to check
     * @param urlMethod The HTTP method
     * @return true if exact match, false otherwise
     */
    private boolean exactStringMatch(String url, Method urlMethod) {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        if (url.endsWith("/")) url = url.substring(0, url.length()-1);

        String tempUrl = this.getTemplateString();
        if (tempUrl.startsWith("/")) tempUrl = tempUrl.substring(1, tempUrl.length());
        if (tempUrl.endsWith("/")) tempUrl = tempUrl.substring(0, tempUrl.length()-1);

        String a = url + " " + urlMethod.name();
        String b = tempUrl + " " + this.method.name();

        return a.equals(b);
    }

    public boolean match(String url, Method urlMethod) {

        if (url.startsWith("/")) url = url.substring(1, url.length());
        if (url.endsWith("/")) url = url.substring(0, url.length()-1);

        String tempUrl = this.getTemplateString();
        if (tempUrl.startsWith("/")) tempUrl = tempUrl.substring(1, tempUrl.length());
        if (tempUrl.endsWith("/")) tempUrl = tempUrl.substring(0, tempUrl.length()-1);

        String a = url + " " + urlMethod.name();
        String b = tempUrl + " " + this.method.name();
        // Optimization: Try exact string match first
        if (a.equals(b)) {
            return true;
        }

        // Fall back to token-based matching
        String[] thatTokens = url.split("/");
        return match(thatTokens, urlMethod);
    }

    public boolean match(URLStatic urlStatic) {
        return this.match(urlStatic.getUrl(), urlStatic.getMethod());
    }

    public boolean match(String[] url, Method urlMethod) {
        if (urlMethod != method) {
            return false;
        }
        return matchTokens(url);
    }

    /**
     * Check if URL tokens match this template pattern (ignoring method).
     * Accepts URL string, normalizes and splits it.
     */
    public boolean matchTokens(String url) {
        // Normalize URL - remove leading/trailing slashes
        if (url.startsWith("/")) url = url.substring(1);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        String[] urlTokens = url.split("/");
        return matchTokens(urlTokens);
    }

    /**
     * Match URL against this template and return detailed result.
     * Checks URL pattern first, then method - enables single-pass matching.
     * Includes exact string match optimization before token-based matching.
     *
     * @param url The URL to match
     * @param urlMethod The HTTP method to match
     * @return MatchResult indicating the match status
     */
    public MatchResult matchTemplate(String url, Method urlMethod) {
        // Optimization: Try exact string match first (faster than token matching)
        if (exactStringMatch(url, urlMethod)) {
            return MatchResult.FULL_MATCH;
        }

        // Fall back to token-based matching for parameterized URLs
        // Step 1: Check if URL pattern matches
        if (!matchTokens(url)) {
            return MatchResult.NO_MATCH;
        }

        // Step 2: URL pattern matched, now check method
        if (this.method != urlMethod) {
            return MatchResult.URL_MATCH_METHOD_MISMATCH;
        }

        return MatchResult.FULL_MATCH;
    }

    /**
     * Check if URL tokens match this template pattern (ignoring method).
     * Extracted for reusability.
     */
    public boolean matchTokens(String[] url) {
        String[] thatTokens = url;
        if (thatTokens.length != this.tokens.length) return false;

        if(HttpResponseParams.isGraphQLEndpoint(Arrays.toString(url))) {
            return false;
        }

        for (int i = 0; i < thatTokens.length; i++) {
            String thatToken = thatTokens[i];
            String thisToken = this.tokens[i];

            if (thisToken == null) {
                SuperType type = types[i];
                if (DictionaryFilter.isEnglishWord(thatToken)) return false;
                switch(type) {
                    case BOOLEAN:
                        if (!"true".equals(thatToken.toLowerCase()) && !"false".equals(thatToken.toLowerCase())) return false;
                        break;
                    case INTEGER:
                        if (thatToken.charAt(0) == '+') {
                            thatToken = thatToken.substring(1);
                        }
                        if (!NumberUtils.isParsable(thatToken) || thatToken.contains(".")) return false;
                        break;
                    case FLOAT:
                        if (!NumberUtils.isParsable(thatToken) || !thatToken.contains(".")) return false;
                        break;
                    case OBJECT_ID:
                        if (!ObjectId.isValid(thatToken)) return false;
                        break;
                    default:
                        continue;

                }

            } else {
                if (!thisToken.equals(thatToken)) {
                    return false;
                }
            }
        }

        return true;
    }

    public String getTemplateString() {
        String str = "";
        for(int i = 0;i < tokens.length; i++) {
            if (i > 0) {
                str += "/";
            } else if (i == 0 && tokens[i] != null && !tokens[i].startsWith("http")) {
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

    public Method getMethod() {
        return this.method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return "{" +
            " tokens='" + getTokens() + "'" +
            ", types='" + getTypes() + "'" +
            ", id='" + getId() + "'" +
            ", creationTs='" + getCreationTs() + "'" +
            ", lastUpdateTs='" + getLastUpdateTs() + "'" +                        
            ", method='" + getMethod() + "'" +                        
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
        
        if (that.getMethod() != this.getMethod()) {
            return false;
        }

        for(int i = 0; i < tokens.length; i ++) {
            if(that.tokens[i] == null ? ( this.types[i]==null || this.types[i] != that.types[i] ) : ( this.tokens[i]==null || !this.tokens[i].equals(that.tokens[i]) ) ){
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

        return ret + method.hashCode();
    }
}
