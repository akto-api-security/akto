import { Box, Button, HorizontalStack, Icon, Popover, RadioButton, Text, TextField, VerticalStack } from '@shopify/polaris'
import { DeleteMajor } from "@shopify/polaris-icons"
import React, { useEffect, useState } from 'react'
import func from "@/util/func"
import TooltipText from '../../../../components/shared/TooltipText'

const SetUserEnvPopupComponent = ({ popover, setPopover, tags, apiCollectionIds, updateTags }) => {
    const [allEnvTypes, setAllEnvTypes] = useState([])
    const [newCustomTag, setNewCustomTag] = useState({keyName: "", value: "", lastUpdatedTs: 0})
    const [showInputBox, setShowInputBox] = useState(false)
    const [selectedEnvType, setSelectedEnvType] = useState(null)

    const toggleTags = (tag, apiCollectionIds) => {
        if (tag === "reset") {
            updateTags(apiCollectionIds, null)
            setSelectedEnvType(null)
            setAllEnvTypes([])
            return
        }

        if(tag?.value?.length === 0) {
            return
        }

        if (apiCollectionIds.length > 1) {
            if (typeof tag === "object" && tag !== null) {
                const exists = allEnvTypes.some(obj =>
                    Object.keys(tag).every(key => {
                        if (key === "lastUpdatedTs") return true
                        const tagValue = tag[key]
                        const objValue = obj[key]
                        if (typeof tagValue === 'string' && typeof objValue === 'string') {
                            return tagValue.toLowerCase() === objValue.toLowerCase()
                        }
                        return tagValue === objValue
                    })
                )

                const newTags = exists
                    ? allEnvTypes.filter(obj => !(obj.keyName === tag.keyName && obj.value === tag.value))
                    : [...allEnvTypes, { ...tag, lastUpdatedTs: 0 }]

                setAllEnvTypes(newTags)
                updateTags(apiCollectionIds, tag)
            }
            return
        }

        const envType = tag?.value?.toLowerCase?.()
        const isEnvType = tag?.keyName?.toLowerCase?.() === "envtype"
        let newEnvTypes = [...allEnvTypes]

        if (isEnvType && (envType === "staging" || envType === "production")) {
            newEnvTypes = newEnvTypes.filter(
                obj => obj.keyName?.toLowerCase() !== "envtype" ||
                    obj.value?.toLowerCase() === envType
            )

            newEnvTypes = newEnvTypes.filter(
                obj => obj.value?.toLowerCase() !== (envType === "staging" ? "production" : "staging")
            )

            const alreadyExists = newEnvTypes.some(obj =>
                obj.keyName?.toLowerCase() === "envtype" && obj.value?.toLowerCase() === envType
            )
            if (!alreadyExists) {
                newEnvTypes.push({ keyName: "envType", value: envType, lastUpdatedTs: 0 })
            }

            setSelectedEnvType(envType)
        } else if (typeof tag === "object" && tag !== null) {
            const exists = newEnvTypes.some(obj =>
                Object.keys(tag).every(key => {
                    if (key === "lastUpdatedTs") return true
                    const tagValue = tag[key]
                    const objValue = obj[key]
                    if (typeof tagValue === 'string' && typeof objValue === 'string') {
                        return tagValue.toLowerCase() === objValue.toLowerCase()
                    }
                    return tagValue === objValue
                })
            )

            newEnvTypes = exists
                ? newEnvTypes.filter(obj => !(obj.keyName === tag.keyName && obj.value === tag.value))
                : [...newEnvTypes, { ...tag, lastUpdatedTs: 0 }]
        }

        if (selectedEnvType === 'staging' || selectedEnvType === 'production') {
            const hasSelectedEnvTag = newEnvTypes.some(obj =>
                obj.keyName?.toLowerCase() === "envtype" &&
                obj.value?.toLowerCase() === selectedEnvType
            )

            if (!hasSelectedEnvTag) {
                newEnvTypes.push({
                    keyName: "envType",
                    value: selectedEnvType,
                    lastUpdatedTs: 0
                })
            }
        }

        setAllEnvTypes(newEnvTypes)
        updateTags(apiCollectionIds, tag)
    }

    const handleEnvChange = (value) => {
        if (apiCollectionIds.length !== 1) return

        let updatedTags = [...allEnvTypes]

        if (value === 'staging') {
            updatedTags = updatedTags.filter(tag => tag?.value?.toLowerCase() !== 'production')

            const hasStagingTag = updatedTags.some(tag =>
                tag?.keyName?.toLowerCase() === 'envtype' &&
                tag?.value?.toLowerCase() === 'staging'
            )
            if (!hasStagingTag) {
                updatedTags.push({ keyName: "envType", value: "staging", lastUpdatedTs: 0 })
            }

            setSelectedEnvType('staging')
        } else if (value === 'production') {
            updatedTags = updatedTags.filter(tag => tag?.value?.toLowerCase() !== 'staging')

            const hasProductionTag = updatedTags.some(tag =>
                tag?.keyName?.toLowerCase() === 'envtype' &&
                tag?.value?.toLowerCase() === 'production'
            )
            if (!hasProductionTag) {
                updatedTags.push({ keyName: "envType", value: "production", lastUpdatedTs: 0 })
            }

            setSelectedEnvType('production')
        }

        setAllEnvTypes(updatedTags)
        updateTags(apiCollectionIds, updatedTags.at(updatedTags.length-1))
    }

    useEffect(() => {
        apiCollectionIds.map((apiCollectionId) => {
            const envTypes = tags?.[apiCollectionId]

            envTypes?.map((envType) => {
                if(envType?.keyName === "envType" || envType?.keyName === "userSetEnvType") {
                    if(  (envType?.keyName === "envType" && (envType?.value?.toLowerCase() === "staging" || envType?.value?.toLowerCase() === "production"))) {
                        setSelectedEnvType(envType?.value?.toLowerCase())
                    } else if (envType?.keyName === "userSetEnvType") {
                        setAllEnvTypes((prev) => [...prev, { ...envType, keyName: "env" }])
                    }
                } else {
                    setAllEnvTypes((prev) => [...prev, envType])
                }
            })
        })
    }, [tags])

    return (
        <Popover.Pane fixed>
            {apiCollectionIds?.length == 1 && (
                <>
                    <Popover.Section>
                        <VerticalStack gap={2}>
                            <Text variant='headingXs'>Environment</Text>
                            <RadioButton
                                label="Staging"
                                checked={selectedEnvType === 'staging'}
                                id="staging"
                                name="envType"
                                onChange={() => handleEnvChange('staging')}
                            />
                            <RadioButton
                                label="Production"
                                checked={selectedEnvType === 'production'}
                                id="production"
                                name="envType"
                                onChange={() => handleEnvChange('production')}
                            />
                        </VerticalStack>
                    </Popover.Section>

                    {allEnvTypes?.length > 0 && (
                        <Popover.Section>
                            <VerticalStack gap={2}>
                                <Text variant='headingXs'>Custom tags</Text>
                                <VerticalStack gap={3}>
                                    {
                                        allEnvTypes.map((env) => {
                                            if (["staging", "production"].includes(env?.value?.toLowerCase())) return null

                                            return (
                                                <HorizontalStack align='space-between' gap={4}>
                                                    <HorizontalStack >
                                                        <Box maxWidth='150px'>
                                                            <TooltipText textProps={{fontWeight:"bold"}} tooltip={`${env?.keyName}`} text={`${env?.keyName}`}/>
                                                        </Box>
                                                        <Text>=</Text>
                                                        <Box width='150px'>
                                                            <TooltipText tooltip={env?.value} text={env?.value}/>
                                                        </Box>
                                                    </HorizontalStack>
                                                    <Button 
                                                        icon={DeleteMajor}
                                                        plain
                                                        onClick={() => {
                                                            const deleteConfirmationMessage = "Are you sure you want to delete this custom tag?"
                                                            func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => toggleTags(env, apiCollectionIds) )}
                                                        }
                                                    />
                                                </HorizontalStack>
                                            )
                                        })
                                    }
                                </VerticalStack>
                            </VerticalStack>
                        </Popover.Section>
                    )}
                </>
            )}

            <Popover.Section>
                <VerticalStack gap={2}>
                    {
                        showInputBox ? (
                            <HorizontalStack gap={2}>
                                <TextField
                                    onChange={(val) =>
                                        setNewCustomTag((prev) => ({
                                            ...prev,
                                            keyName: val
                                        }))
                                    }
                                    value={newCustomTag?.keyName}
                                    placeholder="Key"
                                    maxLength={64}
                                />
                                <Text>{": "}</Text>
                                <TextField
                                    onChange={(val) =>
                                        setNewCustomTag((prev) => ({
                                            ...prev,
                                            value: val
                                        }))
                                    }
                                    value={newCustomTag?.value}
                                    placeholder="Value"
                                    maxLength={64}
                                />
                                <Button onClick={() => {
                                    toggleTags(newCustomTag, apiCollectionIds)
                                    setNewCustomTag({keyName: "", value: "", lastUpdatedTs: 0})
                                    setShowInputBox(false)
                                }}>Done</Button>
                            </HorizontalStack>
                        ) : 
                        <div onClick={() => setShowInputBox(true)} style={{ cursor: 'pointer' }}>
                            <Text fontWeight="regular" variant="bodyMd">Add custom tag</Text>
                        </div>
                    }
                    <div onClick={() => toggleTags("reset", apiCollectionIds)} style={{ cursor: 'pointer' }}>
                        <Text fontWeight="regular" variant="bodyMd">Reset</Text>
                    </div>
                </VerticalStack>
            </Popover.Section>
        </Popover.Pane>
    )
}

export default SetUserEnvPopupComponent