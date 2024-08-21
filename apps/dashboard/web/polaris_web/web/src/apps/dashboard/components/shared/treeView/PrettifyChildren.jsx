import React, { useEffect, useState } from 'react'
import useTable from '../../tables/TableContext'
import transform from '../../../pages/observe/transform';
import PrettifyDisplayName from "./PrettifyDisplayName"
import { Badge, DataTable } from '@shopify/polaris';
import func from '@/util/func';
import { useNavigate } from 'react-router-dom';
import TableStore from '../../tables/TableStore';

function PrettifyChildren({data, headers}) {
    const [dataRows, setDataRows] = useState([])
    const navigate = useNavigate();

    const { openedRows, selectItems, modifyOpenedLevels} = useTable();

    const handleRowClick = (isTerminal, level, apiCollectionId) => {
        if(isTerminal){
            navigate("/dashboard/observe/inventory/" + apiCollectionId)
        }else{
            let newItems = []
            if(openedRows.includes(level)){
                newItems = openedRows.filter((x) => x !== level)
            }else{
                newItems = [...new Set([...openedRows, ...[level]])]
            }
            TableStore.getState().setOpenedLevels(newItems)
            modifyOpenedLevels(newItems)
        }
    }

    const traverseAndMakeChildrenData = (childrenNodes, headers) =>{
        childrenNodes.forEach((c) => {
            let ids = []
            if(c.hasOwnProperty('apiCollectionIds')){
                ids = c['apiCollectionIds']
            }else{
                ids = [c.id]
            }
            let collectionObj = transform.convertToPrettifyData(c)
            collectionObj.endpoints = c.endpoints
            collectionObj.envTypeComp = c.envType ? <Badge size="small" status="info">{func.toSentenceCase(c.envType)}</Badge> : null
            let isChildOpen = false;
            collectionObj.displayNameComp = 
                <PrettifyDisplayName name={c.displayName} 
                    level={c.level} 
                    isTerminal={c.isTerminal} 
                    isOpen={isChildOpen} 
                    selectItems={selectItems} 
                    collectionIds={ids} 
                />
            let tempRow = [<div/>]
            headers.forEach((x) => {
                tempRow.push(<div style={{cursor: 'pointer'}} onClick={() => handleRowClick(c.isTerminal, c?.level, c?.id)}>{collectionObj[x.value]}</div>)
            })
            console.log("level", c.level)
            

            setDataRows((prev) => {
                let newRow = [...prev];
                newRow.push(tempRow)
                return newRow
            })
            if(c?.isTerminal === false){
                traverseAndMakeChildrenData(c?.children, headers, selectItems)
            }
        })
    }
    useEffect(() => {
        traverseAndMakeChildrenData(data, headers, selectItems)
    },[])
    
    return (
        <td colSpan={10} style={{padding: '0px !important'}} className="control-row">
            <DataTable
                rows={dataRows}
                hasZebraStripingOnData
                headings={[]}
                columnContentTypes={['text', 'numeric', 'text', 'text', 'text', 'text', 'text', 'text', 'text']}
                truncate
                
            />
        </td>
    )
}

export default PrettifyChildren