import { Page, Box, Button, Text, Card } from '@shopify/polaris';
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout';
import func from '../../../util/func';

function McpSecurityPage() {
  const hasMcpSecurityAccess = func.checkForFeatureSaas('MCP_SECURITY');

  if (hasMcpSecurityAccess) {
    // Show actual MCP Security feature content when user has access
    return (
      <Page title="MCP Security" fullWidth>
        <Box width="100%">
          <Card>
            <Box padding="4">
              <Text variant="headingLg" as="h2">MCP Security Dashboard</Text>
              <Box paddingBlockStart="4">
                <Text variant="bodyMd">
                  Welcome to MCP Security! This feature provides advanced security monitoring and control capabilities.
                </Text>
              </Box>
              {/* TODO: Add actual MCP Security feature content here */}
            </Box>
          </Card>
        </Box>
      </Page>
    );
  }

  // Show beta card when user doesn't have access
  return (
    <Page title="MCP Security" fullWidth>
      <Box width="100%">
        <EmptyScreensLayout
          iconSrc={"/public/mcp.svg"}
          headingText={"MCP Security is in beta"}
          description={"MCP Security is currently in beta. Contact our sales team to learn more about this feature and get access."}
          bodyComponent={<Button url="https://www.akto.io/api-security-demo" target="_blank" primary>Contact sales</Button>}
        />
      </Box>
    </Page>
  );
}

export default McpSecurityPage; 