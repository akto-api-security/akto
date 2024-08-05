import func from '@/util/func';
import { CellType } from '../../tables/rows/GithubRow'
import HeadingWithTooltip from '../HeadingWithTooltip'
import { ActionList, Popover, Text } from '@shopify/polaris'


const updateEnvType = (a,b) => {
    console.log(a);
}
const dummyData = [
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "Akto:shadow_apis",
        "envType": null,
        "hostName": null,
        "id": 1333333333,
        "matchDependencyWithOtherCollections": false,
        "name": "shadow_apis",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1714024952,
        "type": null,
        "urls": [],
        "urlsCount": 81,
        "userSetEnvType": [],
        "vxlanId": 1333333333,
        "endpoints": 81,
        "detected": "2 months ago",
        "nextUrl": "/dashboard/observe/inventory/1333333333",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "EMAIL",
            "SECRET",
            "TOKEN"
        ],
        "severityInfo": {},
        "detectedTimestamp": 1718344959,
        "riskScore": 0,
        "discovered": "Apr 25 2024",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [
            "EMAIL",
            "SECRET",
            "TOKEN"
        ],
        "lastTraffic": "2 months ago",
        "deactivatedRiskScore": 0,
        "activatedRiskScore": 10,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "EMAIL SECRET TOKEN"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "kore.kore-pentesting",
        "envType": null,
        "hostName": null,
        "id": 1719405066,
        "matchDependencyWithOtherCollections": false,
        "name": "kore-pentesting",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1719405066,
        "type": null,
        "urls": [],
        "urlsCount": 224,
        "userSetEnvType": [],
        "vxlanId": 1719405066,
        "endpoints": 224,
        "detected": "2 months ago",
        "nextUrl": "/dashboard/observe/inventory/1719405066",
        "testedEndpoints": 224,
        "sensitiveInRespTypes": [
            "TOKEN",
            "IP_ADDRESS",
            "JWT",
            "USERNAME",
            "SECRET",
            "EMAIL"
        ],
        "severityInfo": {
            "HIGH": 54
        },
        "detectedTimestamp": 1719758457,
        "riskScore": 2,
        "discovered": "2 months ago",
        "coverage": 224,
        "issuesArrVal": "HIGH: 54 ",
        "sensitiveSubTypes": [
            "TOKEN",
            "IP_ADDRESS",
            "JWT",
            "USERNAME",
            "SECRET",
            "EMAIL"
        ],
        "lastTraffic": "2 months ago",
        "deactivatedRiskScore": 2,
        "activatedRiskScore": 8,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "TOKEN IP_ADDRESS JWT USERNAME SECRET EMAIL"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "kore.kore-testing",
        "envType": null,
        "hostName": null,
        "id": 1719760732,
        "matchDependencyWithOtherCollections": false,
        "name": "kore-testing",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1719760732,
        "type": null,
        "urls": [],
        "urlsCount": 125,
        "userSetEnvType": [],
        "vxlanId": 1719760732,
        "endpoints": 125,
        "detected": "3 weeks ago",
        "nextUrl": "/dashboard/observe/inventory/1719760732",
        "testedEndpoints": 125,
        "sensitiveInRespTypes": [
            "EMAIL",
            "USERNAME",
            "JWT",
            "IP_ADDRESS",
            "SECRET",
            "TOKEN"
        ],
        "severityInfo": {
            "HIGH": 51
        },
        "detectedTimestamp": 1721150204,
        "riskScore": 4,
        "discovered": "2 months ago",
        "coverage": 125,
        "issuesArrVal": "HIGH: 51 ",
        "sensitiveSubTypes": [
            "EMAIL",
            "USERNAME",
            "JWT",
            "IP_ADDRESS",
            "SECRET",
            "TOKEN"
        ],
        "lastTraffic": "3 weeks ago",
        "deactivatedRiskScore": 4,
        "activatedRiskScore": 6,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "EMAIL USERNAME JWT IP_ADDRESS SECRET TOKEN"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "Default.Default",
        "envType": null,
        "hostName": null,
        "id": 0,
        "matchDependencyWithOtherCollections": false,
        "name": "Default",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": false,
        "startTs": 1685956748,
        "type": null,
        "urls": [],
        "urlsCount": 0,
        "userSetEnvType": [],
        "vxlanId": 0,
        "endpoints": 0,
        "detected": "Never",
        "nextUrl": "/dashboard/observe/inventory/0",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [],
        "severityInfo": {},
        "detectedTimestamp": 0,
        "riskScore": 0,
        "discovered": "Jun 5 2023",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [],
        "lastTraffic": "Never",
        "deactivatedRiskScore": 0,
        "activatedRiskScore": 10,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "-"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": true,
        "displayName": "Whatfix:Whatfix",
        "envType": null,
        "hostName": null,
        "id": 1716257797,
        "matchDependencyWithOtherCollections": false,
        "name": "Whatfix",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1716257797,
        "type": null,
        "urls": [],
        "urlsCount": 152,
        "userSetEnvType": [],
        "vxlanId": 1716257797,
        "endpoints": 152,
        "detected": "May 26 2024",
        "nextUrl": "/dashboard/observe/inventory/1716257797",
        "rowStatus": "critical",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "EMAIL",
            "IP_ADDRESS",
            "PHONE_NUMBER",
            "JWT",
            "TOKEN",
            "SECRET"
        ],
        "severityInfo": {},
        "detectedTimestamp": 1716675901,
        "riskScore": 1,
        "discovered": "May 21 2024",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [
            "EMAIL",
            "IP_ADDRESS",
            "PHONE_NUMBER",
            "JWT",
            "TOKEN",
            "SECRET"
        ],
        "lastTraffic": "May 26 2024",
        "deactivatedRiskScore": -9,
        "activatedRiskScore": -1,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "EMAIL IP_ADDRESS PHONE_NUMBER JWT TOKEN SECRET"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": true,
        "displayName": "Whatfix.Whatfix-profile-settings",
        "envType": null,
        "hostName": null,
        "id": 1717564646,
        "matchDependencyWithOtherCollections": false,
        "name": "Whatfix-profile-settings",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1717564646,
        "type": null,
        "urls": [],
        "urlsCount": 3,
        "userSetEnvType": [],
        "vxlanId": 1717564646,
        "endpoints": 3,
        "detected": "2 months ago",
        "nextUrl": "/dashboard/observe/inventory/1717564646",
        "rowStatus": "critical",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "TOKEN"
        ],
        "severityInfo": {
            "HIGH": 2
        },
        "detectedTimestamp": 1717573095,
        "riskScore": 2,
        "discovered": "2 months ago",
        "coverage": 0,
        "issuesArrVal": "HIGH: 2 ",
        "sensitiveSubTypes": [
            "TOKEN"
        ],
        "lastTraffic": "2 months ago",
        "deactivatedRiskScore": -8,
        "activatedRiskScore": -2,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "TOKEN"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "Akto.auth-endpoints",
        "envType": null,
        "hostName": null,
        "id": 1717483284,
        "matchDependencyWithOtherCollections": false,
        "name": "auth-endpoints",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1717483284,
        "type": null,
        "urls": [],
        "urlsCount": 2,
        "userSetEnvType": [],
        "vxlanId": 1717483284,
        "endpoints": 2,
        "detected": "2 months ago",
        "nextUrl": "/dashboard/observe/inventory/1717483284",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "EMAIL"
        ],
        "severityInfo": {},
        "detectedTimestamp": 1717484097,
        "riskScore": 0,
        "discovered": "2 months ago",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [
            "EMAIL"
        ],
        "lastTraffic": "2 months ago",
        "deactivatedRiskScore": 0,
        "activatedRiskScore": 10,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "EMAIL"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": true,
        "displayName": "Whatfix.Burp",
        "envType": null,
        "hostName": null,
        "id": 1713426091,
        "matchDependencyWithOtherCollections": false,
        "name": "Burp",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1713426091,
        "type": null,
        "urls": [],
        "urlsCount": 137,
        "userSetEnvType": [],
        "vxlanId": 1713426091,
        "endpoints": 137,
        "detected": "3 weeks ago",
        "nextUrl": "/dashboard/observe/inventory/1713426091",
        "rowStatus": "critical",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "EMAIL",
            "SECRET",
            "TOKEN"
        ],
        "severityInfo": {},
        "detectedTimestamp": 1720691640,
        "riskScore": 2,
        "discovered": "Apr 18 2024",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [
            "EMAIL",
            "SECRET",
            "TOKEN"
        ],
        "lastTraffic": "3 weeks ago",
        "deactivatedRiskScore": -8,
        "activatedRiskScore": -2,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "EMAIL SECRET TOKEN"
    },
    {
        "automated": false,
        "conditions": null,
        "deactivated": false,
        "displayName": "Default:juice_shop_demo",
        "envType": null,
        "hostName": null,
        "id": 1712830776,
        "matchDependencyWithOtherCollections": false,
        "name": "juice_shop_demo",
        "redact": false,
        "runDependencyAnalyser": false,
        "sampleCollectionsDropped": true,
        "startTs": 1712830776,
        "type": null,
        "urls": [],
        "urlsCount": 19,
        "userSetEnvType": [],
        "vxlanId": 1712830776,
        "endpoints": 19,
        "detected": "Mar 9 2023",
        "nextUrl": "/dashboard/observe/inventory/1712830776",
        "testedEndpoints": 0,
        "sensitiveInRespTypes": [
            "TOKEN",
            "EMAIL"
        ],
        "severityInfo": {},
        "detectedTimestamp": 1678373515,
        "riskScore": 0,
        "discovered": "Apr 11 2024",
        "coverage": 0,
        "issuesArrVal": "-",
        "sensitiveSubTypes": [
            "TOKEN",
            "EMAIL"
        ],
        "lastTraffic": "Mar 9 2023",
        "deactivatedRiskScore": 0,
        "activatedRiskScore": 10,
        "envTypeComp": null,
        "sensitiveSubTypesVal": "TOKEN EMAIL"
    },
]
const headers =  [
        {
            title: "Group name",
            text: "Group name",
            filterKey:"displayName",
            textValue: 'displayName',
            showFilter:true,
            type: CellType.COLLAPSIBLE
        },
        {
            title: "Total endpoints",
            text: "Total endpoints",
            value: "endpoints",
            isText: CellType.TEXT,
            sortActive: true,
            mergeType: (a,b) => {return a + b}
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of collection is maximum risk score of the endpoints inside this collection</Text>} title="Risk score" />,
            value: 'riskScoreComp',
            textValue: 'riskScore',
            numericValue: 'riskScore',
            text: 'Risk Score',
            sortActive: true,
            mergeType: (a,b) => {return Math.max(a,b)}
        },
        {   
            title: 'Test coverage',
            text: 'Test coverage', 
            value: 'coverage',
            isText: CellType.TEXT,
            tooltipContent: (<Text variant="bodySm">Percentage of endpoints tested successfully in the collection</Text>),
            mergeType: (a,b) => {return a + b}
        },
        {
            title: 'Issues', 
            text: 'Issues', 
            value: 'issuesArr',
            numericValue: 'severityInfo',
            textValue: 'issuesArrVal',
            tooltipContent: (<Text variant="bodySm">Severity and count of issues present in the collection</Text>),
            mergeType: (a,b) => {return {
                HIGH: ((a?.HIGH || 0) + (b?.HIGH || 0)),
                MEDIUM: ((a?.MEDIUM || 0) + (b?.MEDIUM || 0)),
                LOW: ((a?.LOW || 0) + (b?.LOW || 0)),
            }}
        },
        {   
            title: 'Sensitive data' , 
            text: 'Sensitive data' , 
            value: 'sensitiveSubTypes',
            textValue: 'sensitiveSubTypesVal',
            tooltipContent: (<Text variant="bodySm">Types of data type present in response of endpoint inside the collection</Text>),
            mergeType: (a,b)=> [...new Set([...a, ...b])]
        },
        {
            text: 'Collection type',
            title: 'Collection type',
            value: 'envTypeComp',
            filterKey: "userSetEnvType",
            showFilter: true,
            textValue: 'userSetEnvType',
            tooltipContent: (<Text variant="bodySm">Environment type for an API collection, Staging or Production </Text>),
            mergeType: (a,b)=> [...new Set([...a, ...b])]
        },
        {   
            title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint within collection was either discovered for the first time or seen again</Text>} title="Last traffic seen" />, 
            text: 'Last traffic seen', 
            value: 'lastTraffic',
            numericValue: 'detectedTimestamp',
            isText: CellType.TEXT,
            sortActive: true,
            mergeType: (a,b) => {return Math.max(a,b)}
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">Time when collection was created</Text>} title="Discovered" />,
            text: 'Discovered',
            value: 'discovered',
            isText: CellType.TEXT,
            sortActive: true,
            numericValue: 'startTs',
            mergeType: (a,b) => {return Math.max(a,b)}
        }
    ]

