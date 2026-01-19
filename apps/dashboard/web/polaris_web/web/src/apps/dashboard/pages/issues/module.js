import issuesApi from "@/apps/dashboard/pages/issues/api"
import IssuesStore from '@/apps/dashboard/pages/issues/issuesStore';
import { Button, Checkbox, HorizontalStack, Popover, Text, TextField } from "@shopify/polaris";
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch";
import Dropdown from "@/apps/dashboard/components/layouts/Dropdown";
import SingleDate from "@/apps/dashboard/components/layouts/SingleDate";
import ShowListInBadge from "@/apps/dashboard/components/shared/ShowListInBadge"
import TagManager from "@/apps/dashboard/components/shared/TagManager";
import { useState } from "react";

const setCreateJiraIssueFieldMetaData = IssuesStore.getState().setCreateJiraIssueFieldMetaData;
const updateDisplayJiraIssueFieldValues = IssuesStore.getState().updateDisplayJiraIssueFieldValues;

const setCreateABWorkItemFieldMetaData = IssuesStore.getState().setCreateABWorkItemFieldMetaData;
const updateDisplayABWorkItemFieldValues = IssuesStore.getState().updateDisplayABWorkItemFieldValues;

const issuesFunctions = {
    fetchIntegrationCustomFieldsMetadata: () => {
        if (window.JIRA_INTEGRATED === 'true') {
            issuesFunctions.fetchCreateIssueFieldMetaData()
        }
            
        if (window.AZURE_BOARDS_INTEGRATED === 'true') {
            issuesFunctions.fetchCreateABWorkItemFieldMetaData()
        }
    },
    getJiraFieldConfigurations: (field) => {
        let customFieldURI = field?.schema?.custom || "";
        const allowedValues = Array.isArray(field?.allowedValues) ? field.allowedValues : [];
        const fieldId = field?.fieldId || "";
        const fieldName = field?.name || "";
        const isFieldRequired = field?.required || false;
        const hasDefaultValue = field?.hasDefaultValue || false;
        const defaultValue = field?.defaultValue || null;

        // handle allowed system fields
        const systemFieldURI = field?.schema?.system || "";
        if (systemFieldURI && customFieldURI === "") {
            if (systemFieldURI === "priority") {
                // treat priority system field as select field
                customFieldURI = "com.atlassian.jira.plugin.system.customfieldtypes:select";

                allowedValues.forEach(item => { item.value = item.name; })
            }
        }

        const handleFieldChange = (fieldId, value) => {
            updateDisplayJiraIssueFieldValues(fieldId, value)
        } 

        switch (customFieldURI) {
            case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
                return {
                    initialValue: hasDefaultValue ? defaultValue : "",
                    getComponent: () => {
                        const displayJiraIssueFieldValues = IssuesStore(state => state.displayJiraIssueFieldValues);
                        return (
                            <TextField
                                key={fieldId}
                                label={fieldName}
                                value={displayJiraIssueFieldValues[fieldId] || ""}
                                onChange={(value) => handleFieldChange(fieldId, value)}
                                maxLength={255}
                                showCharacterCount
                                requiredIndicator={isFieldRequired}
                            />)
                    }
                }
            case "com.atlassian.jira.plugin.system.customfieldtypes:select":
            case "com.atlassian.jira.plugin.system.customfieldtypes:multiselect":
            case "com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes":
            case "com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons": {
                const processDefaults = (defaultValue, isMultiSelect, emptyInitialValue) => { 
                    if (isMultiSelect) {
                        return Array.isArray(defaultValue)
                            ? defaultValue.map(item => ({ value: item.value }))
                            : emptyInitialValue;
                    } else {
                        return defaultValue?.value ? { value: defaultValue.value } : emptyInitialValue
                    }
                }
                
                const isMultiSelect = customFieldURI.includes("multiselect") || customFieldURI.includes("multicheckboxes");
                const emptyInitialValue = isMultiSelect ? [] : null;
                const initialFieldState = hasDefaultValue ? processDefaults(defaultValue, isMultiSelect, emptyInitialValue) : emptyInitialValue;
                const preSelected = Array.isArray(initialFieldState) ? initialFieldState.map(item => item.value) : [ initialFieldState?.value ]
                
                return {
                    initialValue: initialFieldState,
                    getComponent: () => {

                        return (
                            <DropdownSearch
                                allowMultiple={isMultiSelect}
                                optionsList={allowedValues.map((option) => ({
                                    label: option?.value,
                                    value: option?.value
                                }))}
                                setSelected={(selectedOption) => {
                                    if (isMultiSelect) {
                                        const updateFieldValue = selectedOption.map((option) => ({ value: option }));
                                        handleFieldChange(fieldId, updateFieldValue);
                                     } else {
                                        const updateFieldValue = { value: selectedOption };
                                        handleFieldChange(fieldId, updateFieldValue);
                                    }
                                }}
                                preSelected={preSelected}
                                label={fieldName}
                                placeholder={`Select an option for the field ${fieldName}`}
                                textfieldRequiredIndicator={isFieldRequired}
                            />
                        )
                    } 
                }
            }
            default:
                return {
                    initialValue: null,
                    getComponent: () => { return null }
                }
        }
    },
    fetchCreateIssueFieldMetaData: async () => {
        try {
            if(IssuesStore.getState().createJiraIssueFieldMetaData && Object.keys(IssuesStore.getState().createJiraIssueFieldMetaData).length === 0) {
                const response = await issuesApi.fetchCreateJiraIssueFieldMetaData()
                if (response && Object.keys(response).length > 0) {
                    setCreateJiraIssueFieldMetaData(response);
                }
            }else{
                return IssuesStore.getState().createJiraIssueFieldMetaData;
            }
        } catch (error) {
        } 
    },
    prepareCustomIssueFields: (projId, issueType) => {
        const createJiraIssueFieldMetaData = IssuesStore.getState().createJiraIssueFieldMetaData;

        const issueTypeFieldMetaDataList = createJiraIssueFieldMetaData?.[projId]?.[issueType] || [];
        const issueTypeFieldMetaDataMap = issueTypeFieldMetaDataList.reduce((acc, field) => {
            acc[field.fieldId] = field
            return acc;
        }, {});

        const displayJiraIssueFieldValues = IssuesStore.getState().displayJiraIssueFieldValues;
        const customIssueFields = Object.keys(displayJiraIssueFieldValues).reduce((acc, fieldId) => {
            const fieldMetaData = issueTypeFieldMetaDataMap[fieldId];
            const fieldConfiguration = issuesFunctions.getJiraFieldConfigurations(fieldMetaData);
            const fieldInitialValue = fieldConfiguration.initialValue;
            const fieldCurrentValue = displayJiraIssueFieldValues[fieldId];

            if (fieldMetaData) {
                const isRequired = fieldMetaData.required || false;
                const hasDefaultValue = fieldMetaData.hasDefaultValue || false;
                
                // Fail validation if the field is required but has no value
                if (isRequired && !hasDefaultValue && (fieldCurrentValue === fieldInitialValue)) {
                    throw new Error();
                }
            }

            let formattedFieldValue;

            switch (fieldId) {
                case "priority":
                    const priorityValue = fieldCurrentValue?.value;
                    formattedFieldValue = { name: priorityValue };
                    break;
                default:
                    formattedFieldValue = fieldCurrentValue;
                    break;  
            }

            acc.push({
                fieldId: fieldId,
                fieldValue: formattedFieldValue
            });
            return acc;
        }, []);

        return customIssueFields;
    },
    prepareAdditionalIssueFieldsJiraMetaData: (projId, issueType) => {
        const customIssueFields = issuesFunctions.prepareCustomIssueFields(projId, issueType);
        const additionalIssueFields = { customIssueFields: customIssueFields };
        const jiraMetaData = { additionalIssueFields: additionalIssueFields };
        return jiraMetaData;
    },
    fetchCreateABWorkItemFieldMetaData: async () => {
        try {
            if (IssuesStore.getState().createABWorkItemFieldMetaData && Object.keys(IssuesStore.getState().createABWorkItemFieldMetaData).length === 0) {
                const response = await issuesApi.fetchCreateABWorkItemFieldMetaData()

                if (response && Object.keys(response).length > 0) {
                    setCreateABWorkItemFieldMetaData(response);
                }
            } else {
                return IssuesStore.getState().createABWorkItemFieldMetaData;
            }
        } catch (error) {
        }
    },
    getOverriddenABFieldType: (fieldReferenceName, fieldType) => {
        let overriddenFieldType;

        switch (fieldReferenceName) {
            case "System.AreaPath": 
                overriddenFieldType = "AreaPath";
                break;
            case "System.Tags":
                overriddenFieldType = "Tags";
                break;
            default:
                overriddenFieldType = fieldType;
                break;
        }

        return overriddenFieldType;
    },
    getABFieldConfigurations: (field) => {
        const { organizationFieldDetails, workItemTypeFieldDetails } = field;

        const fieldReferenceName = organizationFieldDetails?.referenceName || "";
        const fieldName = organizationFieldDetails?.name || "";
        const isFieldPicklist = organizationFieldDetails?.isPicklist || false;

        const fieldAllowedValues = workItemTypeFieldDetails?.allowedValues || [];
        const isFieldRequired = workItemTypeFieldDetails?.alwaysRequired || false;
        const fieldDefaultValue = workItemTypeFieldDetails?.defaultValue || null;

        let fieldType = organizationFieldDetails?.type || "";
        fieldType = issuesFunctions.getOverriddenABFieldType(fieldReferenceName, fieldType);

        const handleFieldChange = (fieldReferenceName, value) => {
            updateDisplayABWorkItemFieldValues(fieldReferenceName, value)
        } 

        const getPicklistFieldConfiguration = (fieldType, fieldDefaultValue, fieldAllowedValues) => {
            const fallbackInitialValue = fieldType === "string" ? "" : 0;
            const firstAllowedValue = fieldAllowedValues.length > 0 ? fieldAllowedValues[0] : fallbackInitialValue;
            const initialValue = fieldDefaultValue !== null ? fieldDefaultValue : firstAllowedValue;
            const menuItems = fieldAllowedValues.map((option) => ({   
                label: option,
                value: option
            }));

            return {
                initialValue: initialValue,
                menuItems: menuItems
            }
        }

        /* 
         * Field types documentation: https://learn.microsoft.com/en-us/azure/devops/boards/queries/query-index-quick-ref?view=azure-devops#operators-and-macros-supported-for-each-data-type 
         */
        switch (fieldType) {
            case "string":
            case "html":
                if (isFieldPicklist) {
                    const { initialValue, menuItems } = getPicklistFieldConfiguration(fieldType, fieldDefaultValue, fieldAllowedValues);

                    return {
                        initialValue: initialValue,
                        getComponent: () => { 
                            return (
                                <Dropdown
                                    id={`${fieldReferenceName}-dropdown`}
                                    label={fieldName}
                                    menuItems={menuItems}
                                    initial={initialValue}
                                    selected={(value) => handleFieldChange(fieldReferenceName, value)}/>
                            ) 
                        }
                    }
                } else {
                    return {
                        initialValue: fieldDefaultValue !== null ? fieldDefaultValue : "",
                        getComponent: () => {
                            const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);
                            
                            return (
                                <TextField
                                    key={fieldReferenceName}
                                    label={fieldName}
                                    value={displayABWorkItemFieldValues[fieldReferenceName] || ""}
                                    onChange={(value) => handleFieldChange(fieldReferenceName, value)}
                                    maxLength={fieldType === "string" ? 255 : 1000}
                                    type="text"
                                    showCharacterCount
                                    requiredIndicator={isFieldRequired}
                                />)
                        }
                    }
                }
            case "integer":
            case "double":
                if (isFieldPicklist) {
                    const { initialValue, menuItems } = getPicklistFieldConfiguration(fieldType, fieldDefaultValue, fieldAllowedValues);

                    return {
                        initialValue: initialValue,
                        getComponent: () => { 
                            return (
                                <Dropdown
                                    id={`${fieldReferenceName}-dropdown`}
                                    label={fieldName}
                                    menuItems={menuItems}
                                    initial={initialValue}
                                    selected={(value) => handleFieldChange(fieldReferenceName, value)}/>
                            ) 
                        }
                    }
                } else {
                    return {
                        initialValue: fieldDefaultValue !== null ? fieldDefaultValue : 0,
                        getComponent: () => {
                            const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);
                            
                            return (
                                <TextField
                                    key={fieldReferenceName}
                                    label={fieldName}
                                    value={displayABWorkItemFieldValues[fieldReferenceName] || 0}
                                    onChange={(value) => handleFieldChange(fieldReferenceName, value)}
                                    type={fieldType === "integer" ? "integer" : "number"}
                                    requiredIndicator={isFieldRequired}
                                />)
                        }
                    }
                }
            case "boolean":
                return {
                    initialValue: fieldDefaultValue !== null ? fieldDefaultValue : false,
                    getComponent: () => { 
                        const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);

                        return (
                            <Checkbox
                                label={fieldName}
                                checked={displayABWorkItemFieldValues[fieldReferenceName] || false}
                                onChange={(newChecked) => handleFieldChange(fieldReferenceName, newChecked)}
                            />
                        ) 
                    }
                }
            case "dateTime":
                const formatABDate = (d) => {
                    if (d instanceof Date && !isNaN(d)) {
                        return d.toLocaleDateString(undefined, {
                            month: 'numeric',
                            day: 'numeric',
                            year: 'numeric'
                        });
                    }
                    return '';
                }

                const tomorrow = new Date();
                tomorrow.setDate(tomorrow.getDate() + 1);
                const initialValue = formatABDate(tomorrow);

                 return {
                    initialValue: initialValue,
                    getComponent: () => { 
                        const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);
                        const currentValue = displayABWorkItemFieldValues[fieldReferenceName] || initialValue;
                        let currentValueDate = new Date();
                        try {
                            const [mm, dd, yyyy] = currentValue.split("/");
                            currentValueDate = new Date(yyyy, mm - 1, dd)
                        } catch (error) {
                            // do nothing
                        }
                        
                        return (
                            <SingleDate
                                dispatch={(action) => {
                                    const selectedDate = action?.obj?.selectedDate;

                                    if (selectedDate instanceof Date && !isNaN(selectedDate)) {
                                        const selectedDateString = formatABDate(selectedDate);
                                        handleFieldChange(fieldReferenceName, selectedDateString);
                                    }
                                }}
                                data={currentValueDate}
                                dataKey="selectedDate"
                                preferredPosition="above"
                                disableDatesBefore={new Date(new Date().setDate(new Date().getDate()))}
                                label="Select date"
                                allowRange={false}
                                readOnly={true}
                            />
                        )
                    }
                }
            case "AreaPath":
                const areasClassificationNodes = Array.isArray(field?.areasClassificationNodes) ? field.areasClassificationNodes : [];
                areasClassificationNodes.sort((a, b) => a.length - b.length);
                
                const initialAreaPathValue = areasClassificationNodes.length > 0 ? areasClassificationNodes[0] : "";

                return {
                    initialValue: initialAreaPathValue,
                    getComponent: () => { 
                        const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);
                        const areaPathOptions = areasClassificationNodes.map((areaPath) => ({
                            label: typeof areaPath === "string" ? areaPath.replace(/\\\\/g, "\\") : "",
                            value: areaPath
                        }));

                        return (
                            <DropdownSearch
                                id={`${fieldReferenceName}-dropdown`}
                                label="Area"
                                placeholder="Select Area"
                                optionsList={areaPathOptions}
                                setSelected={(value) => handleFieldChange(fieldReferenceName, value)}
                                preSelected={initialAreaPathValue}
                                value={displayABWorkItemFieldValues[fieldReferenceName] || ""}
                            />
                        ) 
                    }
                }
            case "Tags":
                const initialTagsValue = [];

                return {
                    initialValue: initialTagsValue,
                    getComponent: () => {
                        const [popover, setPopover] = useState(false);

                        const displayABWorkItemFieldValues = IssuesStore(state => state.displayABWorkItemFieldValues);
                        const tagList = Array.isArray(displayABWorkItemFieldValues[fieldReferenceName]) ? displayABWorkItemFieldValues[fieldReferenceName] : [];

                        const tagsOperationsHandler = (fieldReferenceName, operation, value) => {
                            const exists = tagList.includes(value);

                            if (operation === "RESET") handleFieldChange(fieldReferenceName, []);
                            else if (operation === "ADD" && !exists) handleFieldChange(fieldReferenceName, [...tagList, value]);
                            else if (operation === "DELETE" && exists) handleFieldChange(fieldReferenceName, tagList.filter(tag => tag !== value));

                            setPopover(false);
                        } 

                        const tagsResetHandler = () => { tagsOperationsHandler(fieldReferenceName, "RESET", null); }
                        const tagsAddHandler = (val) => { tagsOperationsHandler(fieldReferenceName, "ADD", val); }
                        const tagsDeleteHandler = (val) => { tagsOperationsHandler(fieldReferenceName, "DELETE", val); }
                        
                        return (
                            <HorizontalStack gap="2">
                                <Popover
                                    activator={<Button onClick={() => setPopover(!popover)}>Set tags</Button>}
                                    onClose={() => { setPopover(false) }}
                                    active={popover}
                                    autofocusTarget="first-node"
                                >
                                    <Popover.Pane>
                                        <TagManager 
                                            isKvTypeTag={false} displayConfirmationModals={false}
                                            showEnvSelector={false}
                                            showTagList={true} tagList={tagList} tagDeletionHandler={tagsDeleteHandler}
                                            showTagListOperationsManager={true} tagsAddHandler={tagsAddHandler} tagsResetHandler={tagsResetHandler}
                                        />
                                    </Popover.Pane>
                                </Popover>
                                { tagList.length > 0 ? (
                                    <ShowListInBadge 
                                        status={"info"} 
                                        useTooltip={true} wrap={false} allowFullWidth={true}
                                        itemsArr={tagList}  maxItems={3}
                                    />) : ( <Text>No tags set</Text> )
                                }
                            </HorizontalStack>
                        ) 
                    }
                }
            default: 
                return {
                    initialValue: null,
                    getComponent: () => { return null }
                }
        }
    },
    formatABWorkItemFieldValue: (fieldType, fieldValue) => {
        let formattedValue;

        switch (fieldType) {
            case "dateTime":
                // Add default time for dateTime fields
                formattedValue = `${fieldValue} 16:00`; 
                break;
            case "Tags":
                // Convert array of tags to semicolon separated string required by Azure Boards API
                if (Array.isArray(fieldValue)) formattedValue = fieldValue.join("; ");
                else formattedValue = "";
                break;
            default:
                formattedValue = fieldValue;
                break;
        }

        return formattedValue;
    },
    prepareCustomABWorkItemFieldsPayload: (project, workItemType) => {
        const displayABWorkItemFieldValues = IssuesStore.getState().displayABWorkItemFieldValues;
        const createABWorkItemFieldMetaData = IssuesStore.getState().createABWorkItemFieldMetaData;
        const workItemTypeFieldMetaDataList = createABWorkItemFieldMetaData?.[project]?.[workItemType] || [];

        // Convert to map for easier lookup
        const workItemTypeFieldMetaDataMap = workItemTypeFieldMetaDataList.reduce((acc, field) => {
            const fieldReferenceName = field?.organizationFieldDetails?.referenceName;
            const fieldType = field?.organizationFieldDetails?.type || "string";

            const isFieldRequired = field?.workItemTypeFieldDetails?.alwaysRequired || false;
            if (fieldReferenceName) {
                acc[fieldReferenceName] = {
                    fieldType: fieldType,
                    isFieldRequired: isFieldRequired,
                    ...field
                };
            }
            return acc;
        }, {});

        const customABWorkItemFieldsPayload = [];
        for (const [fieldReferenceName, fieldValue] of Object.entries(displayABWorkItemFieldValues)) {
            const fieldMetaData = workItemTypeFieldMetaDataMap[fieldReferenceName];

            // Fail validation if the field is required but has no value
            if (fieldMetaData !== undefined) {
                const isFieldRequired = fieldMetaData?.isFieldRequired || false;
                if (isFieldRequired && fieldValue === null) {
                    throw new Error();
                }
            }

            let fieldType = fieldMetaData?.fieldType || "string";
            fieldType = issuesFunctions.getOverriddenABFieldType(fieldReferenceName, fieldType);

            const formattedFieldValue = issuesFunctions.formatABWorkItemFieldValue(fieldType, fieldValue);

            customABWorkItemFieldsPayload.push({
                referenceName: fieldReferenceName,
                value: formattedFieldValue,
                type: fieldType
            })
        }

        return customABWorkItemFieldsPayload;
    }
}

export default issuesFunctions