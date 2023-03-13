<template>
    <div class="data-types-container d-flex">
                <a-card
                    :title="title" 
                    color="var(--rgbaColor2)"
                    style="min-height: 600px; flex: 1 1 20%"
                    icon_right="$fas_plus"
                    icon_right_color="var(--themeColor)"
                    @icon_right_clicked="createNewDataType"
                >
                    <div v-for="data_type in data_types" :key="data_type.name">
                        <data-type-card :item="data_type" :actions="data_type_card_actions" @selectedEntry="entryUpdated"/>
                    </div>
                </a-card>
            <div class="details-container" >
                <slot name="details-container"/>
            </div>
    </div>
</template>

<script>
import ACard from "@/apps/dashboard/shared/components/ACard"
import DataTypeCard from "./components/DataTypeCard.vue"
import obj from '@/util/obj'
import {mapState} from 'vuex'
export default {
    name: "DataTypes",
    components: {
        ACard,
        DataTypeCard
    },
    props: {
        title: obj.strR,
        data_types: obj.arrN,
        toggleActivateFieldFunc: obj.funcR,
        createNewDataType: obj.funcR,
        selectedEntry: obj.funcR
    },
    data() {
        return {
            data_type_card_actions: [
                {
                    isValid: item => item.id,
                    icon: item => item.active? '$fas_trash' : '$fas_check',
                    text: item => item.active? 'Deactivate' : 'Activate',
                    func: item => this.toggleActivateFieldFunc(item),
                    success: (resp, item) => this.toggleSuccessFunc(resp, item),
                    failure: (err, item) => this.toggleFailureFunc(err, item)
                }
            ],
        }
    },
    methods: {
        entryUpdated(item) {
            this.$emit("selectedEntry", item)
        },
        toggleSuccessFunc (resp, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${item.name} `+ (item.active? 'de' : '') +`activated successfully!`,
                color: 'green'
            })
        },
        toggleFailureFunc (err, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `An error occurred while `+ (item.active? 'de' : '')+`activating ${item.name}!`,
                color: 'red'
            })
        },
    }
}

</script>

<style lang="sass" scoped>
.main
    background-color: var(--themeColorDark)
    
.details-container
    flex: 1 1 80%

</style>