import React from 'react';
import { Text } from "@shopify/polaris";

// Regular expression to validate IP address (IPv4 and IPv6)
const IPV4_REGEX = /^(\d{1,3}\.){3}\d{1,3}$/;
const IPV6_REGEX = /^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$/;

export const formatActorId = (actorId) => {
  if (!actorId) return "-";
  
  const isValidIP = IPV4_REGEX.test(actorId) || IPV6_REGEX.test(actorId);
  
  if (isValidIP) {
    return (
      <Text variant="bodyMd" fontWeight="medium">
        {actorId}
      </Text>
    );
  } else {
    const truncated = actorId.length > 20 ? `${actorId.slice(0, 20)}...` : actorId;
    return (
      <Text variant="bodyMd" fontWeight="medium">
        Non IP Value({truncated})
      </Text>
    );
  }
};