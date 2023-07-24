import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useParams, useNavigate } from "react-router-dom"
import { Button, Text, Box, Popover, ActionList, VerticalStack, HorizontalStack, Icon } from "@shopify/polaris"
import api from "../api";
import { useEffect, useState } from "react";
import SampleDataList from "../../../components/shared/SampleDataList";
import Store from "../../../store";
import {
    SearchMinor,
    FraudProtectMinor
  } from '@shopify/polaris-icons';
import transform from "../transform";

let headerDetails = [
    {
        text: "Collection",
        value: "collection",
        icon: FraudProtectMinor,
    },
    {
        text: "Discovered",
        value: "detected_timestamp",
        icon: SearchMinor,
    },
    {
        text: "Location",
        value: "location",
        icon: SearchMinor,
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

    const allCollections = Store(state => state.allCollections);
    const apiCollectionMap = allCollections.reduce(
        (map, e) => {map[e.id] = e.displayName; return map}, {}
    )
    const [endpointData, setEndpointData]=useState({})

    useEffect(() => {
        async function fetchData(){
            await api.loadSensitiveParameters(apiCollectionId, url, method, subType).then((res) => {
                setEndpointData(transform.prepareEndpointData(apiCollectionMap, res));
            })
            await api.fetchSensitiveSampleData(url, apiCollectionId, method).then((res) => {
                setSampleData(transform.prepareSampleData(res, subType))
            })
        } 
        fetchData();
    },[])

    const navigate = useNavigate();
    function navigateBack() {
        navigate("/dashboard/observe/sensitive/" + subType)
    }

    return (
        <PageWithMultipleCards
            title={
                <VerticalStack gap="3">
                <HorizontalStack gap="2" align="start" blockAlign='start'>
                    <Box maxWidth="50vw">
                        <Text variant='headingLg' truncate>
                            {`${subType} in ${method} ${url}`}
                        </Text>
                    </Box>
                </HorizontalStack>
                <HorizontalStack gap='2' align="start" >
                  {
                    headerDetails?.map((header) => {
                      return (
                        <HorizontalStack key={header.value} gap="1">
                          <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                            <Icon source={header.icon} color="subdued" />
                          </div>
                          <Text as="div" variant="bodySm" color="subdued" fontWeight='regular'>
                            {endpointData[header.value]}
                          </Text>
                        </HorizontalStack>
                      )
                    })
                  }
                </HorizontalStack>
              </VerticalStack>
                
            }
            backAction = {{onAction:navigateBack}}
            secondaryActions = {
                <Popover
                active={popoverActive}
                activator={<Button onClick={togglePopoverActive} disclosure>Actions</Button>}
                onClose={togglePopoverActive}
            >
                <ActionList
                    actionRole="menuitem"
                    items={
                        [
                            {
                                content: 'Ignore',
                                onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
                            },
                            {
                                content: 'Mark as false positive',
                                onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
                            },
                            {
                                content: 'Create issue',
                                onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
                            },
                            {
                                content: 'Configure data types',
                                onAction: () => { console.log('Todo: implement function'); togglePopoverActive() },
                            },
                        ]
                    }
                />
            </Popover>
            }
            components = {[
                sampleData.length>0 && <SampleDataList
                key="Sample values"
                sampleData={sampleData}
                heading={"Sample values"}
              />,
            ]}
        />
    )
}

export default SingleRequest