<template>
    <div class="filter-container">
        <div class="list-header">
            <div>{{title}}</div>
        </div>
        <div v-if="!hideOperators" class="d-flex jc-end pr-2" style="background-color: #FFFFFF">
            <v-btn 
                v-for="key, index in operators" 
                plain 
                :ripple="false" 
                :key="index"
                @click=operatorClicked(key)
                :class="['operator', selectedOperator === key ? 'underline': '']"
            >
                {{key}}
            </v-btn>
        </div>
        <v-text-field
            v-model="searchText"
            dense
            color="#6200EA"
            prepend-inner-icon="$fas_search"
            class="px-2 py-3"
        >
            <template v-slot:prepend-inner>
                <v-icon size="12" color="#6200EA">$fas_search</v-icon>
            </template>
        </v-text-field>
        <div class="d-flex jc-end pr-4 pb-2">
            <v-btn 
                primary
                color="#6200EA" 
                :ripple="false" 
                @click=applyClicked
            >
                <div style="color: #FFFFFF">Apply</div>
            </v-btn>
        </div>

    </div>    
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "FilterSearch",
    props: {
        title: obj.strR,
        hideOperators: obj.boolN        
    },
    data() {
        return {
            searchText: "",
            operators: ['OR', 'AND', 'NOT'],
            selectedOperator: 'OR'
        }
    },
    methods: {
        operatorClicked(operator) {
            this.selectedOperator = operator
            this.$emit('operatorChanged', {operator})
        },
        applyClicked() {
            this.$emit('clickedItem', {searchText: this.searchText, operator: this.selectedOperator})
        }
    }
}
</script>

<style lang="sass" scoped>
.filter-container
    background-color: #FFFFFF
    width: 250px
.list-header
    border-bottom: 1px solid #47466A    
    font-weight: 500
    display: flex
    justify-content: space-between
    padding: 8px 16px
    color: #47466A
    background: white
    opacity: 1
    font-size: 14px
.underline
    text-decoration: underline 
    color: #6200EA !important
.operator
    color: #47466A99
    min-width: unset !important
    padding: 0px 8px !important

</style>

<style scoped>
.v-text-field >>> input {
    font-size: 13px
}

.v-text-field >>> .v-input__prepend-inner {
    margin: auto !important
}

.v-text-field >>> .v-text-field__details {
    display: none !important;
}
</style>