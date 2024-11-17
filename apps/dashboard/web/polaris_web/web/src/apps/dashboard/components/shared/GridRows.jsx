import { InlineGrid, BlockStack } from '@shopify/polaris';
import React from 'react'

function GridRows(props) {

    const {columns , items, CardComponent, buttonText, onButtonClick, changedColumns} = props;
    let usedColumns = changedColumns && changedColumns > 0 ? changedColumns : columns

    const rows = Math.ceil((items?.length)/usedColumns)
    const widthChanged = Math.floor(((usedColumns * 100)/columns)) + '%'

    return (
        <div style={{width: widthChanged }}>
            <BlockStack gap="500">
                {Array.from({length: rows}).map((_,index)=>(
                    <InlineGrid
                        columns={{xs: 1, sm: Math.max(usedColumns - 2,1) , md: usedColumns - 1 , lg:usedColumns, xl: usedColumns}}
                        gap="500"
                        key={(index + 1) * 1000}>
                        {Array.from({ length: usedColumns }).map((_, col) => {
                            const itemIndex = index * usedColumns + col;
                            const item = items[itemIndex];
                            if (item) {
                                return <CardComponent cardObj={item} buttonText={buttonText} key={itemIndex} onButtonClick={onButtonClick}/>;
                            }
                        })}
                    </InlineGrid>
                ))}
            </BlockStack>
        </div>
    );
}

export default GridRows