import { memo, useCallback, useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Tree } from "react-arborist"

import { Badge, Box, Button, HorizontalStack, Icon, Navigation, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { ChevronDownMinor, ChevronRightMinor, SearchMinor } from "@shopify/polaris-icons"

import TestEditorStore from "../testEditorStore"
import convertFunc from "../transform"
import "../TestEditor.css"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import func from "../../../../../util/func"
import { isAgenticSecurityCategory } from "../../../../main/labelHelper"

const SEARCH_DEBOUNCE_MS = 300
const VIRTUAL_LIST_THRESHOLD = 80
const ROW_HEIGHT = 36
const MAX_LIST_HEIGHT = 520

const TestNavRow = memo(function TestNavRow({ node, style, selectedTestLabel, onSelectTest }) {
    const item = node.data.testItem
    const isActive = selectedTestLabel === item.label

    return (
        <div
            style={{ ...style, boxSizing: "border-box" }}
            className="Polaris-Navigation__ItemWrapper"
            onClick={() => onSelectTest(item)}
        >
            <div className="Polaris-Navigation__Item Polaris-Navigation__Item--secondary">
                <Tooltip content={item.label} dismissOnMouseOut width="wide" preferredPosition="below">
                    <div className={isActive ? "active-left-test" : ""}>
                        <Text
                            variant={isActive ? "headingSm" : "bodyMd"}
                            as="h4"
                            color={isActive ? "default" : "subdued"}
                            truncate
                        >
                            {item.label}
                        </Text>
                    </div>
                </Tooltip>
            </div>
        </div>
    )
})

function VirtualTestRows({ tests, selectedTestLabel, onSelectTest }) {
    const treeData = useMemo(
        () => tests.map((testItem) => ({ id: testItem.value, name: testItem.label, testItem })),
        [tests]
    )
    const height = Math.min(treeData.length * ROW_HEIGHT, MAX_LIST_HEIGHT)
    if (!treeData.length) return null

    return (
        <div className="Polaris-Navigation__SecondaryNavigation test-editor-virtual-subnav">
            <Tree
                data={treeData}
                openByDefault
                width="100%"
                height={height}
                indent={0}
                rowHeight={ROW_HEIGHT}
                overscanCount={12}
                disableDrag
                disableDrop
            >
                {(props) => (
                    <TestNavRow
                        {...props}
                        selectedTestLabel={selectedTestLabel}
                        onSelectTest={onSelectTest}
                    />
                )}
            </Tree>
        </div>
    )
}

const TestEditorFileExplorer = ({ addCustomTest }) => {
    const testObj = TestEditorStore((state) => state.testsObj)
    const selectedTestLabel = TestEditorStore((state) => state.selectedTest?.label)
    const setSelectedTest = TestEditorStore((state) => state.setSelectedTest)
    const contentSearchIndex = TestEditorStore((state) => state.contentSearchIndex)

    const [selectedCategory, setSelectedCategory] = useState("none")
    const [customItems, setCustomItems] = useState({ items: [], count: 0 })
    const [aktoItems, setAktoItems] = useState({ items: [], count: 0 })
    const [searchText, setSearchText] = useState("")
    const [debouncedSearch, setDebouncedSearch] = useState("")
    const [showCustom, setShowCustom] = useState(false)
    const [showAkto, setShowAkto] = useState(false)

    const navigate = useNavigate()

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearch(searchText), SEARCH_DEBOUNCE_MS)
        return () => clearTimeout(timer)
    }, [searchText])

    const selectedFunc = useCallback((val) => {
        setSelectedCategory((prev) => (prev === val ? "none" : val))
    }, [])

    const toggleFunc = (param) => {
        if (param === "CUSTOM") {
            setShowCustom((prev) => !prev)
            setShowAkto(false)
        } else {
            setShowAkto((prev) => !prev)
            setShowCustom(false)
        }
    }

    useEffect(() => {
        if (!testObj) return
        const q = convertFunc.normalizeSearchTerm(debouncedSearch)
        let searchObj = testObj
        if (q) {
            const filtered = convertFunc.getFilteredExplorerData(testObj, debouncedSearch, contentSearchIndex)
            searchObj = convertFunc.toFilteredTestObj(testObj, filtered)
        }
        setCustomItems(convertFunc.getNavigationItems(searchObj, "CUSTOM", selectedFunc))
        setAktoItems(convertFunc.getNavigationItems(searchObj, "Akto", selectedFunc))
    }, [testObj, debouncedSearch, contentSearchIndex, selectedFunc])

    useEffect(() => {
        if (!selectedTestLabel || !testObj) return
        const testData = testObj.mapTestToData[selectedTestLabel]
        if (!testData) return
        if (testData.type === "CUSTOM") {
            setSelectedCategory(testData.superCategory + "_custom")
        } else {
            setSelectedCategory(testData.superCategory + "_akto")
        }
    }, [selectedTestLabel, testObj])

    useEffect(() => {
        if (!testObj) return
        const selectedTest = TestEditorStore.getState().selectedTest
        if (!selectedTest) return
        const testData = testObj.mapTestToData[selectedTest.label]
        if (testData?.type) {
            toggleFunc(testData.type)
        }
    }, [])

    const onSelectTest = useCallback((item) => {
        navigate(`/dashboard/test-editor/${item.value}`)
        setSelectedTest(item)
    }, [navigate, setSelectedTest])

    const buildSubNavigationItems = useCallback((tests) => {
        if (!tests?.length) return []
        if (tests.length > VIRTUAL_LIST_THRESHOLD) {
            return [{
                label: (
                    <VirtualTestRows
                        tests={tests}
                        selectedTestLabel={selectedTestLabel}
                        onSelectTest={onSelectTest}
                    />
                ),
                onClick: () => {},
                key: "virtual-test-list",
            }]
        }
        return tests.map((item) => ({
            label: (
                <Tooltip content={item.label} dismissOnMouseOut width="wide" preferredPosition="below">
                    <div className={item.label === selectedTestLabel ? "active-left-test" : ""}>
                        <Text
                            variant={item.label === selectedTestLabel ? "headingSm" : "bodyMd"}
                            as="h4"
                            color={item.label === selectedTestLabel ? "default" : "subdued"}
                            truncate
                        >
                            {item.label}
                        </Text>
                    </div>
                </Tooltip>
            ),
            onClick: () => onSelectTest(item),
            key: item.value,
        }))
    }, [selectedTestLabel, onSelectTest])

    const getItems = useCallback((navItems) => {
        let arr = navItems.map((obj) => {
            const isExpanded = selectedCategory === obj.key + obj.param
            return {
                ...obj,
                selected: isExpanded,
                icon: isExpanded ? ChevronDownMinor : ChevronRightMinor,
                subNavigationItems: isExpanded ? buildSubNavigationItems(obj.subNavigationItems) : [],
            }
        })
        if (isAgenticSecurityCategory()) {
            arr = func.sortByCategoryPriority(arr, "key")
        }
        return arr
    }, [selectedCategory, buildSubNavigationItems])

    const customNavItems = useMemo(() => getItems(customItems.items), [customItems.items, getItems])
    const aktoNavItems = useMemo(() => getItems(aktoItems.items), [aktoItems.items, getItems])

    return (
        <div className="editor-navbar" style={{ overflowY: "scroll", overflowX: "hidden", width: "18rem" }}>
            <Navigation location="/">
                <VerticalStack gap="4">
                    <TextField
                        id="test-search"
                        prefix={<Icon source={SearchMinor} />}
                        onChange={setSearchText}
                        value={searchText}
                        placeholder="Search for Tests"
                        autoComplete="off"
                    />

                    <Box>
                        <Button
                            id="create-custom-test-button"
                            plain
                            monochrome
                            onClick={() => toggleFunc("CUSTOM")}
                            removeUnderline
                            fullWidth
                        >
                            <HorizontalStack align="space-between">
                                <HorizontalStack gap="1">
                                    <Box>
                                        <Icon source={showCustom ? ChevronDownMinor : ChevronRightMinor} />
                                    </Box>
                                    <TitleWithInfo
                                        tooltipContent="Custom tests"
                                        titleText="Custom"
                                        textProps={{ variant: "headingMd" }}
                                        docsUrl="https://docs.akto.io/test-editor/concepts/custom-test"
                                    />
                                </HorizontalStack>
                                <div style={{ marginRight: "-2px" }}>
                                    <Badge size="small" status="new">{customItems.count.toString()}</Badge>
                                </div>
                            </HorizontalStack>
                        </Button>
                        {showCustom ? <Navigation.Section items={customNavItems} /> : null}
                    </Box>
                    <Box>
                        <Button plain monochrome onClick={() => toggleFunc("Akto")} removeUnderline fullWidth>
                            <HorizontalStack align="space-between">
                                <HorizontalStack gap="1">
                                    <Box>
                                        <Icon source={showAkto ? ChevronDownMinor : ChevronRightMinor} />
                                    </Box>
                                    <TitleWithInfo
                                        tooltipContent="Akto's test library"
                                        titleText="Akto default"
                                        textProps={{ variant: "headingMd" }}
                                        docsUrl="https://docs.akto.io/test-editor/concepts/test-library"
                                    />
                                </HorizontalStack>
                                <div style={{ marginRight: "-2px" }}>
                                    <Badge size="small" status="new">{aktoItems.count.toString()}</Badge>
                                </div>
                            </HorizontalStack>
                        </Button>
                        {showAkto ? <Navigation.Section items={aktoNavItems} /> : null}
                    </Box>
                </VerticalStack>
            </Navigation>
        </div>
    )
}

export default TestEditorFileExplorer
