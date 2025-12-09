import { Box, Button, HorizontalStack, Popover, RadioButton, Text, TextField, VerticalStack } from "@shopify/polaris";
import { DeleteMajor } from "@shopify/polaris-icons";
import TooltipText from "./TooltipText";
import func from "@/util/func"
import { useState } from "react";

const EnvSelector = ({ selectedEnvType, handleEnvChange }) => {

    return (
        <Popover.Section>
            <VerticalStack gap={2}>
                <Text variant='headingXs'>Environment</Text>
                <RadioButton label="Staging" id="staging" name="envType"
                    checked={selectedEnvType === 'staging'} 
                    onChange={() => handleEnvChange('staging')}
                />
                <RadioButton label="Production" id="production" name="envType"
                    checked={selectedEnvType === 'production'} 
                    onChange={() => handleEnvChange('production')}
                />
            </VerticalStack>
        </Popover.Section>
    )
}

const TagList = ({ tagList, isKvTypeTag, displayConfirmationModals, tagDeletionHandler }) => {

    return (
        (tagList && Array.isArray(tagList) && tagList.length > 0 && (
                <Popover.Section>
                    <VerticalStack gap={2}>
                        <Text variant='headingXs'>Custom tags</Text>
                        <VerticalStack gap={3}>
                            {
                                tagList.map((tag) => {
                                    
                                    let keyName, value;;
                                    if (isKvTypeTag) {
                                        if (typeof tag !== "object" || tag === null) return null;

                                        keyName = tag.keyName;
                                        value = tag.value;

                                        if (!keyName || !value || typeof keyName !== "string" || typeof value !== "string") return null;
                                        if (["staging", "production"].includes(value.toLowerCase())) return null;     
                                    }

                                    return (
                                        <HorizontalStack align='space-between' gap={4} key={isKvTypeTag ? `${keyName}=${value}` : tag}>
                                            <HorizontalStack >
                                                {isKvTypeTag ? (
                                                    <>
                                                        <Box maxWidth='150px'>
                                                            <TooltipText textProps={{ fontWeight: "bold" }} tooltip={`${keyName}`} text={`${keyName}`} />
                                                        </Box>
                                                        <Text>=</Text>
                                                        <Box width='150px'>
                                                            <TooltipText tooltip={value} text={value} />
                                                        </Box>
                                                    </>
                                                ) : (
                                                    <>
                                                        <Box width='150px'>
                                                            <TooltipText tooltip={tag} text={tag} />
                                                        </Box>
                                                    </>
                                                )}
                                            </HorizontalStack>
                                            <Button icon={DeleteMajor} plain
                                                onClick={() => {
                                                    if (displayConfirmationModals) {
                                                        const deleteConfirmationMessage = "Are you sure you want to delete this custom tag?"
                                                        func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => tagDeletionHandler(tag))
                                                    } else {
                                                        tagDeletionHandler(tag)
                                                    }
                                                }}
                                            />
                                        </HorizontalStack>
                                    )
                                })
                            }
                        </VerticalStack>
                    </VerticalStack>
                </Popover.Section>
        ))
    )
}

const TagListOperationsManager = ({ isKvTypeTag, displayConfirmationModals, tagsAddHandler, tagsResetHandler }) => {
    const [showInputBox, setShowInputBox] = useState(false)
    const initialTagState = isKvTypeTag ? {keyName: "", value: "", lastUpdatedTs: 0} : ""
    const [newCustomTag, setNewCustomTag] = useState(initialTagState)
    
    return (
        <Popover.Section>
            <VerticalStack gap={2}>
                {
                    showInputBox ? (
                        <HorizontalStack gap={2}>
                            {isKvTypeTag ? (
                                <HorizontalStack gap={1}>
                                    <TextField value={newCustomTag?.keyName} placeholder="Key" maxLength={64}
                                        onChange={(val) => setNewCustomTag((prev) => ({ ...prev, keyName: val }))}     
                                    />
                                    <Text>{": "}</Text>
                                    <TextField value={newCustomTag?.value} placeholder="Value" maxLength={64}
                                        onChange={(val) => setNewCustomTag((prev) => ({...prev, value: val }))}
                                    />
                                </HorizontalStack>
                            ) : (
                                <TextField value={newCustomTag} placeholder="Enter tag" maxLength={64}
                                    onChange={(value) => setNewCustomTag(value)}  
                                />
                            )
                            }
                            <Button onClick={() => {
                                setNewCustomTag(initialTagState)
                                setShowInputBox(false)
                                tagsAddHandler(newCustomTag)
                            }}>Done</Button>
                        </HorizontalStack>
                    ) : (
                        <div onClick={() => setShowInputBox(true)} style={{ cursor: 'pointer' }}>
                            <Text fontWeight="regular" variant="bodyMd">Add custom tag</Text>
                        </div>
                    )
                }
                <div style={{ cursor: 'pointer' }} 
                    onClick={() => {
                        if (displayConfirmationModals) {
                            const resetConfirmationMessage = "Are you sure you want to reset all custom tags?"
                            func.showConfirmationModal(resetConfirmationMessage, "Reset", () => tagsResetHandler())
                        } else {
                            tagsResetHandler()
                        }
                    }} 
                >
                    <Text fontWeight="regular" variant="bodyMd">Reset</Text>
                </div>
            </VerticalStack>
        </Popover.Section>
    )
}

const TagManager = (
    { 
        isKvTypeTag = false, displayConfirmationModals = true,
        showEnvSelector = false, selectedEnvType, handleEnvChange,
        showTagList = false, tagList, tagDeletionHandler,
        showTagListOperationsManager = false, tagsAddHandler, tagsResetHandler
    }) => {

    return (
        <Popover.Pane fixed>
            {showEnvSelector && <EnvSelector selectedEnvType={selectedEnvType} handleEnvChange={handleEnvChange} />}

            {showTagList && <TagList isKvTypeTag={isKvTypeTag} displayConfirmationModals={displayConfirmationModals} tagList={tagList}  tagDeletionHandler={tagDeletionHandler}/>}

            {showTagListOperationsManager && <TagListOperationsManager isKvTypeTag={isKvTypeTag} displayConfirmationModals={displayConfirmationModals} tagsAddHandler={tagsAddHandler} tagsResetHandler={tagsResetHandler}/>}
        </Popover.Pane>
    )
}

export default TagManager;