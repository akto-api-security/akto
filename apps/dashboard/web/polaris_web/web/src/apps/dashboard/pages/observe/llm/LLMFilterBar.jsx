import { useCallback, useEffect, useState } from "react";
import {ChoiceList, HorizontalStack, IndexFilters,IndexFiltersMode, useSetIndexFiltersMode,} from "@shopify/polaris";
import api from "./api";

export default function LLMFilterBar({
    currDateRange,
    queryValue = "",
    onQueryChange,
    filters = {},
    onFiltersChange,
    queryPlaceholder,
}) {
    const { mode, setMode } = useSetIndexFiltersMode(IndexFiltersMode.Default);

    const [choices, setChoices] = useState({ userName: [], deviceId: [], serviceId: [] });

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until).then(c => setChoices({
            userName:  c.userName  || [],
            deviceId:  c.deviceId  || [],
            serviceId: c.serviceId || [],
        }));
    }, [getEpochs]);

    const update = (key, val) => onFiltersChange({ ...filters, [key]: Array.isArray(val) ? val.join(",") : val });
    const clear  = (key) => onFiltersChange({ ...filters, [key]: "" });

    const indexFilters = [
        {
            key: "userName",
            label: "User",
            shortcut: true,
            filter: (
                <ChoiceList
                    title="User"
                    titleHidden
                    choices={choices.userName.map(v => ({ label: v, value: v }))}
                    selected={filters.userName ? [filters.userName] : []}
                    onChange={([v]) => update("userName", v || "")}
                />
            ),
        },
        {
            key: "deviceId",
            label: "Device",
            shortcut: true,
            filter: (
                <ChoiceList
                    title="Device"
                    titleHidden
                    choices={choices.deviceId.map(v => ({ label: v, value: v }))}
                    selected={filters.deviceId ? [filters.deviceId] : []}
                    onChange={([v]) => update("deviceId", v || "")}
                />
            ),
        },
        {
            key: "serviceId",
            label: "Service",
            shortcut: true,
            filter: (
                <ChoiceList
                    title="Service"
                    titleHidden
                    choices={choices.serviceId.map(v => ({ label: v, value: v }))}
                    selected={filters.serviceId ? [filters.serviceId] : []}
                    onChange={([v]) => update("serviceId", v || "")}
                />
            ),
        },
    ];

    const appliedFilters = [
        filters.userName  && { key: "userName",  label: `User: ${filters.userName}`,    onRemove: () => clear("userName") },
        filters.deviceId  && { key: "deviceId",  label: `Device: ${filters.deviceId}`,  onRemove: () => clear("deviceId") },
        filters.serviceId && { key: "serviceId", label: `Service: ${filters.serviceId}`, onRemove: () => clear("serviceId") },
    ].filter(Boolean);

    const hasActiveFilters = appliedFilters.length > 0;

    return (
        <HorizontalStack gap={"1"} wrap={false}>
            <IndexFilters
                filters={indexFilters}
                appliedFilters={appliedFilters}
                onClearAll={() => onFiltersChange({ userName: "", deviceId: "", serviceId: "" })}
                queryValue={queryValue}
                onQueryChange={onQueryChange}
                onQueryClear={() => onQueryChange("")}
                queryPlaceholder={queryPlaceholder}
                mode={mode}
                setMode={setMode}
                tabs={[]}
                selected={0}
                onSelect={() => {}}
                canCreateNewView={false}
                cancelAction={{ onAction: () => setMode(IndexFiltersMode.Default), disabled: !hasActiveFilters }}
            />
        </HorizontalStack>
    );
}
