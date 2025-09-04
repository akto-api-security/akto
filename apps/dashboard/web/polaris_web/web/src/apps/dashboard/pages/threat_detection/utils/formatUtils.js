import React from 'react';
import { Text } from "@shopify/polaris";

export const formatActorId = (actorId) => {
  if (!actorId) return "-";
  
  // Regular expression to validate IP address (IPv4 and IPv6)
  const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
  const ipv6Regex = /^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$/;
  
  const isValidIP = ipv4Regex.test(actorId) || ipv6Regex.test(actorId);
  
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