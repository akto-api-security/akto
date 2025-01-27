package com.akto.threat.detection.actor;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.policies.ApiAccessTypePolicy;

import java.util.List;
import java.util.Optional;

public class SourceIPActorGenerator implements ActorGenerator {

  private SourceIPActorGenerator() {}

  public static SourceIPActorGenerator instance = new SourceIPActorGenerator();

  @Override
  public Optional<String> generate(HttpResponseParams responseParams) {
    List<String> sourceIPs = ApiAccessTypePolicy.getSourceIps(responseParams);
    if (sourceIPs.isEmpty()) {
      return Optional.of(responseParams.getSourceIP());
    }

    return Optional.of(sourceIPs.get(0));
  }
}
