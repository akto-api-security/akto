import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, HorizontalStack, Divider, Text, Button, Box, Autocomplete, TextField, HorizontalGrid } from "@shopify/polaris";
import api from "../../../pages/threat_detection/api.js";
import Dropdown from "../../../components/layouts/Dropdown.jsx";
import { DeleteMinor } from "@shopify/polaris-icons"

const ThreatActorConfigComponent = ({ title, description }) => {
    const [actorIds, setActorIds] = useState([]);
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const actorIds = response?.threatConfiguration?.actor?.actorId || [];
        setActorIds(actorIds);
    };

    const onSave = async () => {
        const payload = {
            actor: {
                actorId: actorIds
            }
        };
        await api.modifyThreatConfiguration(payload).then(() => {
            try {
                func.setToast(true, false, "Actor config saved successfully");
                fetchData()
            } catch (error) {
                func.setToast(true, true, "Error saving actor config");
            }
        });
    };

    const addActorId = () => {
        setActorIds([...actorIds, { kind: "hostname", pattern: "", key: "", type: "header" }]);
    };

    useEffect(() => {
        fetchData().then(() => {
            validateSaveButton(actorIds);
        });
    }, []);

    useEffect(() => {
        validateSaveButton(actorIds);
    }, [actorIds]);

    function TitleComponent({ title, description }) {
        return (
            <Box paddingBlockEnd="4">
                <Text variant="headingMd">{title}</Text>
                <Box paddingBlockStart="2">
                    <Text variant="bodyMd">{description}</Text>
                </Box>
            </Box>
        )
    }

    const handleInputChange = (index, field, value) => {
        setActorIds((prevActorIds) => {
            const updatedActorIds = [...prevActorIds];
            updatedActorIds[index] = { ...updatedActorIds[index], [field]: value };
            validateSaveButton(updatedActorIds);
            return updatedActorIds;
        });
    };

    const handleDelete = (index) => {
        setActorIds((prevActorIds) => {
            const updatedActorIds = [...prevActorIds];
            updatedActorIds.splice(index, 1);
            validateSaveButton(updatedActorIds);
            return updatedActorIds;
        });
    };

    const validateSaveButton = (actorIds) => {
        const hasEmptyFields = actorIds.some(actor => !actor.pattern || !actor.key);
        setIsSaveDisabled(hasEmptyFields);
    };

    const dropdownOptions = [
        { value: "hostname", label: "Hostname" },
    ];

    return (
        <LegacyCard title={<TitleComponent title={title} description={description} />}
            primaryFooterAction={{
                content: 'Save',
                onAction: onSave,
                loading: false,
                disabled: isSaveDisabled
            }}
        >
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    {actorIds.map((actor, index) => (
                        <HorizontalGrid columns={"4"} key={index} gap="4">
                            <Dropdown
                                menuItems={dropdownOptions}
                                selected={(val) => handleInputChange(index, "kind", val)}
                                label="Select Type"
                                initial={() => actor.kind}
                            />
                            <TextField
                                label="Hostname"
                                value={actor.pattern || ""}
                                onChange={(value) => handleInputChange(index, "pattern", value)}
                                placeholder="Enter hostname"
                                requiredIndicator={true}
                            />
                            <TextField
                                label="Header Name"
                                value={actor.key || ""}
                                onChange={(value) => handleInputChange(index, "key", value)}
                                placeholder="Enter header name"
                                requiredIndicator={true}
                            />
                            <Box paddingBlockStart="6" width="100px">
                                <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
                            </Box>
                        </HorizontalGrid>
                    ))}
                    <HorizontalStack align="space-between">
                        <Button onClick={addActorId}>Add Actor ID</Button>
                    </HorizontalStack>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
};

export default ThreatActorConfigComponent;