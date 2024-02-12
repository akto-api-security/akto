package com.akto.dto.notifications.content;

public abstract class Visitor<T> {

    public T visit(Content content) {
        switch (content.type()) {
            case COMPOSITE:
                return visit((CompositeContent) content);
            case IMAGE:
                return visit((ImageContent) content);
            case LINKED:
                return visit((LinkedContent) content);
            case NEWLINE:
                return visit((NewlineContent) content);
            case STRING:
                return visit((StringContent) content);
            case BLOCK:
                return visit((BlockContent) content);
            default:
                throw new IllegalStateException("Found unknown type: " + content.type());
        }
    }

    public abstract T visit(CompositeContent content);
    public abstract T visit(ImageContent content);
    public abstract T visit(LinkedContent content);
    public abstract T visit(NewlineContent content);
    public abstract T visit(StringContent content);
    public abstract T visit(BlockContent content);

}
