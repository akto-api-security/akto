package com.akto.dto.loaders;

public class PostmanUploadLoader extends NormalLoader {

    public PostmanUploadLoader() {
        super(Type.POSTMAN_UPLOAD);
    }

    public PostmanUploadLoader(int userId, int currentCount, int totalCount, boolean show) {
        super(Type.POSTMAN_UPLOAD, userId, currentCount, totalCount, show);
    }
}
