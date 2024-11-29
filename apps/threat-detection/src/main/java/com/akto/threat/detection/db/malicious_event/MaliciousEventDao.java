package com.akto.threat.detection.db.malicious_event;

import com.akto.dto.type.URLMethods;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MaliciousEventDao {

  private final Connection conn;
  private static final int BATCH_SIZE = 50;

  public MaliciousEventDao(Connection conn) {
    this.conn = conn;
  }

  public void batchInsert(List<MaliciousEventModel> events) throws SQLException {
    String sql =
        "INSERT INTO threat_detection.malicious_event (id, actor, filter_id, url, method, timestamp, orig, ip, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    conn.setAutoCommit(false);
    for (int i = 0; i < events.size(); i++) {
      MaliciousEventModel event = events.get(i);
      PreparedStatement stmt = this.conn.prepareStatement(sql);
      stmt.setString(1, event.getId());
      stmt.setString(2, event.getActor());
      stmt.setString(3, event.getFilterId());
      stmt.setString(4, event.getUrl());
      stmt.setString(5, event.getMethod().name());
      stmt.setLong(6, event.getTimestamp());
      stmt.setString(7, event.getOrig());
      stmt.setString(8, event.getIp());

      stmt.addBatch();

      if (i % BATCH_SIZE == 0 || i == events.size() - 1) {
        stmt.executeBatch();
        stmt.clearBatch();
      }
    }

    conn.commit();
  }

  public List<MaliciousEventModel> findGivenActorAndFilterId(
      String actor, String filterId, int limit) throws SQLException {
    String sql =
        "SELECT * FROM threat_detection.malicious_event WHERE actor = ? AND filter_id = ? LIMIT ?";
    PreparedStatement stmt = this.conn.prepareStatement(sql);
    stmt.setString(1, actor);
    stmt.setString(2, filterId);
    stmt.setInt(3, limit);
    try (ResultSet rs = stmt.executeQuery()) {
      List<MaliciousEventModel> models = new ArrayList<>();
      while (rs.next()) {
        MaliciousEventModel model =
            MaliciousEventModel.newBuilder()
                .setId(rs.getString("id"))
                .setActorId(rs.getString("actor"))
                .setFilterId(rs.getString("filter_id"))
                .setUrl(rs.getString("url"))
                .setMethod(URLMethods.Method.fromString(rs.getString("method")))
                .setTimestamp(rs.getLong("timestamp"))
                .setOrig(rs.getString("orig"))
                .setIp(rs.getString("ip"))
                .setCreatedAt(rs.getDate("created_at").toLocalDate())
                .build();
        models.add(model);
      }
      return models;
    }
  }

  public int countTotalMaliciousEventGivenActorAndFilterId(String actor, String filterId)
      throws SQLException {
    String sql =
        "SELECT COUNT(*) FROM threat_detection.malicious_event WHERE actor = ? AND filter_id = ?";
    PreparedStatement stmt = this.conn.prepareStatement(sql);
    stmt.setString(1, actor);
    stmt.setString(2, filterId);
    try (ResultSet rs = stmt.executeQuery()) {
      if (rs.next()) {
        return rs.getInt(1);
      }
    }
    return 0;
  }

  public void deleteEventsBefore(LocalDate date) throws SQLException {
    String sql = "DELETE FROM threat_detection.malicious_event WHERE created_at < ?";
    PreparedStatement stmt = this.conn.prepareStatement(sql);
    stmt.setDate(1, java.sql.Date.valueOf(date));
    stmt.executeUpdate();
  }
}
