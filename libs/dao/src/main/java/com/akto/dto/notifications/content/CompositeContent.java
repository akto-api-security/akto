package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.ArrayList;

@BsonDiscriminator
public class CompositeContent extends Content {

    ArrayList<BlockContent> contents = new ArrayList<>();

    public CompositeContent() {
    }

    public CompositeContent(ArrayList<BlockContent> contents) {
        this.contents = contents;
    }

    public CompositeContent(BlockContent... blockContents) {
        for(BlockContent blockContent: blockContents) {
            this.contents.add(blockContent);
        }
    }

    public ArrayList<BlockContent> getContents() {
        return contents;
    }

    public void setContents(ArrayList<BlockContent> contents) {
        this.contents = contents;
    }

    @Override
    public Type type() {
        return Type.COMPOSITE;
    }
}
