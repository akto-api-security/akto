import { Text, HorizontalStack, Avatar } from '@shopify/polaris';

function AgenticWelcomeHeader({ username }) {

  return (
      <HorizontalStack align="center" gap={"4"}>
        <Avatar source="/public/akto.svg" shape="square" size="extraSmall"/>
        <Text variant="headingXl">
          Hi {username}, Welcome back!
        </Text>
      </HorizontalStack>
  );
}

export default AgenticWelcomeHeader;
