import {
    IndexTable,
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    ButtonGroup,
    Icon,
    Box,
    Link,
    Button, Popover, ActionList
} from '@shopify/polaris';
import {
    HorizontalDotsMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";

import { useState, useCallback } from 'react';

function GithubRow(props) {
    const navigate = useNavigate();
    const [popoverActive, setPopoverActive] = useState(-1);
    const togglePopoverActive = (index) =>useCallback(
        () => setPopoverActive(index),
        [],
    );

    function getStatus(item) {
        let confidence = item.confidence.toUpperCase();
        switch (confidence) {
            case 'HIGH': return 'critical';
            case 'MEDIUM': return 'warning';
            case 'LOW': return 'neutral';
        }
    }

    function navigateToTest(hexId){
        console.log(hexId);
        navigate("/dashboard/testing/"+hexId)
    }

    return (
        <IndexTable.Row
            id={props.data.hexId}
            key={props.data.hexId}
            selected={props.selectedResources.includes(props.data.hexId)}
            position={props.index}
            // onClick={()=>{console.log("something")}}
        // onClick={fun}
        >
            <IndexTable.Cell
            onClick={()=>{console.log("something")}}>
                {/* <div style={{ padding: '12px 16px', width: '100%' }}> */}
                <HorizontalStack align='space-between'>
                    {/* <div> */}
                    {/* <Link url={"testing/" + props.data.hexId} monochrome={true} removeUnderline={true} > */}
                    <div onClick={() => (props.nextPage && props.nextPage=='singleTestRunPage' ? navigateToTest(props.data.hexId) : {})} style={{cursor: 'pointer'}}>
                    <HorizontalStack gap="1">
                        {/* <VerticalStack align="start" inlineAlign="start" gap="1"> */}
                        {/* <HorizontalStack gap="2" align='center'> */}
                        <Box padding="1">
                            {
                                props?.headers[0]?.icon &&
                                <Icon source={props.data[props?.headers[0]?.icon['value']]} color="primary" />
                            }
                        </Box>
                        {/* </HorizontalStack> */}
                        {/* </VerticalStack> */}
                        <VerticalStack gap="2">
                            <HorizontalStack gap="2" align='start'>
                                <Text as="span" variant="headingMd">
                                    {
                                        props?.headers[0]?.name &&
                                        props.data[props?.headers[0]?.name['value']]
                                    }
                                </Text>
                                {
                                    props?.headers[1]?.severityList &&
                                        props.data[props?.headers[1]?.severityList['value']] ? props.data[props?.headers[1]?.severityList['value']].map((item) =>
                                            <Badge key={item.confidence} status={getStatus(item)}>{item.count ? item.count: ""} {item.confidence}</Badge>) :
                                        []}
                            </HorizontalStack>
                            {/* <div style={{width: 'fit-content'}}> */}
                            <HorizontalStack gap='2' align="start" >
                                {/* {
            props?.headers[2]?.icon &&
            <Icon source={props?.headers[2]?.icon['value']} color="primary" />
          } */}
                                {
                                    props?.headers[2]?.details &&
                                    props?.headers[2]?.details.map((detail) => {
                                        return (
                                            <ButtonGroup key={detail.value}>
                                                <Icon source={detail.icon} color="subdued" />
                                                <Text as="span" variant="bodySm" color="subdued">
                                                    {props.data[detail.value]}
                                                </Text>
                                            </ButtonGroup>
                                        )
                                    })
                                }
                            </HorizontalStack>
                            {/* </div> */}
                        </VerticalStack>
                    </HorizontalStack>
                    {/* </Link> */}
                    </div>
                    {/* </div> */}
                    <VerticalStack align="center">
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
                    </VerticalStack>
                </HorizontalStack>

                {/* ) */}

                {/* }) */}

                {/* } */}

                {/* </div> */}
            </IndexTable.Cell>
        </IndexTable.Row>
    )

}

export default GithubRow;