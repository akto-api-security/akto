import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import { Button } from "@shopify/polaris";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import func from "@/util/func";
import tagsApi from "./api";

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
            itemCell: 2
        },
        {
            text: "Created by",
            value: "createdBy",
            itemCell: 2
        }
    ]

    const resourceName = {
        singular: 'tag',
        plural: 'tags',
    };

    const [tags, setTags] = useState([]);
    const [loading, setLoading] = useState(false);
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
        types={"Tags"}
        primaryAction={<Button primary onClick={handleRedirect}>Create new tags</Button>}
        components={[
            <GithubSimpleTable
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