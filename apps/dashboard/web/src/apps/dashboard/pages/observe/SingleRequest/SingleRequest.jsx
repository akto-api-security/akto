import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useParams, useNavigate } from "react-router-dom"
import { Button, Text, Box } from "@shopify/polaris"
import api from "../api";
import { useEffect, useState } from "react";
import SampleDataList from "../../../components/shared/SampleDataList";

function SingleRequest(){

    const params = useParams()
    const apiCollectionId = params.apiCollectionId
    const [url, method] = atob(params.urlAndMethod).split(" ")
    const endpoint = method + " " + url
    const [sampleData, setSampleData] = useState([])
    useEffect(() => {
        async function fetchData(){
            await api.fetchSampleData(url, apiCollectionId, method).then((res) => {
                setSampleData(res.sampleDataList[0].samples);
            })
        } 
        fetchData();
    },[])

    const navigate = useNavigate();
    function navigateBack() {
        navigate("/dashboard/observe/sensitive")
    }

    return (
        <PageWithMultipleCards
            title={
                <Box maxWidth="50vw">
                    <Text variant='headingLg' truncate>
                {
                    endpoint || "Endpoint"
                }
            </Text>
            </Box>
            }
            backAction = {{onAction:navigateBack}}
            secondaryActions = {<Button disclosure>Actions</Button>}
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