<template>
    <div class="issue-box" :class="getSeverityClass(severity)">
        <div class="issue-title">{{ categoryName }}</div>
        <div class="issue-description">{{ categoryDescription }}</div>
        <div class="display-flex">
            <span>
                <v-icon class="margin-left-24">$fas_cog</v-icon>
                <span class="issue-method">{{ method }}</span>
                <span class="issue-endpoint">{{ endpoint }}</span>
            </span>
            <span class="collection-span-css">
                <v-icon>$far_folder-open</v-icon>
                <span class="issue-collection">{{ collectionName }}</span>
                <v-icon>$far_clock</v-icon>
                <span class="issue-time">{{ getCreationTime(creationTime) }}</span>
            </span>
        </div>
    </div>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "IssueBox",
    props: {

        method: obj.strR,
        endpoint: obj.strR,
        creationTime: obj.numR,
        severity: obj.strR,
        collectionName: obj.strR,
        categoryName: obj.strR,
        categoryDescription: obj.strR,
        testType: obj.strR,
    },
    methods: {
        getCreationTime(creationTime) {
            let epoch = Date.now();
            let difference = epoch / 1000 - creationTime;
            let numberOfDays = difference / (60 * 60 * 24)
            if (numberOfDays < 31) {
                return parseInt(numberOfDays) + ' d';
            } else if (numberOfDays < 366) {
                return parseInt(numberOfDays / 31) + ' m' + parseInt(numberOfDays % 31) + ' d';
            } else {
                return parseInt(numberOfDays / 365) + ' y' + parseInt((numberOfDays % 365) / 31) + ' m'
                    + parseInt((numberOfDays % 365) % 31) + ' d';
            }
        },
        getSeverityClass(severity) {
            return {
                'severity-high' : severity === "HIGH",
                'severity-medium' : severity === "MEDIUM",
                'severity-low' : severity === "LOW"
            }
        }
    }
}

</script>

<style scoped >
.collection-span-css {
    float: none;
    align-self: right;
}

.margin-left-24 {
    margin-left: 24px;
}

.issue-collection {
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 500;
    font-size: 14px;
    align-items: right;
    color: #101828;
}

.issue-time {
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 500;
    font-size: 14px;
    margin-right: 24px;
    align-items: right;
    color: #101828;
}

.issue-endpoint {
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    /* identical to box height */
    align-items: left;
    color: #101828;


    /* Inside auto layout */

}

.issue-method {
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    /* identical to box height */
    margin-bottom: 24px;
    align-items: left;
    margin-right: 10px;
    color: #5C04D5;


}

.display-flex {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    margin-bottom: 24px;
}

.issue-title {
    font-family: 'Poppins';
    font-style: normal;
    font-weight: 500;
    font-size: 24px;
    /* identical to box height */
    text-align: left;
    margin-top: 24px;
    margin-left: 24px;
    color: #101828;
}

.issue-description {

    font-family: 'Poppins';
    font-style: normal;
    font-weight: 400;
    font-size: 16px;
    line-height: 24px;
    margin-top: 4px;
    margin-left: 24px;
    margin-bottom: 20px;
    color: #5B5B5B;
}

.issue-box {
    margin-left: 32px;
    margin-right: 32px;
    margin-top: 16px;
    margin-bottom: 16px;
    height: fit-content;
    background: #FCFCFD;
    border: 1px solid #D6D3DA;
    border-radius: 6px;
}

.severity-high {
    border-left: 6px solid #FF1717;
}

.severity-medium {
    border-left: 6px solid #FF8717;
}

.severity-low {
    border-left: 6px solid #1790FF;
}

</style>