import { HorizontalGrid, VerticalStack } from '@shopify/polaris';
import React from 'react'

function GridRows(props) {

    const {columns , items, CardComponent, buttonText, onButtonClick, changedColumns} = props;
    let usedColumns = changedColumns && changedColumns > 0 ? changedColumns : columns

    const rows = Math.ceil((items.length)/usedColumns)

    return (
        <VerticalStack gap="5">
            {Array.from({length: rows}).map((_,index)=>(
                <HorizontalGrid columns={columns} gap="5" key={(index + 1) * 1000}>
                    {Array.from({ length: usedColumns }).map((_, col) => {
                        const itemIndex = index * usedColumns + col;
                        const item = items[itemIndex];
                        if (item) {
                            return <CardComponent cardObj={item} buttonText={buttonText} key={itemIndex} onButtonClick={onButtonClick}/>;
                        }
                    })}
                </HorizontalGrid>
            ))}
        </VerticalStack>
    )
}

export default GridRows