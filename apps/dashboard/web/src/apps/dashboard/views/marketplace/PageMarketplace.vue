<template>
    <simple-layout title="Tests library">
        <template>
            <div class="pa-8">
                <div>                
                    <layout-with-left-pane>
                        <div class="category-tests">
                            <router-view :key="$route.fullPath"/>
                        </div>
                        <template #leftPane>
                            <v-navigation-drawer
                                v-model="drawer"
                                floating
                                width="250px"
                            >
                                <v-btn primary outlined color="#6200EA" @click="showCreateTestDialog = true" class="ma-4">
                                    <div class="d-flex">
                                        <div class="my-auto"><v-icon size="14">$fas_plus</v-icon></div>
                                        <div class="my-auto">Add test</div>
                                    </div>
                                </v-btn>
                                <div class="nav-section">
                                    <api-collection-group
                                        :items=leftNavItems
                                    >
                                        <!-- <template #prependItem>
                                            <v-btn primary dark color="#6200EA" tile style="width: -webkit-fill-available" class="mt-8 mb-8">
                                                <div style="width: 100%">
                                                    <v-icon>$fas_plus</v-icon> 
                                                    New test
                                                </div>
                                            </v-btn>
                                        </template> -->
                                    </api-collection-group>
                                </div>
                            </v-navigation-drawer>
                        </template>
                    </layout-with-left-pane>
                </div>
            </div>
            <v-dialog v-model="showCreateTestDialog" width="600px">
                <a-card title="Add test" icon="$fas_plus"  class="ma-0">
                        <div class="py-4 px-6">
                            <div class="input-title">
                                <div>Raw URL of Nuclei template</div>
                                <v-text-field
                                    v-model="newTest.url"
                                    dense
                                    class="form-field-text"                             
                                />
                            </div>
                            <div class="input-title">
                                <div>Category</div>
                                <v-select
                                    class="form-field-select"
                                    :items="businessCategoryNames"
                                    v-model="newTest.category"
                                    attach
                                />
                            </div>
                            <div class="input-title mb-4">
                                <div>Subcategory</div>
                                <v-menu v-model="showSubcategoriesMenu">
                                    <template v-slot:activator="{ on, attrs }">
                                        <div v-bind="attrs" v-on="on" class="category-selection">
                                            {{newTest.subcategory}}
                                        </div>
                                    </template>

                                    <v-list class="form-field-select py-2">
                                        <v-list-item
                                            v-for="(item, index) in allSubcategories"
                                            :key="index"
                                            :class='["clickable", newTest.subcategory == item ? "c-item--selected" : "", "c-item--title"]'
                                            @click="newTest.subcategory = item" 
                                        >
                                            <v-list-item-title>
                                                {{ item }}
                                            </v-list-item-title>
                                        </v-list-item>
                                        <v-list-item
                                            :class='["clickable", "c-item--title"]'
                                            @click="(e) => {addNewSubcategory = true; e.stopPropagation(); e.preventDefault()}" 
                                        >
                                            <v-list-item-title class="primary--text">
                                                <div v-if="addNewSubcategory">
                                                    <simple-text-field
                                                        :readOutsideClick="true"
                                                        placeholder="Add new"
                                                        @changed="(name) => {newTest.subcategory = name; addNewSubcategory = false; showSubcategoriesMenu=false}"
                                                        @aborted="addNewSubcategory = false"
                                                    >

                                                    </simple-text-field>
                                                </div>

                                                <div v-else class="primary--text fs-14"><v-icon color="#6200EA" size="12">$fas_plus</v-icon>Add new</div>
                                            </v-list-item-title>
                                        </v-list-item>
                                    </v-list>
                                </v-menu>
                            </div>
                            <div class="input-title">
                                <div>Severity</div>
                                <v-select
                                    class="form-field-select"
                                    :items="allSeverities"
                                    v-model="newTest.severity"
                                    attach
                                />
                            </div>
                            <div class="d-flex" style="justify-content: right">
                                <v-btn primary outlined color="#47466A" @click="showCreateTestDialog = false" class="mr-4">Cancel</v-btn>
                                <v-btn :disabled="validateUrl()" primary dark color="#6200EA" @click="addTest">Add</v-btn>
                            </div>
                        </div>
                </a-card>
            </v-dialog>
        </template>
    </simple-layout>
