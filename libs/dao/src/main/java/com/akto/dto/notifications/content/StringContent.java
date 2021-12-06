package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class StringContent extends Content {
    String str;
    int fontSize;
    String fontColor;

    public StringContent() {}

    public StringContent(String str, int fontSize, String fontColor) {
        this.str = str;
        this.fontSize = fontSize;
        this.fontColor = fontColor;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    @Override
    public Type type() {
        return Type.STRING;
    }
}