const dummyDataObj = {
    dummyData : dummyData,
    headers: headers,
    sortOptions : [
        { label: 'Name', value: 'displayName asc', directionLabel: 'A-Z', sortKey: 'displayName' },
        { label: 'Name', value: 'displayName desc', directionLabel: 'Z-A', sortKey: 'displayName' },
        { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'deactivatedRiskScore' },
        { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'activatedRiskScore' },
        { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
        { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore' , columnIndex: 3},
        { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 9 },
        { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs' , columnIndex: 9},
        { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'endpoints', columnIndex: 2 },
        { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'endpoints' , columnIndex: 2},
        { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detectedTimestamp', columnIndex: 8 },
        { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detectedTimestamp' , columnIndex: 8},
      ],
      resourceName : {
        singular: 'collection',
        plural: 'collections',
      },
      
    promotedBulkActions : (selectedResources) => {
        let actions = [
            {
                content: `Remove collection${func.addPlurality(selectedResources.length)}`,
            },
            {
                content: 'Export as CSV',
            }
        ];

        const toggleTypeContent = (
            <Popover
                activator={<div onClick={() => console.log("hey")}>Set ENV type</div>}
                onClose={() => console.log("hii")}
                active={false}
                autofocusTarget="first-node"
            >
                <Popover.Pane>
                    <ActionList
                        actionRole="menuitem"
                        items={[
                            {content: 'Staging', onAction: () => updateEnvType(selectedResources, "STAGING")},
                            {content: 'Production', onAction: () => updateEnvType(selectedResources, "PRODUCTION")},
                            {content: 'Reset', onAction: () => updateEnvType(selectedResources, null)},
                        ]}
                    />
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent
        }

        return [...actions, toggleEnvType];
    },

}

export default dummyDataObj
