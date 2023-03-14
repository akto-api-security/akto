<template>
    <div class="filter-container">
        <div class="list-header">
            <div>{{title}}</div>
        </div>
        <div v-if="!hideOperators" class="d-flex jc-end pr-2" style="background-color: var(--white)">
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
            color="var(--themeColor)"
            prepend-inner-icon="$fas_search"
            class="px-2 py-3"
        >
            <template v-slot:prepend-inner>
                <v-icon size="12" color="var(--themeColor)">$fas_search</v-icon>
            </template>
        </v-text-field>
        <div class="d-flex jc-end pr-4 pb-2">
            <v-btn 
                primary
                color="var(--themeColor)" 
                :ripple="false" 
                @click=applyClicked
            >
                <div style="color: var(--white)">Apply</div>
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
    background-color: var(--white)
    width: 250px
.list-header
    border-bottom: 1px solid var(--themeColorDark)    
    font-weight: 500
    display: flex
    justify-content: space-between
    padding: 8px 16px
    color: var(--themeColorDark)
    background: white
    opacity: 1
    font-size: 14px
.underline
    text-decoration: underline 
    color: var(--themeColor) !important
.operator
    color: var(--themeColorDark7)
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