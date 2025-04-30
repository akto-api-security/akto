import React, { useState, useCallback, useMemo } from 'react'
import useTable from '../../tables/TableContext'
import transform from '../../../pages/observe/transform';
import PrettifyDisplayName from "./PrettifyDisplayName"
import { Badge, DataTable } from '@shopify/polaris';
import func from '@/util/func';
import { useNavigate } from 'react-router-dom';
import TableStore from '../../tables/TableStore';

function PrettifyChildren({ data, headers }) {
    const [refresh, setRefresh] = useState(false);
    const navigate = useNavigate();
    const { openedRows, selectItems, modifyOpenedLevels } = useTable();

    const handleRowClick = useCallback((isTerminal, level, apiCollectionId,e) => {
        if(e.target.type === 'checkbox'){
            return ;
        }
        if (isTerminal) {
            navigate("/dashboard/observe/inventory/" + apiCollectionId);
        } else {
            setRefresh(!refresh); // Trigger a refresh
            let newItems = [];
            if (openedRows.includes(level)) {
                newItems = openedRows.filter(x => x !== level);
            } else {
                newItems = [...new Set([...openedRows, level])];
            }
            TableStore.getState().setOpenedLevels(newItems);
            modifyOpenedLevels(newItems);
        }
    }, [openedRows, navigate, modifyOpenedLevels, refresh]);

    const isLevelVisible = useCallback((level) => {
        const inputSegments = level.split("#");
        const currentDepth = inputSegments.length - 1;

        if (currentDepth < 2) {
            return true;
        }
        if (openedRows.length === 0) {
            return false;
        }

        for (let i = 0; i < openedRows.length; i++) {
            const arrayString = openedRows[i];
            const arraySegments = arrayString.split('#');

            if (arraySegments.length === inputSegments.length || arraySegments.length === inputSegments.length - 1) {
                let match = true;
                for (let j = 0; j < arraySegments.length; j++) {
                    if (arraySegments[j] !== inputSegments[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
        }
        return false;
    }, [openedRows]);

    const traverseAndMakeChildrenData = useCallback((childrenNodes, headers) => {
        let newRows = [];
        childrenNodes && childrenNodes.length > 0 && childrenNodes.forEach((c) => {
            let ids = c.hasOwnProperty('apiCollectionIds') ? c['apiCollectionIds'] : [c.id];
            let collectionObj = transform.convertToPrettifyData(c);
            collectionObj.urlsCount = c.urlsCount;
            collectionObj.envTypeComp = c.envType ? <Badge size="small" status="info">{c.envType}</Badge> : null;

            let isChildOpen = isLevelVisible(c.level);
            collectionObj.displayNameComp = 
                <PrettifyDisplayName 
                    key={c.level}
                    name={c.displayName} 
                    level={c.level} 
                    isTerminal={c.isTerminal} 
                    selectItems={selectItems} 
                    collectionIds={ids} 
                    isOpen={openedRows.includes(c.level)}
                />;
            
            let tempRow = [<div style={{width: '5px'}} key={c.level}/>];
            headers.forEach((x) => {
                const width = x?.boxWidth || "200px"
                tempRow.push(
                    <div 
                        key={`${c.level}-${x.value}`} 
                        style={{ cursor: 'pointer', width: width }} 
                        onClick={(e) => handleRowClick(c.isTerminal, c?.level, c?.id, e)}
                    >
                        {collectionObj[x.value]}
                    </div>
                );
            });

            if (isChildOpen) {
                newRows.push(tempRow);
                if (!c?.isTerminal) {
                    newRows = [...newRows, ...traverseAndMakeChildrenData(c?.children, headers)];
                }
            }
        });
        return newRows;
    }, [isLevelVisible]);

    const dataRows = useMemo(() => {
        return traverseAndMakeChildrenData(data, headers);
    }, [refresh]);

    return (
        <td colSpan={8} style={{ padding: '0px !important' }} className="control-row">
            <DataTable
                rows={dataRows}
                hasZebraStripingOnData
                headings={[]}
                columnContentTypes={['text', 'text', 'text', 'text', 'text', 'text', 'text', 'text']}
            />
        </td>
    );
}

export default PrettifyChildren;
