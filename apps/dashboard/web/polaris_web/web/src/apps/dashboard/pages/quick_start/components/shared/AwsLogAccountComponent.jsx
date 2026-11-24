import { Box, Button, HorizontalStack, Text, TextField, VerticalStack } from "@shopify/polaris";
import { DeleteMajor, CirclePlusMajor } from "@shopify/polaris-icons";
import api from "../../api";
import { useEffect, useState } from "react";
import func from "../../../../../../util/func";

function AwsLogAccountComponent() {

    // Each row: { roleArn, regions } where regions is the comma-separated text shown in the field.
    const [rows, setRows] = useState([]);

    const fetchData = async () => {
        const resp = await api.fetchAwsAccountIdsForApiGatewayLogging();
        if (resp && resp.awsApiGConnectorRoleRegions && resp.awsApiGConnectorRoleRegions.length > 0) {
            setRows(resp.awsApiGConnectorRoleRegions.map((r) => ({
                roleArn: r.roleArn || "",
                regions: (r.regions || []).join(","),
            })));
        } else if (resp && resp.awsAccountIds) {
            // Saved before regions existed: one row per role, no regions.
            setRows(resp.awsAccountIds.split(",").map((arn) => arn.trim()).filter((arn) => arn)
                .map((arn) => ({ roleArn: arn, regions: "" })));
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    function handleUpdate(targetIndex, field, value) {
        setRows(prev => prev.map((row, index) => index === targetIndex ? { ...row, [field]: value } : row));
    }

    function handleAdd() {
        setRows(prev => [...prev, { roleArn: "", regions: "" }]);
    }

    function handleRemove(removeIndex) {
        setRows(prev => prev.filter((_, index) => index !== removeIndex));
    }

    const saveFunction = async () => {
        const roleRegions = rows
            .filter((row) => row.roleArn.trim())
            .map((row) => ({
                roleArn: row.roleArn.trim(),
                regions: row.regions.split(",").map((r) => r.trim()).filter((r) => r),
            }));
        await api.addAwsAccountIdsForApiGatewayLogging(roleRegions)
        func.setToast(true, false, "Saved Successfully")
    }

    return (
        <Box paddingBlockStart={4}>
            <VerticalStack gap={4}>
                <Text >Input the AWS Role ARNs, for which the API gateway logging service will be scraping the logs, and the regions to read with each. Leave regions empty to use the connector's own region.</Text>
                {rows.map((row, index) => (
                    <HorizontalStack key={index} gap={2} wrap={false} blockAlign="end">
                        <div style={{ flex: 2 }}>
                            <TextField
                                label={index === 0 ? "AWS Role ARN" : ""}
                                placeholder="arn:aws:iam::111111111111:role/akto/example"
                                value={row.roleArn}
                                onChange={(value) => handleUpdate(index, "roleArn", value)}
                                autoComplete="off"
                            />
                        </div>
                        <div style={{ flex: 1 }}>
                            <TextField
                                label={index === 0 ? "Regions" : ""}
                                placeholder="us-east-1,eu-west-1"
                                value={row.regions}
                                onChange={(value) => handleUpdate(index, "regions", value)}
                                autoComplete="off"
                            />
                        </div>
                        <Button icon={DeleteMajor} onClick={() => handleRemove(index)} plain />
                    </HorizontalStack>
                ))}
                <div>
                    <Button icon={CirclePlusMajor} onClick={handleAdd} plain>Add role</Button>
                </div>
                <div>
                    <Button primary onClick={() => saveFunction()}>Save</Button>
                </div>
            </VerticalStack>
        </Box>
    )
}

export default AwsLogAccountComponent;
