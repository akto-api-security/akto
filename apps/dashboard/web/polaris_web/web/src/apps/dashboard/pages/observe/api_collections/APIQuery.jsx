import api from "./api"

const selectOptions1 = [
    {
        label: "Endpoint",
        value: "ENDPOINT"
    },
    {
        label: "Method",
        value: "METHOD"
    },
    {
        label: "Timestamp",
        value: "TIMESTAMP"
    },
    {
        label: "Parameter",
        value: "PARAMETER"
    },
]
const selectOptions = [
    {
        label: 'contains',
        value: 'CONTAINS'
    },
    {
        label: 'belongs to',
        value: 'BELONGS_TO',
        operators: [
            {
                label: 'OR',
                value: 'OR',
            }
        ],
        type: "MAP"
    },
    {
        label: 'does not belongs to',
        value: 'NOT_BELONGS_TO',
        operators: [{
            label: 'AND',
            value: 'AND',
        }],
        type: "MAP"
    }
]

function APIQuery() {

}

export default APIQuery