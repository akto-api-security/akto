
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useParams } from "react-router-dom"
import { Button, Text, Box, Popover, ActionList, BlockStack, InlineStack, Icon } from "@shopify/polaris"
import api from "../api";
import { useEffect, useState } from "react";
import SampleDataList from "../../../components/shared/SampleDataList";
import { SearchIcon, ShieldPersonIcon } from "@shopify/polaris-icons";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";

let headerDetails = [
    {
        text: "Collection",
        value: "collection",
        icon: ShieldPersonIcon,
    },
    {
        text: "Discovered",
        value: "detected_timestamp",
        icon: SearchIcon,
    },
    {
        text: "Location",
        value: "location",
        icon: SearchIcon,
    },
  ]

function SingleRequest(){

    const params = useParams()
    const apiCollectionId = Number(params.apiCollectionId)
    const [url, method] = atob(params.urlAndMethod).split(" ")
    const subType = params.subType
    const [sampleData, setSampleData] = useState([])
    const [popoverActive, setPopoverActive] = useState(false);
    function togglePopoverActive() {
        setPopoverActive(!popoverActive);
    }
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [endpointData, setEndpointData]=useState({})

    useEffect(() => {
        async function fetchData(){
            let sensitiveData =[]
            await api.loadSensitiveParameters(apiCollectionId, url, method, subType).then((res) => {
                setEndpointData(transform.prepareEndpointData(collectionsMap, res));
                sensitiveData = res.data.endpoints;
            })
            await api.fetchSensitiveSampleData(url, apiCollectionId, method).then(async(res) => {
                if(res.sensitiveSampleData && Object.keys(res.sensitiveSampleData).length > 0){
                    setSampleData(transform.prepareSampleData(res, subType))
                }else{
                    await api.fetchSampleData(url, apiCollectionId, method).then((resp) => {
                        let sampleData = resp.sampleDataList.map(x => x.samples)
                        sampleData = sampleData.flat()
                        let newResp = transform.convertSampleDataToSensitiveSampleData(sampleData, sensitiveData)
                        setSampleData(transform.prepareSampleData(newResp, subType))
                    })
                }
            })
        } 
        fetchData();
    },[])

    return (
        <PageWithMultipleCards
            title={
                <BlockStack gap="300">
                    <InlineStack gap="200" align="start" blockAlign='start'>
                        <Box maxWidth="50vw">
                            <Text variant='headingLg' truncate>
                                {`${subType} in ${method} ${url}`}
                            </Text>
                        </Box>
                    </InlineStack>
                    <InlineStack gap='200' align="start">
                        {
                          headerDetails?.map((header) => {
                            return (
                                <InlineStack key={header.value} gap="100">
                                    <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                                      <Icon source={header.icon} tone="subdued" />
                                    </div>
                                    <Text as="div" variant="bodySm" tone="subdued" fontWeight='regular'>
                                      {endpointData[header.value]}
                                    </Text>
                                </InlineStack>
                            );
                          })
                        }
                    </InlineStack>
                </BlockStack>
                
            }
            backUrl={`/dashboard/observe/sensitive/${subType}`}
            // secondaryActions = {
            //     <Popover
            //     active={popoverActive}
            //     activator={<Button onClick={togglePopoverActive} disclosure>Actions</Button>}
            //     onClose={togglePopoverActive}
            // >
            //     <ActionList
            //         actionRole="menuitem"
            //         items={
            //             [
            //                 {
            //                     content: 'Ignore',
            //                     onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
            //                 },
            //                 {
            //                     content: 'Mark as false positive',
            //                     onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
            //                 },
            //                 {
            //                     content: 'Create issue',
            //                     onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
            //                 },
            //                 {
            //                     content: 'Configure data types',
            //                     onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
            //                 },
            //             ]
            //         }
            //     />
            // </Popover>
            // }
            components = {[
                sampleData.length>0 && <SampleDataList
                key="Sample values"
                sampleData={sampleData}
                heading={"Sample values"}
              />,
            ]}
        />
    );
}

export default SingleRequest