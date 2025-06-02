import { Page, Box, Button } from '@shopify/polaris';
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout';

function McpSecurityPage() {
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