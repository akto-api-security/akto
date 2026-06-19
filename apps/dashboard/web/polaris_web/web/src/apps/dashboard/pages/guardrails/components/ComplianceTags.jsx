import { Avatar, Divider, HorizontalStack, Tag, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";

const ComplianceTags = ({ complianceMap, showDivider = true }) => {
    if (!complianceMap || Object.keys(complianceMap).length === 0) return null;

    return (
        <>
            {showDivider && <Divider />}
            <VerticalStack gap={"2"}>
                <Text variant="headingMd" fontWeight="bold">Compliance</Text>
                <HorizontalStack gap={"2"} wrap>
                    {Object.entries(complianceMap).flatMap(([framework, clauses], idx) => {
                        const clauseList = Array.isArray(clauses) && clauses.length > 0 ? clauses : [null];
                        return clauseList.map((clause, cIdx) => (
                            <Tag key={`${idx}-${cIdx}`}>
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Avatar
                                        source={func.getComplianceIcon(framework)}
                                        shape="square"
                                        size="extraSmall"
                                    />
                                    <Text variant="bodySm" fontWeight="medium">
                                        {clause ? `${framework} - ${clause}` : framework}
                                    </Text> 
                                </HorizontalStack>
                            </Tag>
                        ));
                    })}
                </HorizontalStack>
            </VerticalStack>
        </>
    );
};

export default ComplianceTags;
