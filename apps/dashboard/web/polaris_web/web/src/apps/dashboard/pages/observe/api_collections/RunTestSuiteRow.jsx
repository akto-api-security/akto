import { VerticalStack, Modal, TextField, Button, Text, HorizontalStack, Collapsible, Badge, Pagination, TextContainer, Icon, Scrollable, Checkbox, Box, Tooltip, Card, MediaCard } from "@shopify/polaris";
import { useState } from "react";

function RunTestSuiteRow({data,checkifSelected,checkedSelected,handleTestSuiteSelection,checkDisableTestSuite }) {
    const [toggle, setToggle] = useState(true);
    const rowName = data.rowName;

    function renderTestSuites(data) {
        const formattedName = data.name.replaceAll("_", " ");
        return (
            <div className="testSuiteCard" style={{ marginLeft: "0.15rem" }}>
                <Box minWidth="300px" maxWidth="300px" borderRadius={2} borderStyle="solid" insetInlineEnd={1}>
                    <VerticalStack>
                        <div >
                            <Box paddingBlockStart={2} paddingBlockEnd={2} paddingInlineStart={4} paddingInlineEnd={4} borderRadiusEndStart={2} borderRadiusEndEnd="2" borderColor="border">
                                <Checkbox
                                    label={
                                        <Tooltip content={formattedName}>
                                            <Text variant="headingSm" fontWeight="medium" truncate={true}>{formattedName}</Text>
                                        </Tooltip>
                                    }
                                    helpText={checkifSelected(data)}
                                    onChange={() => { handleTestSuiteSelection(data) }}
                                    checked={checkedSelected(data)}
                                    disabled={checkDisableTestSuite(data)}
                                />

                            </Box>
                        </div>
                    </VerticalStack>
                </Box>
            </div>
        );
    }

    return (
        <VerticalStack>
            <HorizontalStack align="start">
                <div className="testSuiteDisclosureButton" style={{ paddingBottom: "0.5rem" }}>
                    <Button
                        onClick={() => setToggle(!toggle)}
                        ariaExpanded={open}
                        ariaControls="basic-collapsible"
                        plain
                        disclosure
                    >
                        <span style={{ fontWeight: "550", color: " #202223" }}>
                            {rowName} <span style={{ paddingLeft: "0.2rem" }}> </span>
                        </span>
                        <Badge>{data?.testSuite?.length}</Badge>
                    </Button>
                </div>
            </HorizontalStack>
            <Collapsible
                open={toggle}
                id="basic-collapsible"
                transition={{ duration: "500ms", timingFunction: "ease-in-out" }}
                expandOnPrint
            >
                <HorizontalStack gap={4} align={"start"} blockAlign={"center"}>

                    {

                        data?.testSuite?.map((val) => (
                            renderTestSuites(val)
                        ))
                    }


                </HorizontalStack>
            </Collapsible>

        </VerticalStack>
    );
}


export default RunTestSuiteRow;