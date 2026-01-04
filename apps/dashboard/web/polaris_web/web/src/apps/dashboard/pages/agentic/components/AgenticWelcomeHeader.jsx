import { Text, Box, HorizontalStack } from '@shopify/polaris';

function AgenticWelcomeHeader({ username }) {
  const name = username || 'User';

  return (
    <Box style={{ marginBottom: '24px', display: 'flex', justifyContent: 'center' }}>
      <HorizontalStack gap="3" blockAlign="center">
        <Box style={{ width: '20px', height: '20px' }}>
          <img
            src="/public/akto.svg"
            alt="Akto"
            style={{ width: '100%', height: '100%', display: 'block' }}
          />
        </Box>
        <Text variant="headingLg" as="h1" fontWeight="semibold">
          Hi {name}, Welcome back!
        </Text>
      </HorizontalStack>
    </Box>
  );
}

export default AgenticWelcomeHeader;
