import { Checkbox, VerticalStack } from '@shopify/polaris'
import { useEffect, useState } from 'react'
import Dropdown from '../../../components/layouts/Dropdown'
import testingApi from '../../testing/api'

// Lets the user send import requests from one of their mini-testing modules,
// for targets that are only reachable from inside their network.
const MiniTestingSelector = ({ id, enabled, setEnabled, selected, setSelected }) => {
    const [options, setOptions] = useState([])

    useEffect(() => {
        testingApi.fetchMiniTestingServiceNames().then(({ miniTestingServiceNames }) => {
            setOptions((miniTestingServiceNames || []).map(name => ({ label: name, value: name })))
        }).catch(() => setOptions([]))
    }, [])

    if (options.length === 0) {
        return null
    }

    const handleToggle = (checked) => {
        setEnabled(checked)
        setSelected(checked ? options[0].value : '')
    }

    return (
        <VerticalStack gap="2">
            <Checkbox
                label="Send requests via mini-testing"
                checked={enabled}
                onChange={handleToggle}
                helpText="Use this when the endpoint is internal and not reachable from the dashboard. Requests are sent from the selected mini-testing module in your network."
            />
            {enabled && (
                <Dropdown
                    id={id}
                    label="Select testing module"
                    menuItems={options}
                    initial={selected}
                    selected={setSelected}
                />
            )}
        </VerticalStack>
    )
}

export default MiniTestingSelector
