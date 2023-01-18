<template>
    <div>
        <div>
            <span class="category-title">{{categoryTitle}}</span>
            <span>
                <a :href='githubLink' class="github-link ml-2" target="_blank">Contribute in GithHub</a>
            </span>
        </div>
        <spinner v-if="loading"/>
        <div v-else class="testcases-layout">
            <div v-for="(item, index) in testSourceConfigs" :key="index" class="testcase-container">
                <v-icon size="20" color="#24292F" class="icon-border">$fab_github</v-icon>
                <div>
                    <div class="testcase-title">{{getName(item.id)}}</div>
                    <div>
                        <a :href='item.id' class="github-link" target="_blank">Contribute in GithHub</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"
import api from '../api'

import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "MPTestCategory",
    props: {
        categoryType: obj.strR, 
        categoryId: obj.strR
    },
    components: {
        Spinner
    },
    data() {
        return {
            githubLink: this.categoryType === "default" ? "https://github.com/akto-api-security/testing_sources" : null,
            testSourceConfigs: [],
            categoryTitle: this.categoryId.replaceAll("_", " "),
            loading: false
        }
    },
    methods: {
        getName(filePath) {
            return filePath.substring(filePath.lastIndexOf("/")+1, filePath.lastIndexOf("."))
        } 
    },
    mounted() {
        this.loading = true
        api.fetchTestingSources(this.categoryType === "default", this.categoryId).then(resp => {
            this.testSourceConfigs = resp.testSourceConfigs;
            this.loading = false
        }).catch(() => {
            this.loading = false
        })
    }
}
</script>

<style scoped lang="sass">
.category-title
    font-weight: 600
    font-size: 18px

.github-link
    font-weight: 300
    font-size: 12px
    text-decoration: underline 
        
.testcases-layout
    display: flex
    margin: 32px 0
    flex-wrap: wrap
    gap: 40px
    justify-content: space-between
    & > div
      flex: 1 0 40%
        

.testcase-container
    display: flex      
    max-width: 45%
    flex-grow: 1
    flex-shrink: 0
    margin-bottom: 32px

.icon-border
    border-radius: 50%
    box-shadow: 0px 2px 4px rgb(45 44 87 / 10%)
    min-width: 40px    
    min-height: 40px
    margin-right: 8px
      
.testcase-title
    font-weight: 600
    font-size: 14px
    color: var(--v-themeColor-base)

.testcase-description
    margin-top: 8px
    font-weight: 400
    font-size: 14px
    max-height: 63px
    overflow: hidden
    opacity: 0.8

.testcase-usage-icon
    width: 14px !important

.testcase-usage-count
    font-weight: 400
    font-size: 12px

.testcase-usage-text   
    margin-right: 8px    
    font-weight: 400
    font-size: 12px

</style>