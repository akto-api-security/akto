import { Badge, Box, HorizontalStack } from "@shopify/polaris";
import transform from "../../../pages/observe/transform";
import PrettifyChildren from "./PrettifyChildren";
import TooltipText from "../TooltipText";

const treeViewFunc = {
    pruneTree(tree, branchFieldSplitter, reverse) {
        // Prune the tree by collapsing nodes with single children
        const keys = Object.keys(tree);

        keys.forEach(key => {
            let nodeCollapsed = false
            let newKey = ""
            let node =  tree[key]
            
            do {
                // Collapse nodes (greeedy approach)
                nodeCollapsed = false

                if (!node.isTerminal) {
                    if (Object.keys(node.children).length === 1) {
                        const childNodeKey = Object.keys(node.children)[0]
                        const childNode = node.children[childNodeKey]
    
                        if (!childNode.isTerminal) {
                            // If parent contains single child and both parent and child are terminal, collapse the node
                            if (reverse) {
                                newKey = `${childNodeKey}${branchFieldSplitter}${key}`
                            } else {
                                newKey = `${key}${branchFieldSplitter}${childNodeKey}`
                            }
                            
                            // Delete the old node and create a new node with the combined key
                            delete tree[key]
                            tree[newKey] = { isTerminal: false, children: childNode.children, items: [] }
                            nodeCollapsed = true

                            key = newKey
                            node = tree[newKey]

                        }
                    }
                }
            } while (nodeCollapsed)
            
            this.pruneTree(tree[key].children, branchFieldSplitter, reverse)
        })
    },
    buildTree(items, branchField, branchFieldSplitter, reverse=false, secondaryBranch=false, secondaryBranchFieldSplitter, headers, shouldPrune) {
        const itemsTree = {}

        items.forEach(item => {
            const branchFieldValue = item?.[branchField]
            let branchFieldValueParts = branchFieldValue?.split(branchFieldSplitter) || []
            
            if (reverse) {
                branchFieldValueParts.reverse()
            }

            let currentNode = itemsTree
            branchFieldValueParts.forEach((part, index) => {
                if (!currentNode[part]) {
                    // Create a new node if it does not exist
                    currentNode[part] = { isTerminal: false, children: {}, items: [] };
                }
                if (index === branchFieldValueParts.length - 1) {
                    // Mark the node as terminal if it is the last part of the branch and set items field
                    if (secondaryBranch) {
                        const secondaryBranchFieldValue = part
                        const secondaryBranchFieldValueParts = secondaryBranchFieldValue.split(secondaryBranchFieldSplitter).filter(part => part)
                        
                        if (secondaryBranchFieldValueParts.length === 2) {
                            // delete the created leaf node 
                            delete currentNode[part]

                            // create secondary branch node if it does not exist
                            const secondaryBranchFieldKey = secondaryBranchFieldValueParts[0]
                            if (!currentNode[secondaryBranchFieldKey]) {
                                currentNode[secondaryBranchFieldKey] = { isTerminal: false, children: {}, items: [] };
                            }

                            currentNode = currentNode[secondaryBranchFieldKey].children;
                            // create a new leaf node
                            currentNode[part] = { isTerminal: false, children: {}, items: [] };
                        }
                    }
                    
                    // Mark the node as terminal if it is the last part of the branch and set items field
                    currentNode[part].isTerminal = true; 
                    currentNode[part].items.push(item)
                }
                currentNode = currentNode[part].children;
            })
        })
        if(shouldPrune){
            this.pruneTree(itemsTree, branchFieldSplitter, reverse)
        }
        let finalResult = []
        Object.keys(itemsTree).forEach((x) => {
            const result = this.dfs(itemsTree[x], x, headers)
            finalResult.push(result)
        })
        return finalResult;
    },

    getFinalKey(value, filterKey, numericValue){
        let finalKey = value
        if(filterKey !== null && filterKey !== undefined){
            finalKey = filterKey
        }else if(numericValue !==null && numericValue !== undefined){
            finalKey = numericValue
        }
        return finalKey
    },

    mergeNodeData(children, headers) {
        let mergedNode = {};
        
        headers.forEach((h) => {
            const key = this.getFinalKey(h?.value, h?.filterKey, h?.numericValue)
            if(key !== 'displayName'){
                mergedNode[key] = children[0][key]
            }else{
                if(children[0].hasOwnProperty('apiCollectionIds')){
                    mergedNode['apiCollectionIds'] = children[0].apiCollectionIds
                }else{
                    mergedNode['apiCollectionIds'] = [children[0]['id']]
                }
            }
            
        })
        
        if(children.length === 1)return mergedNode

        children.slice(1, children.length).forEach((c) => {
            headers.forEach((h) => {
                const  {value, filterKey, numericValue, mergeType} = h
                const key = this.getFinalKey(value, filterKey, numericValue)
                if(key !== 'displayName'){
                    mergedNode[key] = mergeType(mergedNode[key], c[key])
                }else{
                    let finalArr = []
                    if(c.hasOwnProperty('id')){
                        finalArr = [c.id]
                    }else{
                        finalArr = c.apiCollectionIds
                    }

                    let temp = mergedNode['apiCollectionIds'] || []
                    temp = [...temp, ...finalArr]
                    mergedNode['apiCollectionIds'] = [...new Set(temp)]
                }
            })
        })
    
        return mergedNode;
    },

    dfs(node, level = '', headers = [], visited = new Set()) {
        if (visited.has(node)) return null;
        visited.add(node);
    
        let result = {};
        let children = [];
    
        if (node.children) {
            Object.keys(node.children).forEach((key) => {
                const child = node.children[key];
                const childResult = this.dfs(child, level ? `${level}#${key}` : key, headers, visited);
                if (childResult) children.push(childResult);
            });
        }
    
        if (node.items && node.items.length > 0) {
            node.items.forEach((item) => {
                item.level = level;
                item.isOpen = false;
                children.push(item);
            });
        }
    
        if (children.length > 1) {
            const mergedNode = this.mergeNodeData(children, headers);
            mergedNode.level = level;
            mergedNode.isTerminal = false;
            mergedNode.children = children;
            result = mergedNode;
        } else if (children.length === 1) {
            // If there's only one child, merge it directly and mark it as terminal
            result = children[0];
            result.level = level;
            result.isTerminal = true;
        } else {
            result = { level, isTerminal: true, ...node };
        }
    
        return result;
    },
    prettifyTreeViewData(normalData, headers){
        return normalData.map((c) => {
            return{
                ...c,
                id: c.apiCollectionIds || c.id,
                displayNameComp: c?.isTerminal ? (
                    <Box maxWidth="180px">
                        <TooltipText tooltip={c.displayName} text={c.displayName} />
                    </Box>
                )
                : (
                        <HorizontalStack gap={"1"} align="space-between" wrap={false}>
                            <Box maxWidth="200px">
                                <TooltipText tooltip={c.level} text={c.level} textProps={{variant: 'headingSm'}} />
                            </Box>
                            <Badge size="small" status="new">{c?.apiCollectionIds?.length}</Badge>
                        </HorizontalStack>),
                ...transform.convertToPrettifyData(c),
                makeTree: (data) => <PrettifyChildren data={data?.children} headers={headers} />
            }
        })
    }
}

export default treeViewFunc;