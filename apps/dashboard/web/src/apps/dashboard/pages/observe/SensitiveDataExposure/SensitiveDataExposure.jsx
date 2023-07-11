import { Text, Button } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"


function SensitiveDataExposure() {
    return (
        <PageWithMultipleCards
        title={
            <Text as="div" variant="headingLg">
            Sensitive data exposure
          </Text>
        }
        primaryAction={<Button primary>Create custom data types</Button>}
        />
    )
}

export default SensitiveDataExposure