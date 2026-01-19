import { Box, Button, Text, TextField, VerticalStack } from "@shopify/polaris";
import api from "../../api";
import { useEffect, useState } from "react";
import func from "../../../../../../util/func";

function AwsLogAccountComponent() {

    const [accountIds, setAccountIds] = useState('');

    const fetchData = async () => {
        const resp = await api.fetchAwsAccountIdsForApiGatewayLogging();
        if (resp && resp.awsAccountIds) {
            setAccountIds(resp.awsAccountIds);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const saveFunction = async () => {
        await api.addAwsAccountIdsForApiGatewayLogging(accountIds)
        func.setToast(true, false, "Saved Successfully")
    }

    return (
        <Box paddingBlockStart={4}>
            <VerticalStack gap={4}>
                <Text >Input the AWS Role ARNs, for which the API gateway logging service will be scraping the logs</Text>
                <TextField
                    label="AWS Role ARNs"
                    placeholder="Role ARNs (comma separated)"
                    value={accountIds}
                    onChange={(value) => setAccountIds(value)}
                    multiline
                    autoComplete="off"
                    maxHeight={200}
                />
                <Button primary onClick={() => saveFunction()}>Save</Button>
            </VerticalStack>
        </Box>
    )
}

export default AwsLogAccountComponent;