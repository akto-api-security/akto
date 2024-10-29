import { Box, Button, Divider, HorizontalStack, Icon, ResourceItem, ResourceList, Text, TextField } from '@shopify/polaris'
import { SearchMinor, ChevronUpMinor, ChevronDownMinor } from '@shopify/polaris-icons'
import React, { useEffect, useState } from 'react'
import Dropdown from '../../../components/layouts/Dropdown'
import LocalStore from '../../../../main/LocalStorageStore'
import useTable from '../../../components/tables/TableContext'
import TableStore from '../../../components/tables/TableStore'
import ActiveTestingCheckbox from './ActiveTestingCheckbox'
import { debounce } from 'lodash'

const TestSuiteResourceList = ({ setSelectedTests, testSuite, setTestSuite, isCreatingTestSuite }) => {
    const [displayItems , setDisplayItems] = useState([])
    const [selectedItems, setSelectedItems] = useState([])
    const [testSuiteMenuItems, setTestSuiteMenuItems] = useState([])
    const [selectedTestSuite, setSelectedTestSuite] = useState()
    const [searchValue, setSearchValue] = useState("")
    const [testTemplates, setTestTemplates] = useState([{}])
    const [showCollapsibleCell, setShowCollapsibleCell] = useState({})
    const [expendAll, setExpendAll] = useState(true)

    const subCategoryMap = LocalStore.getState().subCategoryMap
    const createSuperCategoryArray = () => {
        const superCategoryArray = []
    
        for (const key in subCategoryMap) {
            const item = subCategoryMap[key]
            const superCategoryName = item.superCategory.name
            const testName = item.testName
            const testId = item.name
    
            const existingCategory = superCategoryArray.find(cat => cat.name === superCategoryName)
    
            if (existingCategory) {
                existingCategory.testNames.push(testName)
                existingCategory.testIds.push(testId)
            } else {
                superCategoryArray.push({
                    name: superCategoryName,
                    testNames: [testName],
                    testIds: [testId]
                })
            }
        }

        setTestTemplates(superCategoryArray)
        setDisplayItems(superCategoryArray)
    }

    const fetchData = async () => {
        setTestSuiteMenuItems([])
    }

    useEffect(() => {
        fetchData()
    }, [])

    useEffect(() => {
        createSuperCategoryArray()
    }, [subCategoryMap])
    

    const debouncedSearch = debounce((searchQuery) => {
        let selectedItemList = selectedItems
        setSelectedItems([])
        if(searchQuery.length === 0){
            setDisplayItems(testTemplates)
        } else {
            const resultArr = testTemplates.filter((x) => {
                const query = searchQuery.toLowerCase()
                const isInName = x.name.toLowerCase().includes(query);
                const isInTestNames = x.testNames.some(testName => testName.toLowerCase().includes(query))
                return isInName || isInTestNames
            })

            setDisplayItems(resultArr)
            setTimeout(() => {
                setSelectedItems(selectedItemList)
            }, 0)
        }
    }, 500)

    const searchResult = (item) =>{
        setSearchValue(item)
        debouncedSearch(item)
    }

    const expendAllItems = () => {
        if(Object.keys(showCollapsibleCell).length !== testTemplates.length) {
            const newState = {}
            displayItems.forEach(item => {
                newState[item.name] = true
            })
            setShowCollapsibleCell(newState)
        }

        setShowCollapsibleCell((prev) => {
            const newState = {}
            for (const key in prev) {
                newState[key] = expendAll
            }
            return newState
        })

        setExpendAll((prev) => !prev)
    }

    function CollapsibleCell({ testNames, testIds }) {
        return (
            <>
                {testNames.map((testName, index) => (
                    <Box width='100%' paddingInlineStart={16} paddingInlineEnd={5} key={index} id={"collapsible-cell"}>
                        <HorizontalStack align={"start"} wrap={false} gap={"2"}>
                            <Box paddingBlockStart={1} paddingBlockEnd={1}>
                                <ActiveTestingCheckbox testName={testName} testId={testIds[index]} setSelectedTests={setSelectedTests} />
                            </Box>
                        </HorizontalStack>
                    </Box>
                ))}
            </>
        )
    }

    const {selectItems} = useTable()
    const handleOnSelectionChange = (selectedItemList) => {
        const selectedIndices = selectedItemList.map(Number)
        const allTestNames = selectedIndices.flatMap(index => {
            const item = displayItems[index]
            return item ? item.testIds : []
        })

        const selectedStoreItems = TableStore.getState().selectedItems.flat()
        let newSelectedItems = selectedStoreItems.filter(item => allTestNames.includes(item))
        allTestNames.forEach(item => {
            if (!newSelectedItems.includes(item)) {
                newSelectedItems.push(item);
            }
        })
        TableStore.getState().setSelectedItems(newSelectedItems)
        selectItems(newSelectedItems)

        setSelectedTests(newSelectedItems)
        setSelectedItems(selectedItemList)
    }

    function renderItem(item, id, index) {
        const {name, testNames, testIds} = item
        return (
            <>
                <ResourceItem id={`${index}`} onClick={() => setShowCollapsibleCell((prev) => {
                    return { ...prev, [name]: !prev[name] }
                })}>
                    <HorizontalStack align='space-between'>
                        <Text>
                            {name}
                        </Text>
                        <HorizontalStack gap={2}>
                            <Text>0/{testNames?.length ?? 0}</Text>
                            <Icon source={showCollapsibleCell[name] ? ChevronUpMinor : ChevronDownMinor} />
                        </HorizontalStack>
                    </HorizontalStack>
                </ResourceItem>

                {showCollapsibleCell[name] ? <CollapsibleCell testNames={testNames} testIds={testIds} /> : undefined}
            </>
        )
    }

    const SearchIcon =  (
        <Box>
            <Icon source={SearchMinor} />   
        </Box>
    )

    const headerComponent = (
        <Box style={{margin: '-16px'}}>
            <Box background='bg-subdued' padding={5}>
                <HorizontalStack blockAlign='end' align='space-between' gap={5}>
                    <Box width='38%'>
                        {
                            isCreatingTestSuite ?
                            <TextField
                                label={"Test Suite Name"}
                                value={testSuite}
                                onChange={setTestSuite}
                                placeholder='Your test suite name'
                                maxLength="64"
                                suffix={(
                                    <Text>{testSuite?.length ?? 0}/64</Text>
                                )}
                            /> : <Dropdown
                                label={"Select Test Suite"}
                                menuItems={testSuiteMenuItems}
                                initial={selectedTestSuite}
                                placeHolder={"Select test suite"}
                                selected={setSelectedTestSuite}
                            />
                        }
                    </Box>

                    <div style={{alignSelf: 'end', width: '58%'}}>
                        <TextField
                            prefix={SearchIcon}
                            onChange={searchResult}
                            value={searchValue}
                            placeholder={`Search from ${testTemplates.length} test${testTemplates.length > 1 ? 's' : ''}`}
                        />
                    </div>
                </HorizontalStack>
            </Box>
            <Box paddingBlockEnd={"5"}>
                <Divider />
            </Box>
        </Box>
    )

    return (
        <ResourceList
            headerContent="Select All Tests"
            items={displayItems}
            renderItem={renderItem}
            selectedItems={selectedItems}
            onSelectionChange={handleOnSelectionChange}
            selectable
            filterControl={headerComponent}
            alternateTool={<Button onClick={expendAllItems} plain>{expendAll ? "Expand all" : "Collapse all"}</Button>}
        />
    )
}

export default TestSuiteResourceList