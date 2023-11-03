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
    HorizontalDotsMinor, ChevronDownMinor, ChevronUpMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";
import { useState, useCallback, useEffect} from 'react';
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

    const {dataObj, getNextUrl, isRowClickable, selectedResources, index, headers, hasRowActions, getActions, onRowClick, getStatus, headings } = props;
    const navigate = useNavigate();
    const [popoverActive, setPopoverActive] = useState(-1);
    const [data, setData] = useState(dataObj);
    const [collapsibleActive, setCollapsibleActive] = useState("none")

    const togglePopoverActive = (index) => useCallback(
        () => setPopoverActive((prev) => {
            if(prev==index){
                return -1;
            } 
            return index;
        }),
        [],
    );
    async function nextPage(data){
        if(data?.nextUrl || getNextUrl){
            navigate(data?.nextUrl) || (getNextUrl && navigate(await getNextUrl(data.id), {replace:true}));
        }
    }

    function handleRowClick(data){
        if(data?.collapsibleRow){
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

    const [rowClickable, setRowClickable] = useState(isRowClickable || false)

    useEffect(() => {
        setData((prev) => {
            if(func.deepComparison(prev,dataObj))
            {
                return prev;
            }
            return {...dataObj};
        })
    }, [dataObj,collapsibleActive])

    function OldCell(){
        return(
            <>
            <IndexTable.Cell>
                <div className='linkClass'>
                    <Link
                        {...(rowClickable ? { dataPrimaryLink: rowClickable } : {})}
                        monochrome
                        removeUnderline
                        onClick={() => (handleRowClick(data))}
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
                                active={popoverActive == data.id}
                                activator={<Button onClick={togglePopoverActive(data.id)} plain icon={HorizontalDotsMinor} />}
                                autofocusTarget="first-node"
                                onClose={togglePopoverActive(popoverActive)}
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

    function LinkCell(cellData, header) {
        return (
            <IndexTable.Cell key={header.title}>
                <div className='linkClass'>
                    <Link
                        dataPrimaryLink
                        monochrome
                        removeUnderline
                        onClick={() => (handleRowClick(data))}
                    >
                        {cellData}
                    </Link>
                </div>
            </IndexTable.Cell>
        )
    }

    function TextCell(header) {
        return (
            <Box maxWidth={header.maxWidth ? header.maxWidth : ''}>
                <TooltipText text={data[header.value]} tooltip={data[header.value]} />
            </Box>
        )
    }

    function ActionCell() {
        return (
            <IndexTable.Cell key={"actions"}>
                <HorizontalStack align='end'>
                    {
                        <Popover
                            active={popoverActive == data.id}
                            activator={<Button onClick={togglePopoverActive(data.id)} plain icon={HorizontalDotsMinor} />}
                            autofocusTarget="first-node"
                            onClose={togglePopoverActive(popoverActive)}
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

    function CollapsibleCell() {
        return (
            <IndexTable.Cell key={"collapsible"}>
                <HorizontalStack align='end'>
                    <div onClick={() => handleRowClick(data)} style={{cursor: 'pointer'}}>
                        <Icon source={collapsibleActive === data?.name ? ChevronUpMinor : ChevronDownMinor} />
                    </div>
                </HorizontalStack>
            </IndexTable.Cell>
        )
    }

    function getHeader(header){
        let type = header?.type;

        switch(type){

            case CellType.ACTION : 
                return hasRowActions ? ActionCell() : <></>;
            case CellType.COLLAPSIBLE :
                return CollapsibleCell();
            case CellType.TEXT :
                return header.value ? LinkCell(TextCell(header), header) : <></>;
            default :
                return header.value ? LinkCell(data[header.value], header) : <></>;
        }
    }

    function NewCell(){
        return(
            <>
                {headings.map((header) =>{
                    return getHeader(header);
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
                {...props.newRow ? {status: (index % 2) ? "subdued" : ''} : {}}
                {...props.notHighlightOnselected ? {} : {selected: selectedResources.includes(data?.id)}}
            >
                {props?.newRow ? <NewCell /> :<OldCell/>}   
            </IndexTable.Row>
            
            {collapsibleActive === data?.name ? data.collapsibleRow : null}
            
        </>
    )

}

export {GithubRow, CellType};