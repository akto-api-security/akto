<template>
    <div class="issue-box" :style='{ "border-left": "6px solid " + getSeverityColor(severity) }'>
        <div class="display-flex-heading">
            <div class="issue-title">
                <span v-if="!(filterStatus.length === 0 || filterStatus.length === 1 && filterStatus[0] === 'FIXED')">
                    <input type="checkbox" v-model="issueChecked"/>
                </span>
                <v-btn @click="openDialogBox">{{ categoryName }}</v-btn>
                <v-chip outlined dark class="severity-chip" :color="getSeverityColor(severity)">
                    <v-icon size="6">$fas_circle</v-icon>
                    {{ getSeverityName(severity) }}
                </v-chip>
            </div>
            <div class="mr-6 mt-4">
                <div v-if="issueStatus !== 'FIXED'">
                    <v-menu offset-y>
                        <template v-slot:activator="{ on, attrs }">
                            <v-btn v-bind="attrs" v-on="on" primary class="white--text " color="var(--v-themeColor-base)">
                                <span>{{label(issueStatus, ignoreReason)}}</span>
                                <v-icon>$fas_angle-down</v-icon>
                            </v-btn>
                        </template>
                        <v-list>
                            <v-list-item v-for="(item, index) in items(issueStatus, ignoreReason)" :key="index" link
                                @click="updateStatus(issueId, item)">
                                <span>
                                    {{ item }}
                                </span>
                            </v-list-item>
                        </v-list>
                    </v-menu>
                </div>
            </div>
        </div>
        <div class="issue-description">{{ categoryDescription }}</div>
        <div class="display-flex-url">
            <span style="text-decoration-line:underline">
                <span class="issue-method margin-left-24">{{ method }}</span>
                <span class="issue-endpoint"><a :href="getEndpointLink(issueId)" target="_blank">{{ endpoint
                }}</a></span>
            </span>
            <span class="collection-span-css">
                <v-icon>$far_folder-open</v-icon>
                <span class="issue-collection"><a :href="getCollectionLink(issueId)" target="_blank">{{ collectionName
                }}</a></span>
                <v-icon>$far_clock</v-icon>
                <span class="issue-time">{{ getCreationTime(creationTime) }}</span>
            </span>
        </div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import func from '@/util/func'

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
        issueId: obj.objR,
        issueStatus: obj.strR,
        ignoreReason: obj.strN,
        issueChecked: obj.boolR,
        filterStatus: obj.arrR
    },
    data() {
        const ignoreReasons = [
            "False positive",
            "Acceptable risk",
            "No time to fix"
        ]
        const reOpen = "Reopen"
        return {
            ignoreReasons,
            reOpen,
            selectedValue: "",
        }
    },
    watch: {
        issueChecked (newValue) {
            this.$emit('clickedIssueCheckbox',{'issueId': this.issueId, 'checked': newValue})
        }
    },
    methods: {
        getCreationTime: func.getCreationTime,
        openDialogBox() {
            this.$emit('openDialogBox',{'issueId': this.issueId})
        },
        label(issueStatus, ignoreReason) {
            if (issueStatus === "IGNORED") {
                return ignoreReason
            }
            return "Ignore"
        },
        items(issueStatus, ignoreReason) {
            if (issueStatus === "IGNORED") {
                let itemsArray = []
                this.ignoreReasons.forEach((reason) => {
                    if (ignoreReason !== reason) {
                        itemsArray.push(reason)
                    }
                })
                itemsArray.push(this.reOpen)
                return itemsArray
            }
            return this.ignoreReasons
        },
        async updateStatus(issueId, selectedValue) {
            if (selectedValue !== this.reOpen) {//Ignore case
                await this.$store.dispatch("issues/updateIssueStatus", { selectedIssueId: issueId, selectedStatus: "IGNORED", ignoreReason: selectedValue });
            } else {//Reopen case
                await this.$store.dispatch("issues/updateIssueStatus", { selectedIssueId: issueId, selectedStatus: "OPEN", ignoreReason: selectedValue });
            }
            //If already selected case
            if (this.issueChecked) {//if checked, mark unchecked
                this.$emit('clickedIssueCheckbox',{'issueId': issueId, 'checked': false})
            }
            this.$store.dispatch('issues/loadIssues')
        },
        getCollectionLink(issueId) {
            return '/dashboard/observe/inventory/' + issueId.apiInfoKey.apiCollectionId;
        },
        getEndpointLink(issueId) {
            return '/dashboard/observe/inventory/' + issueId.apiInfoKey.apiCollectionId + '/' +
                btoa(issueId.apiInfoKey.url + " " + issueId.apiInfoKey.method);
        },
        getSeverityName(severity) {
            return severity.charAt(0) + severity.slice(1).toLowerCase();
        },
        getSeverityColor(severity) {
            switch (severity) {
                case "HIGH":
                    return "#FF1717";
                case "MEDIUM":
                    return "#FF8717"
                case "LOW":
                    return "#1790FF"
            }
        },
        getSeverityClass(severity) {
            return {
                'severity-high': severity === "HIGH",
                'severity-medium': severity === "MEDIUM",
                'severity-low': severity === "LOW"
            }
        }
    }
}

</script>

<style scoped >

.severity-chip >>> .v-icon {
    justify-content: flex-start !important;
    width: 12px !important;
}

.severity-chip {
    font-family: 'Inter';
    font-style: normal;
    font-weight: 500;
    font-size: 12px;
}

.fixed-button {
    background-color: var(--v-themeColor-base) !important;
    color: #FFFFFF;
    width: 175px;
    font-family: 'Poppins', sans-serif;;
    font-style: normal;
}

.button-menu>>>.v-input__slot {
    background-color: var(--v-themeColor-base) !important;
    cursor: pointer !important;
    font-size: 12px;
    font-family: 'Poppins', sans-serif;;
    font-style: normal;
}

.button-menu>>>.v-label {
    color: #FFFFFF !important;
    cursor: pointer !important;
}

.button-menu {
    width: 175px;
}

.display-flex-heading {
    display: flex;
    justify-content: space-between;
}

.collection-span-css {
    float: none;
    align-self: right;
}

.margin-left-24 {
    margin-left: 24px;
}

.underline {
    text-decoration: underline;
}

.issue-collection {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 14px;
    align-items: right;
    color: #6200EA;
    text-decoration-line: underline;
}

.issue-time {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 14px;
    margin-right: 24px;
    align-items: right;
    color: #101828;
}

.issue-endpoint {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
    /* identical to box height */
    align-items: left;
    color: #6200EA;
}

.issue-method {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 600;
    font-size: 14px;
    /* identical to box height */
    margin-bottom: 24px;
    align-items: left;
    color: #5C04D5;
}

.display-flex-url {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    margin-bottom: 24px;
}

.issue-title {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 500;
    font-size: 20px;
    /* identical to box height */
    text-align: left;
    margin-top: 24px;
    margin-left: 24px;
    color: #101828;
}

.issue-description {
    font-family: 'Poppins', sans-serif;
    font-style: normal;
    font-weight: 400;
    font-size: 14px;
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