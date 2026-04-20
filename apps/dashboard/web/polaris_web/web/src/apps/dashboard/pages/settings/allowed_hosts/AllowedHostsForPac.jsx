import React from 'react'
import PatternSettingsPage from '../components/PatternSettingsPage'
import settingRequests from '../api'

const resourceName = { singular: 'host', plural: 'hosts' }

async function fetchAllowedHosts() {
    const response = await settingRequests.fetchAdminSettings()
    return response?.accountSettings?.allowedHostsForPac
}

async function addAllowedHost(value) {
    return await settingRequests.addAllowedHostForPac(value)
}

async function deleteAllowedHost(value) {
    return await settingRequests.deleteProxyPattern(value, 'PAC')
}

function AllowedHostsForPac() {
    return (
        <PatternSettingsPage
            title="Allowed Hosts"
            cardTitle="Add Allowed Host"
            description="Add host patterns to ignore during traffic capture. Matching hosts will be excluded from TAC processing."
            inputLabel="Host"
            placeholder="e.g. internal.example.com"
            tableKey="allowed-hosts-table"
            resourceName={resourceName}
            onFetch={fetchAllowedHosts}
            onAdd={addAllowedHost}
            onDelete={deleteAllowedHost}
            patternKey="pattern"
        />
    )
}

export default AllowedHostsForPac
