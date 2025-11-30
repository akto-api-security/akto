import {
    IndexTable,
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    Button,
    Popover,
    ActionList,
    Link,
    Box,
    Icon
} from '@shopify/polaris';
import {
    HorizontalDotsMinor, ChevronDownMinor, ChevronRightMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";
import { useState, useEffect } from 'react';
import './row.css'
import GithubCell from '../cells/GithubCell';
import func from "@/util/func"
import TooltipText from '../../shared/TooltipText';

const CellType = {
    TEXT: "TEXT",
    ACTION: "ACTION",
    COLLAPSIBLE: "COLLAPSIBLE"
}

function GithubRow(props) {

    const {dataObj, getNextUrl, selectedResources, index, headers, hasRowActions, getActions, onRowClick, getStatus, headings, popoverActive, setPopoverActive } = props;
    const navigate = useNavigate();
    const [data, setData] = useState(dataObj);
    const [collapsibleActive, setCollapsibleActive] = useState("none")

    const togglePopoverActive = (e,index) => {
        if(e.stopPropagation){
            e.stopPropagation();
        }
        setPopoverActive((prev) => {
            if(prev === index)
                return -1
            return index
        })
    }
    async function nextPage(data){
        if(data?.nextUrl || getNextUrl){
            navigate(data?.nextUrl) || (getNextUrl && navigate(await getNextUrl(data.id), {replace:true}));
        }
    }

    function handleRowClick(data){

        if(data.deactivated){
            return;
        }

        if(data?.collapsibleRow || (props?.treeView && data?.isTerminal !== true)){
            setCollapsibleActive((prev) => {
                if(prev===data?.name){
                    return "none";
                } 
                return data?.name;
            })
        }
        else if(onRowClick){
            onRowClick(data);
        } else {
            nextPage(data);
        }
    }

    useEffect(() => {
        setData((prev) => {
            if (func.deepComparison(prev, dataObj)) {
                return prev;
            }
            return { ...dataObj };
        })
    }, [dataObj,collapsibleActive])

    function OldCell(){
        return(
            <>
            <IndexTable.Cell>
                <div className='linkClass'>
                    <Link
                        monochrome
                        removeUnderline
                        onClick={() => handleRowClick(data)}
                    >
                        <GithubCell
                            headers={headers}
                            data={data}
                            getStatus={getStatus}
                            width="65vw"
                            nameWidth="50vw"
                        />
                    </Link>
                </div>
            </IndexTable.Cell>
            {headers?.filter((header) => {
                return header.itemCell == 2
            }).filter((header) => {
                return data[header.value] != undefined
            }).map((header) => {
                return (
                    <IndexTable.Cell key={header.text}>
                        <VerticalStack gap="2">
                            <Text variant='bodyMd' fontWeight="medium">
                                {header.text}
                            </Text>
                            <HorizontalStack>
                                <Badge key={header.text}>
                                    {data[header.value]}
                                </Badge>
                            </HorizontalStack>
                        </VerticalStack>
                    </IndexTable.Cell>
                )
            })
            }
            {hasRowActions &&
                <IndexTable.Cell >
                    <HorizontalStack align='end'>
                        {
                            <Popover
                                active={popoverActive === data.id}
                                activator={<Button onClick={(e) => togglePopoverActive(e,data.id)} plain icon={HorizontalDotsMinor} />}
                                autofocusTarget="first-node"
                                onClose={(e) => togglePopoverActive(e,popoverActive)}
                            >
                                <ActionList
                                    actionRole="menuitem"
                                    sections={getActions(data)}
                                />
                            </Popover>
                        }
                    </HorizontalStack>
                </IndexTable.Cell>
            }
        </>
        )
    }

    function LinkCell(cellData, header, cellWidth, customKey) {
        const boxWidth = cellWidth !== undefined ? cellWidth: ''
        // Use customKey if provided, otherwise use text property as key fallback if title is an object (JSX element)
        const cellKey = customKey || (typeof header.title === 'string' ? header.title : (header.text || header.value || 'cell'));
        return (
            <IndexTable.Cell key={cellKey}>
                <div className={`linkClass ${data.deactivated ? "text-subdued" : ""}`} style={{width: boxWidth}}>
                    <Link
                        dataPrimaryLink
                        monochrome
                        removeUnderline
                        {...(data?.nextUrl !== undefined ? { url: data.nextUrl } : {})}
                    >
                        {cellData}
                    </Link>
                </div>
            </IndexTable.Cell>
        )
    }

    function TextCell(header) {
        return (
            <Box maxWidth={header.maxWidth ? header.maxWidth : ''} key={header.value}>
                <TooltipText text={data[header.value]} tooltip={data[header.value]} />
            </Box>
        )
    }

    function ActionCell(customKey) {
        return (
            <IndexTable.Cell key={customKey || "actions"}>
                <HorizontalStack align='end'>
                    {
                        <Popover
                            active={popoverActive === data.id}
                            activator={<Button onClick={(e) => togglePopoverActive(e,data.id)} plain icon={HorizontalDotsMinor} />}
                            autofocusTarget="first-node"
                            onClose={(e) => togglePopoverActive(e,popoverActive)}
                        >
                            <ActionList
                                actionRole="menuitem"
                                sections={getActions(data)}
                            />
                        </Popover>
                    }
                </HorizontalStack>
            </IndexTable.Cell>
        )
    }

    function CollapsibleCell(treeView, value, customKey) {
        let iconSource = ChevronRightMinor
        if(collapsibleActive === data?.name){
            iconSource = ChevronDownMinor
        }
        return (
            <IndexTable.Cell key={customKey || "collapsible"}>
                <Box maxWidth={treeView ? "180px": ''} >
                    <HorizontalStack align={treeView ? "start" : "end"} wrap={false} gap={"2"}>
                        <Box><Icon source={iconSource} /></Box>
                        {treeView ? value : null}
                    </HorizontalStack>
                </Box>
            </IndexTable.Cell>
        )
    }

    function getHeader(header, index){
        let type = header?.type;
        // Create a unique key for this cell using index and header properties
        const cellKey = `${header.text || header.value || 'header'}-${index}`;

        switch(type){

            case CellType.ACTION :
                return hasRowActions ? ActionCell(cellKey) : null;
            case CellType.COLLAPSIBLE :
                if(props?.treeView){
                    if(data?.isTerminal === true){
                        return header.value ? LinkCell(data[header.value], header, undefined, cellKey) : null
                    }
                }
                return CollapsibleCell(props?.treeView, data[header?.value], cellKey);
            case CellType.TEXT :
                return header.value ? LinkCell(TextCell(header), header, header?.boxWidth, cellKey) : null;
            default :
                return header.value ? LinkCell(data[header.value], header, header?.boxWidth, cellKey) : null;
        }
    }

    function NewCell(){
        return(
            <>
                {headings.map((header, index) =>{
                    return getHeader(header, index);
                })}
            </>
        )
    }
    return (
        <>
            <IndexTable.Row
                id={data.id}
                key={data.id}
                position={index}
                {...props.newRow ? {status: ((index % 2) ? "subdued" : '')} : {} }
                {...props.notHighlightOnselected ? {} : {selected: selectedResources.includes(data?.id)}}
                onClick={() => handleRowClick(data)}
            >
                {props?.newRow ? <NewCell /> :<OldCell/>}   
            </IndexTable.Row>
            
            {collapsibleActive === data?.name ? ( props?.treeView ? data?.makeTree(data) : data?.collapsibleRow) : null}
            
        </>
    )

}

export {GithubRow, CellType};