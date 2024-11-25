package com.akto.threat.detection.actor;

import com.akto.dto.HttpResponseParams;

import java.util.Optional;

public interface ActorGenerator {

  Optional<String> generate(HttpResponseParams responseParams);
}
