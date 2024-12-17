package com.akto.threat.detection.session_factory;

import com.akto.threat.detection.db.entity.MaliciousEventEntity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

public class SessionFactoryUtils {

  public static SessionFactory createFactory() {
    final String url = System.getenv("AKTO_THREAT_DETECTION_POSTGRES");
    final String user = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_USER");
    final String password = System.getenv("AKTO_THREAT_DETECTION_POSTGRES_PASSWORD");

    final Configuration cfg = new Configuration();
    cfg.setProperty("hibernate.connection.url", url);
    cfg.setProperty("hibernate.connection.user", user);
    cfg.setProperty("hibernate.connection.password", password);
    cfg.setProperty("dialect", "org.hibernate.dialect.PostgreSQL92Dialect");
    cfg.setProperty("connection.driver_class", "org.postgresql.Driver");
    cfg.setProperty("show_sql", "false");
    cfg.setProperty("format_sql", "false");

    cfg.addAnnotatedClass(MaliciousEventEntity.class);

    return cfg.buildSessionFactory(
        new StandardServiceRegistryBuilder().applySettings(cfg.getProperties()).build());
  }
}
