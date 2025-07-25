import { Card, Button, HorizontalStack, Text } from "@shopify/polaris";
import { ChevronUpMinor, ChevronDownMinor } from '@shopify/polaris-icons';
import { useState } from "react";

export default function IssuesGraphsGroup({
  children,
  heading = "Issues summary"
}) {
  const [collapsed, setCollapsed] = useState(false);

  const childrenArray = Array.isArray(children) ? children : [children];

  return (
    <Card>
      <HorizontalStack align="space-between" blockAlign="center">
        <Text variant="headingMd">{heading}</Text>
        <Button
          plain
          icon={collapsed ? ChevronDownMinor : ChevronUpMinor}
          onClick={() => setCollapsed((prev) => !prev)}
        />
      </HorizontalStack>
      {!collapsed && (
        <div style={{ marginTop: 16 }}>
          {childrenArray.map((child, idx) => (
            <div key={idx} style={{marginTop: idx > 0 ? 24 : 0}}>
              {child}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
} 