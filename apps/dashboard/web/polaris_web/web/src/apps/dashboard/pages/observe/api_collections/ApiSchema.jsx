import { VerticalStack, Box, Button, ButtonGroup, HorizontalStack, Icon, Text, Collapsible, Scrollable, DataTable, Badge } from "@shopify/polaris"
import { useCallback, useEffect, useState } from "react";
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func"
import transform from "../transform";

function prepareTableData (data) {
    let sensitivePayload = []
    let normalPayload = []
    let standardHeader = []
    let customHeader = []
    let sensitiveHeader = []

    const standardHeadersList = transform.getStandardHeaderList()
    let tabSensitive = ""

    data.forEach((element,index) => {
        let paramText = element.param.replaceAll("#", ".").replaceAll(".$", "")
        let isSensitive = func.isSubTypeSensitive(element)
        let comp = [(<HorizontalStack gap={"2"} key={index}>
            <Text fontWeight="regular" variant="bodyMd">
                {paramText}
            </Text>
            {
                isSensitive ?
                    <Badge status="warning">
                        {element.subType.name}
                    </Badge> : null
            }
            </HorizontalStack>), <Text variant="bodySm" fontWeight="regular" color="subdued">{func.prepareValuesTooltip(element)}</Text>
        ]
        if(element.isHeader){
            if(isSensitive){
                sensitiveHeader.push(comp)
                tabSensitive = "Header"
            }
            else if(standardHeadersList.includes(paramText)){
                standardHeader.push(comp)
            }else{
                customHeader.push(comp)
            }
        }else{
            if(isSensitive){
                sensitivePayload.push(comp)
                tabSensitive = "Payload"
            }else{
                normalPayload.push(comp)
            }
        }
    })

    const headers = [...sensitiveHeader,...customHeader,...standardHeader]
    const payload = [...sensitivePayload, ...normalPayload]

    return{
        headerData: headers,
        payloadData: payload,
        tabSensitive : tabSensitive,
    }
}

function ApiSingleSchema(props) {
    const { data, title, badgeActive, setBadgeActive } = props;

    const [open, setOpen] = useState(true);
    const handleToggle = useCallback(() => setOpen((open) => !open), []);
    const [isHeader, setIsHeader] = useState(true)

    const [dataObj,setDataObj] = useState({
        headerData: [],
        payloadData: [],
        tabSensitive: ''
    });
    useEffect(()=>{
        setDataObj(prepareTableData(data));
    },[])
    const headerCount = dataObj?.headerData?.length
    const payloadCount = dataObj?.payloadData?.length

    const activeTab = badgeActive ? (dataObj.tabSensitive === "Header") : isHeader

    return (
        <VerticalStack gap={"2"}>
            <Box background={"bg-subdued"} width="100%" padding={"2"} onClick={handleToggle}>
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">
                        {title}
                    </Text>
                    <Box>
                        <Icon source={open ? ChevronDownMinor : ChevronUpMinor} />
                    </Box>
                </HorizontalStack>
            </Box>
            <Collapsible
                open={open}
                id="basic-collapsible"
                transition={{ duration: '200ms', timingFunction: 'ease-in-out' }}
                expandOnPrint
            >
                <VerticalStack gap={"2"}>
                    <ButtonGroup segmented>
                        <Button primarySuccess={activeTab} onClick={() => {setBadgeActive(false); setIsHeader(true)}} size="slim">
                            <Box paddingBlockStart="05" paddingBlockEnd="05"> 
                                <HorizontalStack gap="2">
                                    <Text variant="bodyMd">Header</Text>
                                    <span style={{padding: '4px 8px', width: '30px', color: '#202223', background:(isHeader ? "#ECEBFF" : "#E4E5E7"), borderRadius: '4px'}}>
                                        {headerCount}
                                    </span>
                                </HorizontalStack>
                            </Box>
                        </Button>
                        <Button primarySuccess={!activeTab} onClick={() => {setBadgeActive(false); setIsHeader(false)}} size="slim">
                            <Box paddingBlockStart="05" paddingBlockEnd="05"> 
                                <HorizontalStack gap="2">
                                    <Text variant="bodyMd">Payload</Text>
                                    <span style={{padding: '4px 8px', width: '30px', color: '#202223', background:(!isHeader ? "#ECEBFF" : "#E4E5E7"), borderRadius: '4px'}}>
                                        {payloadCount}
                                    </span>
                                </HorizontalStack>
                            </Box>
                        </Button>
                    </ButtonGroup>
                    <Scrollable style={{ height: '25vh' }} focusable>
                        <DataTable
                            headings={[]}
                            columnContentTypes={[
                                'text',
                                'numeric'
                            ]}
                            rows={activeTab ? dataObj.headerData : dataObj.payloadData}
                            increasedTableDensity
                            truncate
                        >
                        </DataTable>
                    </Scrollable>
                </VerticalStack>
            </Collapsible>
        </VerticalStack>
    )
}

function ApiSchema(props) {

    const { data, badgeActive, setBadgeActive } = props

    let reqData = data.filter((item) => item.responseCode === -1)
    let resData = data.filter((item) => item.responseCode !== -1)

    return (
        <VerticalStack gap="2">
            {
                ['Request', 'Response'].map((type, index) => {
                    return <ApiSingleSchema title={type} key={type} data={index == 0 ? reqData : resData} badgeActive={badgeActive} setBadgeActive={setBadgeActive}/>
                })
            }

        </VerticalStack>
    )

}

export default ApiSchema