import {
    IndexTable,
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    Button, 
    Popover, 
    ActionList,
    Link
} from '@shopify/polaris';
import {
    HorizontalDotsMinor
} from '@shopify/polaris-icons';
import { useNavigate } from "react-router-dom";
import { useState, useCallback} from 'react';
import './row.css'
import GithubCell from '../cells/GithubCell';

function GithubRow(props) {
    const navigate = useNavigate();
    const [popoverActive, setPopoverActive] = useState(-1);
    const [data, setData] = useState(props.data);
    const togglePopoverActive = (index) =>useCallback(
        () => setPopoverActive(index),
        [],
    );
    async function nextPage(data){
        navigate(data?.nextUrl, {state:{data:data}}) || (props.getNextUrl && navigate(await props.getNextUrl(data.id), {state:{data:data}}));
    }

    const [rowClickable, setRowClickable] = useState(props.rowClickable || false)

    return (
        <IndexTable.Row
            id={data.id}
            key={data.id}
            selected={props.selectedResources.includes(data.id)}
            position={props.index}
        >
            <IndexTable.Cell>
                <div className='linkClass'>
                    <Link
                        {...(rowClickable ? { dataPrimaryLink: rowClickable } : {})}
                        monochrome
                        removeUnderline
                        onClick={() => (nextPage(data))}
                    >
                        <GithubCell
                            headers={props.headers}
                            data={data}
                        />
                    </Link>
                </div>
            </IndexTable.Cell>
            {props?.headers?.filter((header) => {
                return header.itemCell == 2
            }).filter((header) => {
                return data[header.value] != undefined
            }).map((header) => {
                return (
                    <IndexTable.Cell key={header.text}>
                        <VerticalStack>
                            <Text>
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
            {props.hasRowActions &&
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
                                    sections={props.getActions(data)}
                                />
                            </Popover>
                        }
                    </HorizontalStack>
                </IndexTable.Cell>
            }
        </IndexTable.Row>
    )

}

export default GithubRow;