import {
    ChoiceList
} from '@shopify/polaris';

import { useState, useCallback } from 'react';

function CustomChoiceList(props) {
    const [filterStatus, setFilterStatus] = useState(props.appliedFilters);
    const handleFilterStatusChange = useCallback(
        (value) => {
            setFilterStatus(value)
            props.selectedFilters(value);
        },
        [],
    );
    let filter = props.filter
    return (<ChoiceList
        title={filter.title}
        titleHidden
        choices={filter.choices}
        selected={filterStatus || []}
        onChange={handleFilterStatusChange}
        allowMultiple
    />)

}

export default CustomChoiceList