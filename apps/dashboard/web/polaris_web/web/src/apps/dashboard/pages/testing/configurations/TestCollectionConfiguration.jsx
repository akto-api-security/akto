import { Box, Badge } from "@shopify/polaris";
import { useState } from "react";
import api from "../api"
import func from "@/util/func"
import { useEffect } from "react";
import PersistStore from '@/apps/main/PersistStore';
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"

function TestCollectionConfiguration() {
    const allCollections = PersistStore(state => state.allCollections)
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const subCategoryMap = PersistStore(state => state.subCategoryMap)

    const allCollectionsExceptApiGroups = allCollections.filter(x => x.type !== "API_GROUP")
    const [testCollectionProperties, setTestCollectionProperties] = useState([])
    const [apiCollectionId, setApiCollectionId] = useState(0)
    const [possibleTestCollectionProperties, setPossibleTestCollectionProperties] = useState([])

    function getCategoryName(categoryName) {
        return Object.values(subCategoryMap).find(sc => sc.superCategory.name === categoryName)?.superCategory.shortName || categoryName
    }

    function fetchTestCollectionConfiguration(apiCollectionId) {
        setTestCollectionProperties([])
        setApiCollectionId(apiCollectionId)


        api.fetchPropertyIds().then(({propertyIds}) => {
            setPossibleTestCollectionProperties(propertyIds)
            api.fetchTestCollectionConfiguration(apiCollectionId).then(({testCollectionProperties}) => {


                let finalProps = Object.keys(propertyIds).map(k => {
                    let propsFromPossible = propertyIds[k]
                    let propsFromConfig = testCollectionProperties.find(p => p.name === k)

                    let ret = {
                        formattedName: propsFromPossible.title,
                        formattedCategoriesComp: <Box>{propsFromPossible.impactingCategories.map(c => <Badge>{getCategoryName(c)}</Badge>)}</Box>
                    }

                    if (propsFromConfig) {
                        return {
                            formattedValues: propsFromConfig.values.join(", "),
                            statusComp: <Badge status="success" progress="complete">Done</Badge>,
                            ...propsFromConfig,
                            ...ret
                        }
                    } else {
                        return {
                            formattedValues: "-",
                            statusComp: <Badge status="critical" progress="incomplete">Pending</Badge>,
                            ...ret
                        }
                    }
                })

                setTestCollectionProperties(finalProps)
            })
        })

    }

    useEffect(() => {
        fetchTestCollectionConfiguration(0)
    }, [])

    const apiCollectionItems =  allCollectionsExceptApiGroups.map(x => {
        return {
            label: x.displayName,
            value: x.id
        }
    })

    const resourceName = {
        singular: 'configuration',
        plural: 'configurations',
    };

    const headers = [
        {
            text: 'Config name',
            title: 'Config name',
            value: 'formattedName',
            isText: CellType.TEXT
        },
        {
            text: 'Status',
            title: 'Status',
            value: 'statusComp'
        },
        {
            text: 'Values',
            title: 'Values',
            value: 'formattedValuesComp',
            isText: CellType.TEXT
        },
        {
            text: 'Impacting categories',
            title: 'Impacting categories',
            value: 'formattedCategoriesComp',
            isText: CellType.TEXT
        }
    ]

    return (
        <div>
            <Box width="400px" paddingBlockEnd={"16"} >
                <DropdownSearch
                    id={`user-config-api-collections`}
                    disabled={false}
                    placeholder="Select API collection"
                    optionsList={apiCollectionItems}
                    setSelected={fetchTestCollectionConfiguration}
                    preSelected={[0]}
                    value={mapCollectionIdToName[apiCollectionId]}
                />
            </Box>
            <GithubSimpleTable
                key="critical"
                data={testCollectionProperties}
                resourceName={resourceName}
                headers={headers}
                useNewRow={true}
                condensedHeight={true}
                hideQueryField={true}
                headings={headers}
            />
        </div>

    )
}

export default TestCollectionConfiguration