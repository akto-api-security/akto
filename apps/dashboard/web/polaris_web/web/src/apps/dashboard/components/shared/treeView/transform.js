
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
    buildTree(items, branchField, branchFieldSplitter, reverse=false, secondaryBranch=false, secondaryBranchFieldSplitter, headers) {
        const itemsTree = {}

        items.forEach(item => {
            const branchFieldValue = item?.[branchField]
            let branchFieldValueParts = branchFieldValue?.split(branchFieldSplitter)?.filter(part => part) || []
            
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
                        
                        if (secondaryBranchFieldValueParts.length == 2) {
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

        this.pruneTree(itemsTree, branchFieldSplitter, reverse)
        const result = this.flattenTree(itemsTree, headers)
        return result ;
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
        const mergedNode = {};
    
        headers.forEach(header => {
            const { value, filterKey, numericValue, mergeType } = header
            const finalKey = this.getFinalKey(value, filterKey, numericValue)
            if(finalKey !== 'displayName'){
                mergedNode[finalKey] = children.reduce((acc, child) => {
                    return mergeType(acc, child[finalKey]);
                }, children[0][finalKey]);
            }
        });
    
        return mergedNode;
    },
    
    flattenTree(tree, headers, path = '') {
        const result = [];
    
        Object.keys(tree).forEach(key => {
            const currentNode = tree[key];
            const currentPath = path ? `${path}#${key}` : key;
    
            if (currentNode.isTerminal) {
                currentNode.items.forEach(item => {
                    result.push({
                        ...item,
                        level: currentPath,
                        showInRow: false
                    });
                });
            } else {
                const childNodes = this.flattenTree(currentNode.children, headers, currentPath);
                const childrenData = childNodes.filter(node => node.level.startsWith(currentPath));
                const mergedData = this.mergeNodeData(childrenData, headers);
    
                result.push({
                    ...mergedData,
                    level: currentPath,
                    isTerminal: false,
                    showInRow: true
                });
    
                result.push(...childNodes);
            }
        });
    
        return result;
    }
}

export default treeViewFunc;