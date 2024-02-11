package com.akto.oas.requests;

import java.util.ArrayList;
import java.util.List;

public class RequestTree {

    private List<BxB> functors;
    private RequestTree next;

    public RequestTree() {
    }

    public RequestTree(List<BxB> functors, RequestTree next) {
        this.functors = functors;
        this.next = next;
    }

    public RequestTree wrapWith(List<BxB> functors) {
        return new RequestTree(functors, this);
    }

    public List<BxB> composeWithNext() {
        if (next == null) {
            return this.functors;
        }
        List<BxB> ret = new ArrayList<>();
        
        for(BxB thisFunctor: functors) {
            for(BxB thatFunctor: next.getFunctors()) {
                ret.add(BxB.compose(thisFunctor, thatFunctor));
            }
        }

        return ret;
    }

    public List<BxB> composeDeep() {
        if (next == null) {
            return this.functors;
        }

        List<BxB> ret = new ArrayList<>();
        
        for(BxB thisFunctor: functors) {
            for(BxB thatFunctor: next.composeDeep()) {
                ret.add(BxB.compose(thisFunctor, thatFunctor));
            }
        }

        return ret;
    }

    public List<BxB> composeDeepReverse() {
        if (next == null) {
            return this.functors;
        }

        List<BxB> ret = new ArrayList<>();
        
        for(BxB thisFunctor: functors) {
            for(BxB thatFunctor: next.composeDeep()) {
                ret.add(BxB.compose(thatFunctor, thisFunctor));
            }
        }

        return ret;
    }

    public List<BxB> getFunctors() {
        return this.functors;
    }

    public void setFunctors(List<BxB> functors) {
        this.functors = functors;
    }

    public RequestTree getNext() {
        return this.next;
    }

    public void setNext(RequestTree next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return "{" +
            " functors='" + getFunctors() + "'" +
            ", next='" + getNext() + "'" +
            "}";
    }    
}
