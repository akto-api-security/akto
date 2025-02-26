package com.akto.dto.testing;

import org.bson.codecs.pojo.annotations.BsonId;
import java.util.Objects;

public class Remediation {
    
    public static final String TEST_ID = "id";
    @BsonId
    public String id;
    
    public static final String REMEDIATION_TEXT = "remediationText";
    public String remediationText;

    public static final String HASH = "hash";
    public int hash;


    public Remediation() {
    }

    public Remediation(String id, String remediationText, int hash) {
        this.id = id;
        this.remediationText = remediationText;
        this.hash = hash;
    }

    public String getid() {
        return this.id;
    }

    public void setid(String id) {
        this.id = id;
    }

    public String getRemediationText() {
        return this.remediationText;
    }

    public void setRemediationText(String remediationText) {
        this.remediationText = remediationText;
    }

    public int getHash() {
        return this.hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }


    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Remediation)) {
            return false;
        }
        Remediation remediation = (Remediation) o;
        return Objects.equals(id, remediation.id) && Objects.equals(remediationText, remediation.remediationText) && hash == remediation.hash;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, remediationText, hash);
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getid() + "'" +
            ", remediationText='" + getRemediationText() + "'" +
            ", hash='" + getHash() + "'" +
            "}";
    }

}
