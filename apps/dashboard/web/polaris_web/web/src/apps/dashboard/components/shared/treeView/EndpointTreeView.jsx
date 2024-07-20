import { LegacyCard, Box, Text, Icon, HorizontalStack } from "@shopify/polaris"
import TreeView, { flattenTree } from "react-accessible-treeview";
import { FolderMajor, FolderDownMajor } from "@shopify/polaris-icons";
import "./styles.css";

function EndpointTreeView({ treeList }) {

    const data = treeList

    return (
        <LegacyCard>
            <Box padding={2} className="directory">
                <TreeView
                    aria-label="directory tree"
                    data={data}
                    nodeRenderer={({ element, getNodeProps, isBranch, isExpanded, level, handleSelect }) => (
                        <div {...getNodeProps()} style={{ paddingLeft: 20 * (level - 1) }}>
                            {isBranch ? (
                                <HorizontalStack>
                                    <span><Icon source={isExpanded ? FolderDownMajor : FolderMajor} /></span>
                                    <span>{element.name}</span>
                                </HorizontalStack>
                                
                            ) : (
                                <Text>{element.name}</Text>
                            )}
                      </div>
                    )}
                />
            </Box>
        </LegacyCard>

    )
}

export default EndpointTreeView