package com.akto.threat.backend.router;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public interface ARouter {

  Router setup(Vertx vertx);
}
