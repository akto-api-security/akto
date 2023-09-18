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
import { useState, useCallback, useEffect} from 'react';
import './row.css'
import GithubCell from '../cells/GithubCell';
import func from "@/util/func"

function GithubRow(props) {

    const {dataObj, getNextUrl, isRowClickable, selectedResources, index, headers, hasRowActions, getActions, onRowClick, getStatus, selectedIndex, setSelectedIndex } = props;
    const navigate = useNavigate();
    const [popoverActive, setPopoverActive] = useState(-1);
    const [data, setData] = useState(dataObj);

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
        if(data.nextUrl || getNextUrl){
            navigate(data?.nextUrl) || (getNextUrl && navigate(await getNextUrl(data.id), {replace:true}));
        }
    }

    function handleRowClick(data){
        setSelectedIndex(index)
        if(onRowClick){
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
    }, [dataObj])

    return (
        <IndexTable.Row
            id={data.id}
            key={data.id}
            selected={selectedResources.includes(data.id) || selectedIndex === index}
            position={index}
        >
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
        </IndexTable.Row>
    )

}

export default GithubRow;