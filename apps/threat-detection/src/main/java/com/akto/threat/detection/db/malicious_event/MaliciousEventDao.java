package com.akto.threat.detection.db.malicious_event;

import com.akto.dto.type.URLMethods;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class MaliciousEventDao {

  private final Connection conn;
  private static final int BATCH_SIZE = 50;

  public MaliciousEventDao(Connection conn) {
    this.conn = conn;
  }

  public void batchInsert(List<MaliciousEventModel> events) throws SQLException {
    String sql =
        "INSERT INTO threat_detection.malicious_event (id, actor_id, filter_id, url, method, timestamp, data, ip, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    conn.setAutoCommit(false);
    for (int i = 0; i < events.size(); i++) {
      MaliciousEventModel event = events.get(i);
      PreparedStatement stmt = this.conn.prepareStatement(sql);
      stmt.setString(1, event.getId());
      stmt.setString(2, event.getActorId());
      stmt.setString(3, event.getFilterId());
      stmt.setString(4, event.getUrl());
      stmt.setString(5, event.getMethod().name());
      stmt.setLong(6, event.getTimestamp());
      stmt.setString(7, event.getOrig());
      stmt.setString(8, event.getIp());
      stmt.setString(9, event.getCountry());

      stmt.addBatch();

      if (i % BATCH_SIZE == 0 || i == events.size() - 1) {
        stmt.executeBatch();
        stmt.clearBatch();
      }
    }

    conn.commit();
  }

  public Optional<MaliciousEventModel> findOne(String id) throws SQLException {
    String sql = "SELECT * FROM threat_detection.malicious_event WHERE id = ?";
    PreparedStatement stmt = this.conn.prepareStatement(sql);
    try (ResultSet rs = stmt.executeQuery()) {
      if (rs.next()) {
        MaliciousEventModel model =
            MaliciousEventModel.newBuilder()
                .setId(rs.getString("id"))
                .setActorId(rs.getString("actor_id"))
                .setFilterId(rs.getString("filter_id"))
                .setUrl(rs.getString("url"))
                .setMethod(URLMethods.Method.fromString(rs.getString("method")))
                .setTimestamp(rs.getLong("timestamp"))
                .setOrig(rs.getString("data"))
                .setIp(rs.getString("ip"))
                .setCountry(rs.getString("country"))
                .build();

        return Optional.of(model);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }
}
