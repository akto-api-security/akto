<template>
    <div v-if="messages && messages.length > 0">
        <div>
            <div v-if="messages.length > 1" class="d-flex jc-end mr-3">
                <v-btn icon @click="currentIndex = (++currentIndex)%messages.length">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div class="d-flex">
                <sample-single-side
                    class="flex-equal"
                    title="Request" 
                    :firstLine='json.method + " " + json.path + " " + json.type'
                    :headers="JSON.parse(json.requestHeaders)" 
                    :data="json.requestPayload"
                />
                <sample-single-side 
                    class="flex-equal"                
                    title="Response" 
                    :firstLine='json.statusCode + " " + json.status' 
                    :headers="JSON.parse(json.responseHeaders)" 
                    :data="json.responsePayload"
                />
            </div>
        </div>
    </div>
    <div v-else class="empty-container">
        No samples values saved yet!
    </div>
</template>

<script>
import obj from "@/util/obj"

import SampleSingleSide from './SampleSingleSide'

export default {
    name: "SampleData",
    components: {
        SampleSingleSide
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
            return this.messages.length > 0 ? JSON.parse(this.messages[this.currentIndex]) : {}
        }
    }
}
</script>

<style lang="sass" scoped>
.flex-equal
    width: 50%

.empty-container
    font-size: 13px
    color: #47466A
    margin: 8px 8px 0 0 

</style>