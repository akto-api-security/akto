package com.akto.dto.loaders;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public abstract class Loader {
    public enum Type {
        POSTMAN_UPLOAD("Postman", "Importing postman collection", "Please wait while we upload your file."),
        HAR_UPLOAD("HAR", "Uploading HAR file", "Please wait while we upload your file.");

        private final String name;
        private final String title;
        private final String subTitle;

        Type(String name, String title, String subTitle) {
            this.name = name;
            this.title = title;
            this.subTitle = subTitle;
        }

        public String getName() {
            return name;
        }

        public String getTitle() {
            return title;
        }

        public String getSubTitle() {
            return subTitle;
        }
    }

    private ObjectId id;
    private Type type;
    public static final String TYPE = "type";
    private int userId;
    public static final String USER_ID = "userId";

    private boolean show;
    public static final String SHOW = "show";

    @BsonIgnore
    private int percentage;

    @BsonIgnore
    private String hexId;

    public Loader(Type type) {
        this.type = type;
    }

    public Loader(Type type, int userId, boolean show) {
        this.type = type;
        this.userId = userId;
        this.show = show;
    }

    public abstract int getPercentage();


    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public String getHexId() {
        if (this.id == null) return null;
        return this.id.toHexString();
    }
}
