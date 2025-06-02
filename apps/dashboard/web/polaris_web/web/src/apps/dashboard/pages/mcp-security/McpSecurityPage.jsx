import { Page, Box } from '@shopify/polaris';
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout';

function McpSecurityPage() {
  return (
    <Page title="MCP Security" fullWidth>
      <Box width="100%">
        <EmptyScreensLayout
          iconSrc={"/public/mcp.svg"}
          headingText={"MCP Security is in beta"}
          description={"MCP Security is currently in beta. Contact our sales team to learn more about this feature and get access."}
          buttonText={"Contact Sales"}
          redirectUrl={"https://www.akto.io/api-security-demo"}
        />
      </Box>
    </Page>
  );
}

export default McpSecurityPage; 