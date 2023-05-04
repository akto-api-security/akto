<template>
    <div v-if="messages && messages.length > 0">
        <div>
            <div v-if="messages.length > 1" class="d-flex jc-sb mr-3">
                <div v-if="json.title" style="margin: auto 8px; color: var(--themeColorDark)">{{json.title}}</div>
                
                <v-btn icon @click="currentIndex = (++currentIndex)%messages.length">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div v-if="json['message']" style="margin: 24px">
                <sample-data :json="json" requestTitle="Request" responseTitle="Response"/>
            </div>
        </div>
    </div>
    <div v-else class="empty-container">
        No samples values saved yet!
    </div>
</template>

<script>
import obj from "@/util/obj"

import SampleData from "./SampleData"

export default {
    name: "SampleDataList",
    components: {
    SampleData
    },
    props: {
        messages: obj.arrR
    },
    data () {
        return {
            currentIndex: 0
        }
    },
    computed: {
        json: function() {
            let currentMessage = this.messages[this.currentIndex]
            return {
                "message": JSON.parse(currentMessage["message"]),
                title: currentMessage["title"],
                "highlightPaths": currentMessage["highlightPaths"],
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.flex-equal
    width: 50%

.empty-container
    font-size: 13px
    color: var(--themeColorDark)
    margin: 8px 8px 0 0 

</style>
