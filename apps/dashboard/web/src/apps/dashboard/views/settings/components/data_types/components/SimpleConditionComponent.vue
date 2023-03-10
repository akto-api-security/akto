<template>
    <div class='condition-block'>
        <span class="inline-block-child" style="color: var(--themeColor)">
            {{ initial_string }}
        </span>
        <div class="inline-block-child pl-5">
            <div class="text-center">
                <v-menu offset-y>
                    <template v-slot:activator="{ on, attrs }">
                        <span v-if="condition.type" v-bind="attrs" v-on="on" style="text-decoration: underline">
                            {{ formatConditionType(condition.type) }}
                        </span>
                        <span v-else v-bind="attrs" v-on="on" style="text-decoration: italic; color: grey">
                            click
                        </span>
                    </template>
                    <v-list v-if="(onlyEqual == false)">
                        <v-list-item v-for="(item, index) in operation_types" :key="index"
                            @click="$emit('conditionTypeChanged', item.value)">
                            <v-list-item-title>{{ item.text }}</v-list-item-title>
                        </v-list-item>
                    </v-list>
                </v-menu>
            </div>
        </div>
        <div class="inline-block-child pl-5 mr-4"
            v-if="requireTextInputForTypeArray.includes(condition.type)">
            <v-text-field placeholder="value" flat :style="getValueStyle(condition.value)"
                v-model="condition.value" class="value_predicate ma-0 pa-0" hide-details :rules="[value => !!value || 'Required']" />
        </div>
        <div class="inline-block-child pl-5"
            v-else-if="requireMapInputForTypeArray && requireMapInputForTypeArray.includes(condition.type)">

            <!-- Collection menu  -->
            <v-menu offset-y>
                <template v-slot:activator="{ on, attrs }">
                    <v-btn class="filter-button ml-2" v-bind="attrs" v-on="on" primary>
                        <span>{{getSelectedCollectionName(condition)}}</span>
                        <v-icon>$fas_angle-down</v-icon>
                    </v-btn>
                </template>
                <filter-list :title="getSelectedCollectionName(condition)" :items="getAllCollectionsForFilterList()" 
                @clickedItem="$emit('collectionSelected', $event)"
                    hideOperators hideListTitle selectExactlyOne />
            </v-menu>

            <!-- End point menu -->
            <v-menu offset-y :close-on-content-click="false">
                <template v-slot:activator="{ on, attrs }">
                    <v-btn class="filter-button mr-3 ml-4" :ripple="false" v-bind="attrs" v-on="on" primary>
                        <span>Api endpoints<v-icon :size="14">$fas_angle-down</v-icon></span>
                    </v-btn>
                </template>
                <filter-list title="Api endpoints" :items="getSelectedCollectionEndpoints(condition)"
                    @clickedItem="$emit('clickedApiEndpoint', $event)"
                     hideOperators
                    @selectedAll="$emit('selectedAllApiEndpoints', $event)" />
            </v-menu>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"
import FilterList from '@/apps/dashboard/shared/components/FilterList'


export default {
    name: "SimpleConditionComponent",
    props: {
        condition: obj.objR,
        initial_string: obj.strR,
        operators: obj.arrR,
        operation_types: obj.arrR,
        requireTextInputForTypeArray: obj.arrR,
        requireMapInputForTypeArray: obj.arrN,
        onlyEqual: {
            type: Boolean
        },
    },
    components: {
        FilterList
    },
    data() {
        return {

        }
    },
    methods: {
        getSelectedCollectionName (condition) {
            if (condition.value) {
                return this.mapCollectionIdToName[Object.keys(condition.value)[0]]
            }
            return  'Select collection'
        },
        getSelectedCollectionEndpoints(condition) {
            if (condition.value) {
                return condition.value[Object.keys(condition.value)[0]]
            }
        },
        getAllCollectionsForFilterList() {
            let item = []
            Object.keys(this.mapCollectionIdToName).forEach(element => {
                item.push({title:this.mapCollectionIdToName[element], value: element})
            })
            return item
        },
        getValueStyle(value) {
            let width = !value ? 6 : value.length + 4
            return { 'width': width + 'ch' }
        },
        getValueStyleOperator(value) {
            let width = !value ? 6 : value.length + 4
            return { 'width': width + 'ch', "color": "grey" }
        },
        formatConditionType(value) {
            let finalValue = null
            for (let i = 0; i < this.operation_types.length; i++) {
                let operation = this.operation_types[i]
                if (operation["value"] === value) {
                    finalValue = operation["text"]
                    break
                }
            }

            return finalValue;
        }
    },
    created() {

    },
    computed: {
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch: {

    }

}
</script>

<style lang="sass" scoped>

.condition-block
    background: var(--colTableBackground) 
    width: fit-content
    height: fit-content

.inline-block-child
    margin-top: 12px
    margin-bottom: 12px
    margin-left: 16px
    display: inline-block


</style>

<style >
.filter-button {
    box-sizing: border-box;

    width: fit-content;
    height: 40px;

    background: var(--white);
    border: 1px solid var(--hexColor22);

    font-weight: 500;
    font-size: 14px;

    box-shadow: 0px 1px 2px var(--rgbaColor16);
    border-radius: 4px;
}

.inline-block-child .v-text-field .v-input__control .v-input__slot {
    min-height: auto !important;
    display: flex !important;
    align-items: center !important;
    background-color: transparent;
}

.inline-block-child .v-input__slot::before {
    border-style: none !important;
}

.inline-block-child .value_predicate .v-text-field__slot input {
    color: var(--hexColor38) !important;
}
</style>