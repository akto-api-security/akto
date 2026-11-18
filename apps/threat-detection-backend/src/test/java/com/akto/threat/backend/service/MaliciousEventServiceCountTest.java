package com.akto.threat.backend.service;

import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The Active tab total: range count minus the skills/config partitions, never a negated-regex count.
public class MaliciousEventServiceCountTest {

  private static final long RANGE_COUNT = 100;
  private static final long EXCLUDED_COUNT = 7;

  private MaliciousEventService service;
  private MaliciousEventDao dao;
  private final Document scope = new Document("contextSource", "ENDPOINT").append("status", "ACTIVE");

  private static String json(Bson filter) {
    return ((Document) filter).toJson();
  }

  @BeforeEach
  void setUp() throws Exception {
    service = mock(MaliciousEventService.class, CALLS_REAL_METHODS);
    dao = mock(MaliciousEventDao.class);
    Field field = MaliciousEventService.class.getDeclaredField("maliciousEventDao");
    field.setAccessible(true);
    field.set(service, dao);
    // the excluded-partition count is the one carrying the $or of positive patterns
    when(dao.countDocuments(anyString(), any(Bson.class), anyLong())).thenAnswer(call ->
        json(call.getArgument(1)).contains("$or") ? EXCLUDED_COUNT : RANGE_COUNT);
  }

  @Test
  void activeTabSubtractsTheExcludedPartitionsInsteadOfCountingANegatedRegex() {
    List<Document> conditions = ThreatUtils.evaluationModeConditions("ENDPOINT", "exclude", "exclude");

    assertEquals(RANGE_COUNT - EXCLUDED_COUNT, service.countWithEvaluationModes("1", scope, conditions, 1000));

    ArgumentCaptor<Bson> filters = ArgumentCaptor.forClass(Bson.class);
    verify(dao, times(2)).countDocuments(anyString(), filters.capture(), anyLong());
    for (Bson filter : filters.getAllValues()) {
      assertFalse(json(filter).contains("$not"), "no negated regex may reach Mongo: " + json(filter));
    }
    String excluded = json(filters.getAllValues().get(1));
    assertTrue(excluded.contains("skills"), excluded);
    assertTrue(excluded.contains("config"), excluded);
  }

  @Test
  void noEvaluationModeIsASingleCountOfTheScope() {
    assertEquals(RANGE_COUNT, service.countWithEvaluationModes("1", scope, Collections.emptyList(), 1000));
    verify(dao, times(1)).countDocuments(anyString(), any(Bson.class), anyLong());
  }

  @Test
  void onlyModeKeepsTheSinglePositiveCondition() {
    List<Document> conditions = ThreatUtils.evaluationModeConditions("ENDPOINT", "only", null);

    assertEquals(RANGE_COUNT, service.countWithEvaluationModes("1", scope, conditions, 1000));
    verify(dao, times(1)).countDocuments(anyString(), any(Bson.class), anyLong());
  }
}
