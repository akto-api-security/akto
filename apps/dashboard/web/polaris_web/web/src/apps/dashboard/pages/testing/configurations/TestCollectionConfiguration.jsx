import { Box, Badge, Link } from "@shopify/polaris";
import { useState } from "react";
import api from "../api"
import func from "@/util/func"
import { useEffect } from "react";
import PersistStore from '@/apps/main/PersistStore';
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import LocalStore from "../../../../main/LocalStorageStore";

function TestCollectionConfiguration() {
    const allCollections = PersistStore(state => state.allCollections)
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const [testCollectionProperties, setTestCollectionProperties] = useState([])
    const [apiCollectionId, setApiCollectionId] = useState(0)
    const [possibleTestCollectionProperties, setPossibleTestCollectionProperties] = useState([])

    function getCategoryName(categoryName) {
        return Object.values(subCategoryMap).find(sc => sc.superCategory.name === categoryName)?.superCategory.shortName || categoryName
    }

    function drawComponentToEdit(propsFromConfig, propertyIds) {
        let possibleProp = propertyIds[propsFromConfig.name]
        let ret = <div></div>
        if (possibleProp) {
            switch (possibleProp.type) {
            case "CUSTOM_AUTH":
               ret =
                <div>{propsFromConfig.values.map(v => {
                    return <span style={{marginRight: "8px"}}><Link onClick={(e) => e.stopPropagation()}  monochrome target="_blank" url={window.location.origin+"/dashboard/settings/auth-types/details?name="+v}>{v}</Link></span>
                })}</div>
                break;

            case "TEST_YAML_KEYWORD":
               ret =
                (propsFromConfig.values?.length) ?
                <div>{propsFromConfig.values.map(v => {
                    return <span style={{marginRight: "8px"}}><Link onClick={(e) => e.stopPropagation()} monochrome target="_blank" url={window.location.origin+"/dashboard/observe/inventory/"+v}>{mapCollectionIdToName[v]}</Link></span>
                })}</div>
                :<div>Not Implemented</div>


               break;
            case "ROLE":
               ret =
                   <div>{propsFromConfig.values.map(v => {
                       return <Link onClick={(e) => e.stopPropagation()} monochrome target="_blank" url={window.location.origin+"/dashboard/testing/roles/details?name="+v}>{v}</Link>
                   })}</div>
               break;
            
            default:
                break;

            }
            return ret
        } else {
            return <div/>
        }
    }

    function drawComponentToCreateNew(type, name) {
        let ret = null
        switch(type) {
            case "CUSTOM_AUTH":
               ret =
               <Link onClick={(e) => e.stopPropagation()}  target="_blank" url={window.location.origin+"/dashboard/settings/auth-types/details"}>
                Create
               </Link>
               break;
            case "ROLE":
                ret =  <Link onClick={(e) => e.stopPropagation()}  target="_blank" url={window.location.origin+"/dashboard/testing/roles/details?system="+name}>
               Create
               </Link>

               break;
            default:
                break;
        }

        return ret

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
                        let isDefault = (propsFromConfig.apiCollectionId == 0) && apiCollectionId != 0
                        return {
                            formattedValues: propsFromConfig.values.join(", "),
                            formattedValuesComp: drawComponentToEdit(propsFromConfig, propertyIds),
                            statusComp: <Badge status={isDefault ? "warning":"success"} progress="complete">{isDefault ? "Default":"Done"}</Badge>,
                            ...propsFromConfig,
                            ...ret
                        }
                    } else {
                        return {
                            formattedValues: "-",
                            formattedValuesComp: drawComponentToCreateNew(propsFromPossible.type, propsFromPossible.id),
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
            value: 'formattedValuesComp'
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
            <GithubSimpleTable
                key="critical"
                data={testCollectionProperties}
                resourceName={resourceName}
                headers={headers}
                useNewRow={true}
                condensedHeight={true}
                hideQueryField={true}
                headings={headers}
                hidePagination={true}
            />
        </div>

    )
}

export default TestCollectionConfiguration