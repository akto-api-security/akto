<template>
    <div class="issue-box ml-8 mr-8 mt-4 mb-4" :style='{ "border-left": "6px solid " + getSeverityColor(severity) }'>
        <div class="display-flex-heading">
            <div class="issue-title mt-6 ml-6">
                <span class="mr-2"
                    v-if="!(filterStatus.length === 0 || filterStatus.length === 1 && filterStatus[0] === 'FIXED')">
                    <input type="checkbox" class="checkbox-primary issue-checkbox-size" v-model="issueChecked" />
                </span>
                <span class="clickable-line" @click="openDialogBox">{{ categoryName }}</span>
                <v-chip outlined dark class="severity-chip ml-3" :color="getSeverityColor(severity)">
                    <v-icon size="6">$fas_circle</v-icon>
                    {{ getSeverityName(severity) }}
                </v-chip>
            </div>
            <div class="mr-6 mt-4">
                <div v-if="issueStatus !== 'FIXED'">
                    <v-menu offset-y>
                        <template v-slot:activator="{ on, attrs }">
                            <v-btn v-bind="attrs" v-on="on" primary class="white--text ignore-button"
                                color="var(--v-themeColor-base)">
                                <span>{{ label(issueStatus, ignoreReason) }}</span>
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
        <div class="issue-description mt-1 ml-6 mb-3">{{ categoryDescription }}</div>
        <div class="display-flex-url mb-4">
            <span>
                <span class="issue-method ml-6 mb-6">{{ method }}</span>
                <span class="issue-endpoint clickable-line"><a :href="getEndpointLink(issueId)" target="_blank">{{
                        endpoint
                }}</a></span>
            </span>
            <span class="collection-span-css">
                <v-icon>$far_folder-open</v-icon>
                <span class="issue-collection clickable-line"><a class="truncate-collection"
                        :href="getCollectionLink(issueId)" target="_blank">
                        {{ collectionName }}
                    </a></span>
                <v-icon>$far_clock</v-icon>
                <span class="issue-time mr-6">{{ getCreationTime(creationTime) }}</span>
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
        issueChecked(newValue) {
            this.$emit('clickedIssueCheckbox', { 'issueId': this.issueId, 'checked': newValue })
        }
    },
    methods: {
        getCreationTime: func.getCreationTime,
        openDialogBox() {
            this.$emit('openDialogBox', { 'issueId': this.issueId })
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
                this.$emit('clickedIssueCheckbox', { 'issueId': issueId, 'checked': false })
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
                    return "var(--hexColor3)";
                case "MEDIUM":
                    return "var(--hexColor2)"
                case "LOW":
                    return "var(--hexColor1)"
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
.severity-chip>>>.v-icon {
    justify-content: flex-start !important;
    width: 12px !important;
}

.ignore-button {
    font-weight: 500;
    font-size: 12px;
    padding-left: 10px !important;
    padding-right: 10px !important;
}

.severity-chip {
    font-weight: 500;
    font-size: 12px;
    height: 24px !important;
    padding-left: 8px !important;
    padding-right: 10px !important;
    border-width: 1.5px !important;
}

.display-flex-heading {
    display: flex;
    justify-content: space-between;
}

.issue-checkbox-size {
    width: 20px;
    height: 20px;
    display: inline-block;
    vertical-align: middle;
}

.collection-span-css {
    float: none;
    align-self: right;
}

.issue-collection {
    font-weight: 500;
    font-size: 14px;
    align-items: right;
}

.truncate-collection {
    max-width: 300px !important;
    overflow: hidden !important;
    white-space: nowrap !important;
    text-overflow: ellipsis !important;
    display: table-cell;
}

.issue-time {
    font-weight: 500;
    font-size: 14px;
    align-items: right;
    color: var(--hexColor5);
}

.issue-endpoint {
    font-weight: 400;
    font-size: 14px;
    /* identical to box height */
    align-items: left;
}

.issue-method {
    font-weight: 600;
    font-size: 14px;
    /* identical to box height */
    align-items: left;
    color: var(--hexColor12)
}

.display-flex-url {
    display: flex;
    flex-direction: row;
    justify-content: space-between;
}

.issue-title {
    font-weight: 500;
    font-size: 20px;
    /* identical to box height */
    text-align: left;
    color: var(--themeColorDark);
}

.issue-description {
    font-weight: 400;
    font-size: 14px;
    color: var(--themeColorDark3);
}

.issue-box {
    height: fit-content;
    background: var(--white2);
    border: 1px solid var(--hexColor21);
    border-radius: 6px;
}

.severity-high {
    border-left: 6px solid var(--hexColor3);
}

.severity-medium {
    border-left: 6px solid var(--hexColor2);
}

.severity-low {
    border-left: 6px solid var(--hexColor1);
}
</style>