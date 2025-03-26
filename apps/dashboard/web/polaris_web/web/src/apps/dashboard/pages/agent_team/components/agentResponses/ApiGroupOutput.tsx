import React, { useState } from "react"
import { intermediateStore } from "../../intermediate.store";
import { Box, Button, List, Text, VerticalStack } from "@shopify/polaris";
import PersistStore from "../../../../../main/PersistStore";

function ApiGroupOutput() {
    const { outputOptions } = intermediateStore();

    const data = outputOptions?.outputOptions || []

    const allCollections: {
        displayName: string; id: number
    }[] = PersistStore(state => state.allCollections);

    const [showAll, setShowAll] = useState(new Set())
    const maxAPIsShown = 5

    return (
        <VerticalStack gap={"3"}>
            {
                data.map((x, j) => {
                    const moreApis = x.apis.length - maxAPIsShown
                    const showJustOneMore = x.apis.length - 1 == maxAPIsShown
                    const showMoreApis = !showJustOneMore && x.apis.length > maxAPIsShown

                    const listItems = x.apis.filter((x, i) => {
                        if (showAll.has(j)) {
                            return true
                        }
                        return showJustOneMore ? i < maxAPIsShown + 1 : i < maxAPIsShown
                    }).map(x => {
                        const collectionName = allCollections.filter(collection => collection.id === x.apiCollectionId)?.[0]?.displayName || ""
                        return <List.Item >
                            {`${x.method} ${x.url} ${collectionName.length > 0 ? "| Collection:" : ""} ${collectionName}`}
                        </List.Item>
                    })
                    return <VerticalStack inlineAlign={"start"} gap={"4"}>
                        <Box paddingInlineStart={"3"}>
                            <Text as={"dd"} fontWeight={"medium"} >{`${x.textValue} (${j + 1}/${data.length})`}</Text>
                        </Box>
                        <VerticalStack>
                            <List type={"number"} spacing={"extraTight"}>
                                {listItems}
                            </List>
                            <Box paddingInlineStart={"3"}>
                                {
                                    showMoreApis ? <Text as={"dd"} color={"subdued"}>
                                        <Button onClick={() => {
                                            setShowAll(x => {
                                                const y = new Set(x)
                                                if (y.has(j)) {
                                                    y.delete(j)
                                                } else {
                                                    y.add(j)
                                                }
                                                return y
                                            })
                                        }} plain monochrome >
                                            {`${showAll.has(j) ? "Hide" : "Show"} ${moreApis} API${moreApis === 1 ? "" : "s"}`}
                                        </Button></Text> : <></>
                                }
                            </Box>
                        </VerticalStack>
                    </VerticalStack>
                })
            }
        </VerticalStack>
    )
}

export default ApiGroupOutput