</template>

<script>
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'
import ACard from '@/apps/dashboard/shared/components/ACard'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'

import api from './api'
import issuesApi from '../issues/api'
import func from '@/util/func'
import { mapState } from 'vuex'

export default {
    name: "PageMarketplace",
    components: { 
        SimpleLayout,
        LayoutWithLeftPane,
        ApiCollectionGroup,
        ACard,
        SimpleTextField
    },
    data() {
        let allSubcategories = ["path_traversal", "swagger_file_detection"]
        let allSeverities = ["HIGH", "MEDIUM", "LOW"]
        
        return {
            drawer: null,
            showCreateTestDialog: false,
            allSubcategories,
            allSeverities,
            addNewSubcategory: false,
            showSubcategoriesMenu: false,
            businessCategories: [],
            newTest: {
                url: "",
                category: "BOLA",
                subcategory: allSubcategories[0],
                severity: allSeverities[0],
                description: ""
            }
        }
    },
    methods: {
        validateUrl() {
            let url = this.newTest.url
            let isGoodUrl = url != null && (url.startsWith("http://") || url.startsWith("https://")) && (url.endsWith(".yml") || url.endsWith(".yaml"))
            return !isGoodUrl
        },
        addTest() {
            this.showCreateTestDialog = false
            api.addCustomTest(this.newTest).then(resp => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Test added successfully!`,
                    color: 'green'
                })
            })
        },
        nameToKvObj(names) {
            return names.map(x => {
                return {
                    text: x,
                    value: x.toLowerCase().replaceAll(" ", "_")
                }
            })
        },
        createCategoryObj(arrCategoryKv, creatorTitle, creatorType, colorType) {
            return {
                icon: "$fas_plus",
                title: creatorTitle,
                group: "/dashboard/library/"+creatorType,
                color: func.actionItemColors()[colorType],
                active: true,
                items: [
                    ...arrCategoryKv.map(category => {
                        return {
                            title: category.text,
                            link: "/dashboard/library/"+creatorType+"/"+category.value,
                            icon: "$fas_plus",
                            active: false
                        }
                    })
                ]
            }
        }
    },
    async mounted() {
        await this.$store.dispatch('marketplace/fetchAllMarketplaceSubcategories')
        let aktoTestTypes = await issuesApi.fetchAllSubCategories()
        this.businessCategories = aktoTestTypes.subCategories
        this.$router.push(this.leftNavItems[0].items[0].link)
        
    },
    computed: {
        ...mapState('marketplace', ['defaultSubcategories', 'userSubcategories', 'loading']),
        businessCategoryNames() {
            return [...new Set(this.businessCategories.map(category => category.superCategory.name))]
        },
        leftNavItems() {
            return [
                this.createCategoryObj(this.nameToKvObj(this.businessCategoryNames.concat(this.defaultSubcategories)), "Categories", "default", "This week"),
                this.createCategoryObj(this.nameToKvObj(this.userSubcategories), "Your tests", "custom", "Total")
            ]
        }
    }
}
</script>

<style lang="sass" scoped>
.category-tests
    color: #47466A
    font-family: Poppins, sans-serif !important

.input-title
    display: flex
    color: #47466A
    padding-bottom: 8px
    & > div:first-child
        font-size: 14px
        font-weight: 500
        max-width: 200px
        min-width: 200px
        width: 200px

.form-field-select
    padding: 0px
    margin: 0px    
        
</style>

<style scoped>
.form-field-select >>> .v-list-item__title {
    font-size: 14px !important;
    color: #47466A !important;
}

.category-selection {
    font-size: 14px !important;
    color: #47466A !important;
    margin: 0px !important;
    width: 100%;
    padding-bottom: 8px;
    border-bottom: 1px solid #949494;
}

.form-field-select >>> .v-select__selection {
    font-size: 14px !important;
    color: #47466A !important;
    margin: 0px !important;
}

.form-field-select >>> .v-list-item__content {
    padding: 0px !important;
    color: #47466A !important;
}

.form-field-select >>> .v-list-item {
    min-height: 30px !important;
}

.form-field-text >>> input {
    font-size: 14px !important;
    color: #47466A !important;    
}

.c-item--selected {
    background: #e8d8fd;
}

.c-item--title:hover {
    background: #f6f6f6;
}
</style>