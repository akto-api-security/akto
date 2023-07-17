import {
    IndexTable,
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box,
    Button, 
    Popover, 
    ActionList,
    Link
} from '@shopify/polaris';
import {
    HorizontalDotsMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";
import { useState, useCallback } from 'react';
import './row.css'
import GithubCell from '../cells/GithubCell';

function GithubRow(props) {
    const navigate = useNavigate();
    const [popoverActive, setPopoverActive] = useState(-1);
    const togglePopoverActive = (index) =>useCallback(
        () => setPopoverActive(index),
        [],
    );
    
    function nextPage(data){
        navigate(data?.nextUrl)
    }

    const [rowClickable, setRowClickable] = useState(props.page==2)

    return (
        <IndexTable.Row
            id={props.data.hexId}
            key={props.data.hexId}
            selected={props.selectedResources.includes(props.data.hexId)}
            position={props.index}
        >
                {/* <div style={{ padding: '12px 16px', width: '100%' }}> */}
                {/* <HorizontalStack align='space-between'> */}
            <IndexTable.Cell>
                {/* <div onClick={() => (props.nextPage && props.nextPage=='singleTestRunPage' ? navigateToTest(props.data) : {})} style={{cursor: 'pointer'}}> */}
                <div className='linkClass'>
                <Link
                    {...(rowClickable ? {dataPrimaryLink: rowClickable} : {})}
                    monochrome
                    removeUnderline
                    onClick={() => (nextPage(props.data))}
                    // onClick={() => console.log("something")}
                >
                    <GithubCell
                        headers = {props.headers}
                        data = {props.data}
                    />
                        </Link>
                        </div>
                    {/* </div> */}
                        </IndexTable.Cell>
            {
                props?.headers?.filter((header) => {
                    return header.itemCell == 2
                }).map((header) => {
                    return (
                        <IndexTable.Cell key={header.text}>
                            <VerticalStack>
                                <Text>
                                    {header.text}
                                </Text>
                                <HorizontalStack>
                                <Badge key={header.text}>
                                    {props.data[header.value]}
                                </Badge>
                                </HorizontalStack>
                            </VerticalStack>
                        </IndexTable.Cell>
                    )
                })
            }
            <IndexTable.Cell >
                <HorizontalStack align='end'>
                {
                    props.hasRowActions &&
                    <Popover
                        active={popoverActive == props.data.hexId}
                        activator={<Button onClick={togglePopoverActive(props.data.hexId)} plain icon={HorizontalDotsMinor} />}
                        autofocusTarget="first-node"
                        onClose={togglePopoverActive(popoverActive)}
                    >
                        <ActionList
                            actionRole="menuitem"
                            sections={props.getActions(props.data)}
                        />
                    </Popover>
                }
                </HorizontalStack>
            </IndexTable.Cell>
        </IndexTable.Row>
    )

}

export default GithubRow;