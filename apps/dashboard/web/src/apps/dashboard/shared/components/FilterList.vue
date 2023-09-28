<template>
    <div style="background: #FFFFFF">
        <div v-if="!hideListTitle" class="list-header">
            <div>{{title}}</div>
            <div>{{Object.values(checkedMap).filter(x => x).length}}/{{Object.values(checkedMap).length}}</div>
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
        <v-list dense class="filter-list" :style="{'width':  width || '250px'}">
            <v-list-item v-if="items && items.length > 8">
                <span v-if="!selectExactlyOne">
                    <v-btn icon primary plain :ripple="false" @click="globalCheckboxClicked" class="checkbox-btn">
                        <v-icon>
                            {{globalCheckbox? '$far_check-square': '$far_square'}}
                        </v-icon>
                    </v-btn>
                </span>

                <v-text-field
                    v-model="searchText"
                    dense
                    ref="searchTextInput"
                    color="var(--themeColor)"
                    class="search-text-input"
                    prepend-inner-icon="$fas_search"
                    @click.stop="() => {a=1}"
                >
                    <template v-slot:prepend-inner>
                        <v-icon size="12" color="var(--themeColor)">$fas_search</v-icon>
                    </template>
                </v-text-field>
            </v-list-item>
            <v-list-item v-if="filteredItems.length > pageSize">
                <div style="display: flex;margin-left: auto;">
                    <span class="item-label" style="padding-top: 10px !important">{{startItemIndex + 1}} - {{endItemIndex}} of {{filteredItems.length}}</span>
                    <span class="item-label">
                        <v-btn icon :disabled="startItemIndex == 0" @click="pageNumber--">
                            <v-icon>$fas_angle-left</v-icon>
                        </v-btn>
                    </span>
                    <span class="item-label">
                        <v-btn icon :disabled="endItemIndex == filteredItems.length" @click="pageNumber++">
                            <v-icon>$fas_angle-right</v-icon>
                        </v-btn>
                    </span>
                </div>
            </v-list-item>

            <v-list-item v-for="(item, index) in filteredItems.slice(startItemIndex, endItemIndex)" :key="index">
                <span v-if="!selectExactlyOne">
                    <v-btn icon primary plain :ripple="false" @click="checkboxClicked(item)" class="checkbox-btn">
                        <v-icon>
                            {{checkedMap[item.value]? '$far_check-square': '$far_square'}}
                        </v-icon>
                    </v-btn>
                </span>
                <v-list-item-content v-if="!selectExactlyOne">
                    <span class="item-label">{{item.title}}</span>
                    <span class="item-subtitle">{{item.subtitle}}</span>
                </v-list-item-content>
                <v-list-item-content v-else class="clickable-bg" @click="checkboxClicked(item)">
                    <span class="item-label">{{item.title}}</span>
                    <span class="item-subtitle">{{item.subtitle}}</span>
                </v-list-item-content>
            </v-list-item>
        </v-list>
    </div>    
</template>

<script>

import obj from '@/util/obj'

export default {
    name: "FilterList",
    props: {
        title: obj.strR,
        items: obj.arrN,
        width: obj.strN,
        hideOperators: obj.boolN,
        hideListTitle: obj.boolN,
        selectExactlyOne: obj.boolN,
        listOperators: obj.arrN
    },
    data () {
        return {
            checkedMap: this.items.reduce((m, i) => {
                if (i.checked) {
                    m[i.value] = true
                } else {
                    m[i.value] = false
                }
                return m
            }, {}),
            searchText: "",
            globalCheckbox: false,
            pageNumber: 1,
            pageSize: 1000,
            operators: this.listOperators || ['OR', 'AND', 'NOT'],
            selectedOperator: 'OR'
        }
    },
    mounted() {
        let inputEl = this.$refs?.searchTextInput?.$el.querySelector('.search-text-input input')
        setTimeout(()=>{
            inputEl?.focus()
        },200)

    },
    methods: {
        operatorClicked(operator) {
            this.selectedOperator = operator
            this.$emit('operatorChanged', {operator})
        },
        checkboxClicked(item) {
            this.checkedMap[item.value] = !this.checkedMap[item.value]
            this.$emit('clickedItem', {item: item, checked: this.checkedMap[item.value], operator: this.selectedOperator})
        },
        textChanged () {
            if (this.searchText && this.searchText.length > 0) {

            } else {

            }
        },
        globalCheckboxClicked () {
            this.globalCheckbox = !this.globalCheckbox
            for(var index in this.filteredItems) {
                this.checkedMap[this.filteredItems[index].value] = this.globalCheckbox
            }
            this.$emit('selectedAll', {items: this.filteredItems,checked: this.globalCheckbox})
        }
    },
    computed: {
        filteredItems () {
            this.pageNumber = 1
            if (this.searchText && this.searchText.length > 0) {
                return this.items.filter(x => x.title.toLowerCase().indexOf(this.searchText) != -1).sort((x,y) => x.title > y.title ? 1 : -1)
            } else {
                return this.items.sort((x,y) => x.title > y.title ? 1 : -1)
            }
            
        },
        startItemIndex() {
            return (this.pageNumber - 1) * this.pageSize
        },
        endItemIndex() {
            return Math.min(this.startItemIndex + this.pageSize, this.filteredItems.length)
        }
    }
}
</script>

<style lang="sass" scoped>
.item-label
    font-size: 12px
    padding: 0px !important
.item-subtitle
    font-size: 9px
    color: var(--themeColorDark7)
.checkbox-btn
    min-height: 24px !important
    color: var(--themeColorDark) !important
.filter-list
    height: 350px
    overflow-y: scrollbar
    overflow-x: hidden
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