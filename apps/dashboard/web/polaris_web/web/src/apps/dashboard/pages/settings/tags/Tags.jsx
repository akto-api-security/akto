import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import { Button } from "@shopify/polaris";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import tagsApi from "./api";
import {
    ProfileMinor,
    CalendarMinor
  } from '@shopify/polaris-icons';
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import { TAGS_PAGE_DOCS_URL } from "../../../../main/onboardingData";

function Tags(){

    const headers = [
        {
            text: "Name",
            value: "name",
            itemOrder: 1
        },
        {
            text: "Last updated",
            value: "updatedTimestamp",
            itemOrder: 3,
            icon:CalendarMinor
        },
        {
            text: "Created by",
            value: "createdBy",
            itemOrder: 3,
            icon:ProfileMinor
        }
    ]

    const resourceName = {
        singular: 'tag',
        plural: 'tags',
    };

    const [tags, setTags] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showEmptyScreen, setShowEmptyScreen] = useState(false)
    const navigate = useNavigate()

    const handleRedirect = () => {
        navigate("details")
    }

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("details", { state: { name: item?.name, active: item?.active,
                    keyConditions:item?.keyConditions } }),
            }]
        }]
    }

    useEffect(() => {
        setLoading(true);
        async function fetchData() {
            await tagsApi.fetchTagConfigs().then((res) => {
                setShowEmptyScreen(res.tagConfigs.tagConfigs.length === 0)
                let usersMap = res.tagConfigs.usersMap;
                setTags(res.tagConfigs.tagConfigs.map((tag) => {
                    tag.id = tag.name
                    tag.updatedTimestamp = func.prettifyEpoch(tag.timestamp);
                    tag.createdBy = usersMap[tag.creatorId]
                    return tag;
                }));
                setLoading(false);
            })
        }
        fetchData();
    }, [])

    return (
        <PageWithMultipleCards
        title={"Tags"}
        primaryAction={<Button primary onClick={handleRedirect}>Create new tags</Button>}
        isFirstPage={true}
        components={[

            showEmptyScreen ? 
                <EmptyScreensLayout key={"emptyScreen"}
                    iconSrc={"/public/tag_icon.svg"}
                    headingText={"No tags created"}
                    description={"Tag your APIs with business keywords for easier grouping."}
                    buttonText={"Create tag"}
                    redirectUrl={"/dashboard/settings/tags/details"}
                    learnText={"Creating tags"}
                    docsUrl={TAGS_PAGE_DOCS_URL}
                />

            
            :<GithubSimpleTable
                key="table"
                data={tags}
                resourceName={resourceName}
                headers={headers}
                loading={loading}
                getActions={getActions}
                hasRowActions={true}
            />
        ]}
    />
    )
}

export default Tags