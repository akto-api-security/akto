import React, { useState, useReducer, useEffect } from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { Box, DescriptionList, Form, HorizontalGrid, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import Dropdown from '../../components/layouts/Dropdown'
import PersistStore from '../../../main/PersistStore'
import GithubServerTable from '../../components/tables/GithubServerTable'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import func from '@/util/func'
import { produce } from "immer"
import values from "@/util/values";

const rolesOptions = [
    {
        label: 'Admin',
        value: 'ADMIN',
    },
    {
        label: 'Security Engineer',
        value: 'MEMBER',
    },
    {
        label: 'Developer',
        value: 'DEVELOPER',
    },
    {
        label: 'Guest',
        value: 'GUEST',
    }
]

const resourceName = {
    singular: 'activity',
    plural: 'activities',
};

const sortOptions = [
    { label: 'Discovered timestamp', value: 'timestamp asc', directionLabel: 'Latest', sortKey: 'timestamp', columnIndex: 4 },
    { label: 'Discovered timestamp', value: 'timestamp desc', directionLabel: 'Earliest', sortKey: 'timestamp', columnIndex: 4 },
];

const filterOptions = [
    {
        key: 'orgName',
        label: 'Organization name',
        title: 'Organization name',
        choices: [],
    },
    {
        key: 'accountId',
        label: 'Account ID',
        title: 'Account ID',
        choices: [],
    },
    {
        key: 'type',
        label: 'Activity type',
        title: 'Activity type',
        choices: [],
    },
]

const headers = [
    {
        title: "Organization name",
        text: "Organization name",
        value: "orgName",
    },
    {
        title: "Activity type",
        text: "Activity type",
        value: "type",
    },
    {
        title: "Description",
        text: "Description",
        value: "prettifiedDescription",
    },
    {
        title: "Detection time",
        text: "Detection time",
        value: "timestamp",
        sortActive: true,
    },
    {
        title: "Account ID",
        text: "Account ID",
        value: "accountId",
    },
]

const data = [
    {
        orgName: "Acme Corp",
        type: "Unauthorized Access",
        prettifiedDescription: "Suspicious login attempt detected",
        timestamp: "2025-02-09T12:34:56Z",
        accountId: "ACC12345",
    },
    {
        orgName: "Beta Ltd.",
        type: "Data Exfiltration",
        prettifiedDescription: "Large data transfer to an unknown IP",
        timestamp: "2025-02-09T14:21:30Z",
        accountId: "ACC67890",
    },
    {
        orgName: "Gamma Inc.",
        type: "Privilege Escalation",
        prettifiedDescription: "Admin privileges granted to an unverified user",
        timestamp: "2025-02-09T16:10:05Z",
        accountId: "ACC11223",
    },
    {
        orgName: "Delta Systems",
        type: "Malware Execution",
        prettifiedDescription: "Suspicious script execution detected",
        timestamp: "2025-02-09T17:45:20Z",
        accountId: "ACC33445",
    },
    {
        orgName: "Epsilon Tech",
        type: "Phishing Attempt",
        prettifiedDescription: "Multiple login failures detected from external sources",
        timestamp: "2025-02-09T19:12:40Z",
        accountId: "ACC55667",
    },
    {
        orgName: "Zeta Solutions",
        type: "Brute Force Attack",
        prettifiedDescription: "Excessive login attempts detected",
        timestamp: "2025-02-09T20:50:12Z",
        accountId: "ACC77889",
    },
    {
        orgName: "Theta Enterprises",
        type: "Insider Threat",
        prettifiedDescription: "Suspicious internal data access pattern observed",
        timestamp: "2025-02-09T22:33:55Z",
        accountId: "ACC99001",
    }
];


function InternalDashboard() {

    const [accountId, setAccountId] = useState()
    const [selectedRole, setSelectedRole] = useState('MEMBER')
    const [orgName, setOrgName] = useState('')
    const [loading, setLoading] = useState(false)
    const collectionsMap = PersistStore.getState().collectionsMap;

    const [accountInfo, setAccountInfo] = useState({
        "org-name": '-',
        "hybrid-testing": false,
        "hybrid-runtime": false,
        "auth mechanism type": 'HARDCODED'
    })

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[3]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    const goToAccount = (
        <LegacyCard primaryFooterAction={{content: 'Go to Account', onAction: () => {}} } title="Switch Account" key={"change-account"}>
            <LegacyCard.Section>
                <HorizontalGrid gap={"4"} columns={2}>
                    <TextField value={accountId} onChange={setAccountId} type="number" label="AccountId" placeholder='Enter the account Id' />
                    <Dropdown label="Select your role" menuItems={rolesOptions} id={"roles-dropdown"} selected={setSelectedRole} initial={selectedRole} />    
                    
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const knowAccountInfo = (
        <LegacyCard title={"Get basic account info"} key={"account-info"}>
            <LegacyCard.Section>
                <VerticalStack gap={"4"}> 
                    <Form onSubmit={() => {}}>
                        <TextField onChange={setOrgName} value={orgName} label="Enter organization name or account id or orgId."/>
                    </Form>

                    <DescriptionList
                        items={Object.keys(accountInfo).map((x) => {
                            return {
                                term: x.toUpperCase(),
                                description: accountInfo[x].toString()
                            }
                        })}
                    />
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
        
    )

    const fetchData = async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
        return {value: data, total: 7}
    }

    function disambiguateLabel(key, value) {
        switch (key) {
          case "apiCollectionId":
            return func.convertToDisambiguateLabelObj(value, collectionsMap , 2);
          default:
            return func.convertToDisambiguateLabelObj(value, null, 2);
        }
      }
    
    const key = startTimestamp + endTimestamp;

    const summaryInfoTable = (
        <GithubServerTable
            key={key}
            pageLimit={20}
            headers={headers}
            resourceName={resourceName}
            sortOptions={sortOptions}
            disambiguateLabel={disambiguateLabel}
            loading={loading}
            fetchData={fetchData}
            filters={filterOptions}
            selectable={false}
            hasRowActions={true}
            getActions={() => []}
            hideQueryField={true}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
        />
    )

    const componentsArr = [goToAccount, knowAccountInfo, summaryInfoTable]

    return (
        <Box padding="8">
            <PageWithMultipleCards
                title={
                    <Text variant='headingLg'>
                        Internal Dashboard
                    </Text>
                }
                primaryAction={<DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>}
                components={componentsArr}
                isFirstPage={true}
                divider={true}
                backUrl={"/dashboard/observe/inventory"}
            />
        </Box>
    )
}

export default InternalDashboard