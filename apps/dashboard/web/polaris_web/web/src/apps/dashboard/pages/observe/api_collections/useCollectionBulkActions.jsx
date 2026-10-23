import React, { useRef, useState } from "react"
import { Avatar, Box, Popover, ResourceItem, Text } from "@shopify/polaris"
import func from "@/util/func"
import api from "../api"
import collectionApi from "./api"
import settingRequests from "../../settings/api"
import SearchableResourceList from "../../../components/shared/SearchableResourceList"
import ResourceListModal from "../../../components/shared/ResourceListModal"
import SetUserEnvPopupComponent from "./component/SetUserEnvPopupComponent"

/**
 * The bulk actions that apply to any set of API collections — deactivate/reactivate, delete,
 * share, testing scope and tags — shared by every table that lists collections, so the inventory
 * and the agentic asset pages offer the same actions.
 *
 * rowOf(id) returns what is known about a collection (deactivated, isOutOfTestingScope, type,
 * automated); getTags() returns id -> current tags for the tags popover; onDone() runs after a
 * mutation (clear the selection, reload); onTagsUpdated(ids, tagObj), if given, replaces onDone
 * after a tags change.
 */
export default function useCollectionBulkActions({ rowOf, getTags, onDone, onTagsUpdated }) {
    const userRole = window.USER_ROLE
    const canShare = userRole === 'ADMIN' && func.checkForRbacFeature()

    const [popover, setPopover] = useState(false)
    const [teamData, setTeamData] = useState([])
    const [usersCollection, setUsersCollection] = useState([])
    const [selectedMembers, setSelectedMembers] = useState([])
    const shareDataRequested = useRef(false)

    // Admin only: the member list and which collections each can see, for the Share action —
    // fetched the first time something is selected, not on every page visit.
    const loadShareData = () => {
        if (!canShare || shareDataRequested.current) return
        shareDataRequested.current = true
        Promise.allSettled([api.getAllUsersCollections(), settingRequests.getTeamData()]).then(([usersCollectionResp, teamResp]) => {
            if (usersCollectionResp.status === 'fulfilled') setUsersCollection(usersCollectionResp.value)
            if (teamResp.status === 'fulfilled' && teamResp.value) {
                setTeamData(teamResp.value.filter(x => x?.role !== "ADMIN"))
            }
        })
    }

    async function handleCollectionsAction(collectionIdList, apiFunction, toastContent, currentIsOutOfTestingScopeVal = null) {
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        await (currentIsOutOfTestingScopeVal !== null
                ? apiFunction(collectionIdList, currentIsOutOfTestingScopeVal)
                : apiFunction(collectionIdListObj)).then(() => {
            func.setToast(true, false, `${collectionIdList.length} API collection${func.addPlurality(collectionIdList.length)} ${toastContent} successfully`)
        }).catch((error) => {
            func.setToast(true, true, error.message || 'Something went wrong!')
        })
        onDone()
    }

    async function handleShareCollectionsAction(collectionIdList, userIdList) {
        const userCollectionMap = {}
        for (const userId of userIdList) {
            const intUserId = parseInt(userId, 10)
            const userCollections = usersCollection[intUserId] || []
            userCollectionMap[intUserId] = [...new Set([...userCollections, ...collectionIdList])]
        }
        await api.updateUserCollections(userCollectionMap)
        func.setToast(true, false, `${userIdList.length} Member${func.addPlurality(userIdList.length)}'s collections have been updated successfully`)
    }

    const updateTags = async (apiCollectionIds, tagObj) => {
        let updated = false
        await api.updateEnvTypeOfCollection(tagObj === null ? tagObj : [tagObj], apiCollectionIds, tagObj === null).then(() => {
            updated = true
            func.setToast(true, false, "Tags updated successfully")
        }).catch((error) => {
            func.setToast(true, true, error?.message || 'Something went wrong!')
        })
        setPopover(false)
        if (updated && onTagsUpdated) {
            onTagsUpdated(apiCollectionIds, tagObj)
        } else {
            onDone()
        }
    }

    // Actions for the selected collections, in the order the bulk-action bar shows them
    const collectionActions = (selectedResources) => {
        const actions = []
        const plural = func.addPlurality(selectedResources.length)
        const isDefaultApiGroup = (id) => rowOf(id)?.type === "API_GROUP" && rowOf(id)?.automated
        const isActivated = (id) => rowOf(id) && !rowOf(id).deactivated
        const isDeactivated = (id) => rowOf(id)?.deactivated

        if (selectedResources.every(isActivated)) {
            actions.push({
                content: `Deactivate collection${plural}`,
                onAction: () => {
                    const message = "Deactivating a collection will stop traffic ingestion and testing for this collection. Please sync the usage data via Settings > billing after deactivating a collection to reflect your updated usage. Are you sure, you want to deactivate this collection ?"
                    func.showConfirmationModal(message, "Deactivate collection", () => handleCollectionsAction(selectedResources, collectionApi.deactivateCollections, "deactivated"))
                },
                requires: 'api/deactivateCollections'
            })
        } else if (selectedResources.every(isDeactivated)) {
            actions.push({
                content: `Reactivate collection${plural}`,
                onAction: () => {
                    const message = "Please sync the usage data via Settings > billing after reactivating a collection to resume data ingestion and testing."
                    func.showConfirmationModal(message, "Activate collection", () => handleCollectionsAction(selectedResources, collectionApi.activateCollections, "activated"))
                },
                requires: 'api/activateCollections'
            })
        }

        actions.push({
            content: `Delete collection${plural}`,
            onAction: () => {
                const deleteConfirmationMessage = `Are you sure, you want to delete ${selectedResources.length} collection${plural}?`
                func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleCollectionsAction(selectedResources.filter(v => !isDefaultApiGroup(v)), api.deleteMultipleCollections, "deleted"))
            },
            requires: 'api/deleteMultipleCollections'
        })

        if (canShare) {
            loadShareData()
            const renderMember = (item) => {
                const { id, name, login, role } = item
                const initials = func.initials(login)
                const media = <Avatar user size="medium" name={login} initials={initials} />
                const shortcutActions = [
                    {
                        content: <Text color="subdued">{role}</Text>,
                        url: '#',
                        onAction: ((event) => event.preventDefault())
                    }
                ]
                return (
                    <ResourceItem id={id} key={id} media={media} shortcutActions={shortcutActions} persistActions>
                        <Text variant="bodyMd" fontWeight="bold" as="h3">{name}</Text>
                        <Text variant="bodyMd">{login}</Text>
                    </ResourceItem>
                )
            }

            const shareCollectionHandler = () => {
                if (selectedMembers.length > 0) {
                    handleShareCollectionsAction(selectedResources, selectedMembers)
                    return true
                }
                func.setToast(true, true, "No member is selected!")
                return false
            }

            actions.push({
                content: (
                    <ResourceListModal
                        isLarge={true}
                        activatorPlaceaholder={"Share"}
                        title={"Share collections"}
                        primaryAction={shareCollectionHandler}
                        component={
                            <Box>
                                <Box padding={5} background="bg-subdued-hover">
                                    <Text fontWeight="medium">{`${selectedResources.length} collection${plural} selected`}</Text>
                                </Box>
                                <SearchableResourceList
                                    resourceName={'user'}
                                    items={teamData}
                                    renderItem={renderMember}
                                    isFilterControlEnabale={true}
                                    selectable={true}
                                    onSelectedItemsChange={setSelectedMembers}
                                />
                            </Box>
                        }
                    />
                ),
                requires: 'api/updateUserCollections'
            })
        }

        const allOutOfTestScopeFalse = selectedResources.every(id => rowOf(id) && !rowOf(id).isOutOfTestingScope)
        const allOutOfTestScopeTrue = selectedResources.every(id => rowOf(id) && rowOf(id).isOutOfTestingScope)
        if (allOutOfTestScopeFalse || allOutOfTestScopeTrue) {
            actions.push({
                content: `Mark collection${plural} as ${allOutOfTestScopeFalse ? "out of" : "in"} testing scope`,
                onAction: () => handleCollectionsAction(selectedResources, collectionApi.toggleCollectionsOutOfTestScope,
                    allOutOfTestScopeFalse ? "marked out of testing scope" : "marked in testing scope", allOutOfTestScopeTrue),
                requires: 'api/toggleCollectionsOutOfTestScope'
            })
        }
        return actions
    }

    const setTagsAction = (selectedResources) => ({
        content: (
            <Popover
                activator={<div onClick={() => setPopover(!popover)}>Set tags</div>}
                onClose={() => setPopover(false)}
                active={popover}
                autofocusTarget="first-node"
            >
                <Popover.Pane>
                    <SetUserEnvPopupComponent
                        popover={popover}
                        setPopover={setPopover}
                        tags={getTags()}
                        updateTags={updateTags}
                        apiCollectionIds={selectedResources}
                    />
                </Popover.Pane>
            </Popover>
        ),
        requires: 'api/updateEnvType'
    })

    return { collectionActions, setTagsAction }
}
