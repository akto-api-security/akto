import { useState } from "react";
import { HorizontalStack, VerticalStack, Text, Tag, Avatar, Spinner, Popover, Button, TextField } from "@shopify/polaris";
import { PlusMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import ComplianceMenu, { getCompliances } from "../../issues/IssuesPage/ComplianceMenu";

export const buildComplianceMap = (suggested, accepted) =>
    Object.keys(accepted || {}).reduce((acc, framework) => {
        acc[framework] = suggested?.[framework] || [];
        return acc;
    }, {});

const ComplianceMappingTags = ({ loading, complianceMap = {}, onRemove, onAdd }) => {
    const [addActive, setAddActive] = useState(false);
    const [filterValue, setFilterValue] = useState("");

    const frameworks = Object.keys(complianceMap);

    if (!loading && frameworks.length === 0 && !onAdd) return null;

    const availableFrameworks = getCompliances()
        .filter(frameworkName => !complianceMap[frameworkName])
        .filter(frameworkName => !filterValue || frameworkName.toLowerCase().includes(filterValue.toLowerCase()));

    const addActivator = (
        <Button plain size="slim" icon={PlusMinor} onClick={() => setAddActive(x => !x)}>
            Add
        </Button>
    );

    return (
        <VerticalStack gap="2">
            {loading ? (
                <HorizontalStack gap="2" blockAlign="center">
                    <Spinner size="small" />
                    <Text variant="bodySm" tone="subdued">Detecting compliance frameworks...</Text>
                </HorizontalStack>
            ) : (
                <>
                    {frameworks.length > 0 && (
                        <Text variant="bodySm" fontWeight="medium">Compliance frameworks supported by this guardrail:</Text>
                    )}
                    <HorizontalStack gap="2" wrap blockAlign="center">
                        {frameworks.map(frameworkName => (
                            <Tag key={frameworkName} onRemove={onRemove ? () => onRemove(frameworkName) : undefined}>
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Avatar source={func.getComplianceIcon(frameworkName)} shape="square" size="extraSmall" />
                                    {frameworkName}
                                </HorizontalStack>
                            </Tag>
                        ))}
                        {onAdd && (
                            <Popover
                                active={addActive}
                                activator={addActivator}
                                onClose={() => { setAddActive(false); setFilterValue(""); }}
                                preferredAlignment="left"
                            >
                                <Popover.Pane fixed>
                                    <Popover.Section>
                                        <TextField
                                            value={filterValue}
                                            onChange={setFilterValue}
                                            placeholder="Search frameworks..."
                                            autoComplete="off"
                                        />
                                    </Popover.Section>
                                </Popover.Pane>
                                <Popover.Pane>
                                    {availableFrameworks.length > 0 ? (
                                        <ComplianceMenu
                                            items={availableFrameworks}
                                            onSelect={(frameworkName) => { onAdd(frameworkName); setAddActive(false); setFilterValue(""); }}
                                        />
                                    ) : (
                                        <Popover.Section>
                                            <Text variant="bodySm" tone="subdued">
                                                {filterValue ? "No matching frameworks" : "All frameworks added"}
                                            </Text>
                                        </Popover.Section>
                                    )}
                                </Popover.Pane>
                            </Popover>
                        )}
                    </HorizontalStack>
                    {frameworks.length > 0 && (
                        <Text variant="bodySm" tone="subdued">
                            Compliance mappings are AI-generated suggestions and may not be fully accurate. Please review and validate them before relying on them for compliance.
                        </Text>
                    )}
                </>
            )}
        </VerticalStack>
    );
};

export default ComplianceMappingTags;
