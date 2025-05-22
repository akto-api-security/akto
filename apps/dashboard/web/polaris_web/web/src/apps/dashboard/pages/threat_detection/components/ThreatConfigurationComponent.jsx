import { HorizontalGrid, Select, HorizontalStack, TextField, Button } from "@shopify/polaris";
import { useReducer, useEffect, useState } from "react";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import api from "../api";

function ThreatConfigurationComponent() {
    const actorOptions = [
        { label: "IP", value: "ip" },
        { label: "Header", value: "header" }
    ];
    const [selectedActor, setSelectedActor] = useReducer((_, newValue) => newValue, actorOptions[0].value);
    const [headerName, setHeaderName] = useReducer((_, newValue) => newValue, "");
    const [isSaveEnabled, setIsSaveEnabled] = useReducer((_, newValue) => newValue, false);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const actor = response?.threatConfiguration?.actor?.actorId || {};
        setSelectedActor(actor.type === "header" ? "header" : "ip");
        setHeaderName(actor.type === "header" ? actor.key : "");
        setIsSaveEnabled(actor.type === "ip" || (actor.type === "header" && actor.key?.trim() !== ""));
    };

    const onSave = async () => {
        const payload = {
            actor: {
                actorId: {
                    type: selectedActor === "header" ? "header" : "ip",
                    key: selectedActor === "header" ? headerName : "ip"
                }
            }
        };
        await api.modifyThreatConfiguration(payload);
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleHeaderNameChange = (value) => {
        setHeaderName(value);
        setIsSaveEnabled(value.trim() !== "");
    };

    return (
        <HorizontalGrid columns={1} gap={2}>
            <TitleWithInfo
                titleText={"Actor"}
                tooltipContent={"Identify clients uniquely"}
            />
            <HorizontalStack align="start" gap="4">
                <h2>Choose Actor Id</h2>
                <Select
                    options={actorOptions}
                    value={selectedActor}
                    onChange={(value) => {
                        setSelectedActor(value);
                        if (value !== "header") {
                            setHeaderName("");
                            setIsSaveEnabled(true);
                        }
                    }}
                />
            </HorizontalStack>
            {selectedActor === "header" && (
                <div style={{ marginTop: "16px" }}>
                    <HorizontalStack align="start" gap="4">
                        <h2>
                            Header Name <span style={{ color: "red" }}>*</span>
                        </h2>
                        <TextField
                            label=""
                            value={headerName}
                            onChange={handleHeaderNameChange}
                            autoComplete="off"
                        />
                    </HorizontalStack>
                </div>
            )}
            <div style={{ textAlign: "right", padding: "16px" }}>
                <Button primary disabled={!isSaveEnabled} onClick={onSave}>
                    Save
                </Button>
            </div>
        </HorizontalGrid>
    );
}

export default ThreatConfigurationComponent;