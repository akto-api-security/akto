import React, { useState } from 'react'
import BannerLayout from '../../../components/banners/BannerLayout'
import { TESTING_VIDEO_LENGTH, TESTING_VIDEO_URL, TESTING_VIDEO_THUMBNAIL } from '../../../../main/onboardingData'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import PersistStore from '../../../../main/PersistStore'
import { Box, Button, Popover, Text } from '@shopify/polaris'
import { useNavigate } from 'react-router-dom'

function SelectCollectionComponent() {
    const [popoverActive, setPopoverActive] = useState(false)
    const allCollections = PersistStore(state => state.allCollections);
    const navigate = useNavigate()
    let urlsCount = 0
    const allCollectionsOptions = allCollections.filter(x => x.type !== "API_GROUP")
        .map(collection => {
            urlsCount += collection.urlsCount
            return {
                label: collection.displayName,
                value: collection.id
            }
        })
    
    return(
        urlsCount > 0 ?<Popover
            active={popoverActive}
            activator={(
                <Button onClick={() => setPopoverActive(true)} disclosure>
                    Select collection
                </Button>
            )}
            onClose={() => { setPopoverActive(false) }}
            autofocusTarget="first-node"
            preferredAlignment="left"
        >
            <Popover.Pane fixed>
                <Box padding={"1"}>
                    <DropdownSearch
                        placeholder="Search collection"
                        optionsList={allCollectionsOptions}
                        setSelected={(id) => navigate(`/dashboard/observe/inventory/${id}`)}
                    />
                </Box>
            </Popover.Pane>
        </Popover>: <Text color="subdued" variant="bodyMd" fontWeight="medium">No endpoints exist, go to inventory page to upload traffic.</Text>
    )
}

function TestrunsBannerComponent({isInventory,onButtonClick}) {
    const allCollections = PersistStore(state => state.allCollections);
    let urlsCount = 0
    allCollections.filter(x => x.type !== "API_GROUP")
        .forEach(collection => {
            urlsCount += collection.urlsCount}
        )
    return (
        <BannerLayout
            title={"Test your APIs"}
            text={"150+ built-in tests covering OWASP Top 10, HackerOne top 10 and all the business logic vulnerabilities for your API Security testing needs."}
            videoLength={TESTING_VIDEO_LENGTH}
            // videoLink={TESTING_VIDEO_URL}
            videoThumbnail={TESTING_VIDEO_THUMBNAIL}
            bodyComponent={isInventory ? null :<SelectCollectionComponent /> }
            {...isInventory ? {buttonText: "Run test"}: {}}
            {...isInventory ? {onClick: () => onButtonClick()} : {}}
            {...urlsCount === 0 ? {buttonText: "Go to inventory"}: {}} 
            {...urlsCount === 0 ? {buttonUrl: "/dashboard/observe/inventory"}: {}} 
        />
    )
}

export {TestrunsBannerComponent,SelectCollectionComponent}