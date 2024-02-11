package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.ArrayList;

@BsonDiscriminator
public class BlockContent extends Content {

    ArrayList<Content> contents = new ArrayList<>();
    int[] margins = null;

    public BlockContent() {
    }

    public BlockContent(int[] margins, ArrayList<Content> contents) {
        this.contents = contents;
        this.margins = margins;
    }

    public BlockContent(int[] margins, Content... arrContents) {
        for(Content content: arrContents) {
            this.contents.add(content);
        }
        this.margins = margins;
    }

    public ArrayList<Content> getContents() {
        return contents;
    }

    public void setContents(ArrayList<Content> contents) {
        this.contents = contents;
    }

    public int[] getMargins() {
        return margins;
    }

    public void setMargins(int[] margins) {
        this.margins = margins;
    }

    @Override
    public Type type() {
        return Type.BLOCK;
    }
}
