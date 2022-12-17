<template>
    <div>
        <div>
            <span class="category-title">{{categoryTitle}}</span>
            <span>
                <a :href='githubLink' class="github-link ml-2" target="_blank">Contribute in GithHub</a>
            </span>
        </div>
        <div class="testcases-layout">
            <div v-for="(item, index) in arrTestcasesDetails" :key="index" class="testcase-container">
                <v-icon size="40" color="#D3CD28" class="icon-border">$fas_anchor</v-icon>
                <div>
                    <div class="testcase-title">{{item.title}}</div>
                    <div>
                        <a :href='item.githubEditLink' class="github-link" target="_blank">Contribute in GithHub</a>
                    </div>
                    <div class="testcase-description">{{item.description}}</div>
                    <div class="testcase-description">
                        <span>
                            <v-icon size="16" class="testcase-usage-icon">$fas_long-arrow-alt-down</v-icon>
                            <span class="testcase-usage-count">{{prettify(item.installs)}}</span>
                            <span class="testcase-usage-text">installs</span>
                        </span>
                        <span>
                            <v-icon size="16" class="testcase-usage-icon">$far_star</v-icon>
                            <span class="testcase-usage-count">{{prettify(item.githubStars)}}</span>
                            <span class="testcase-usage-text">stars</span>
                        </span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"
import func from "@/util/func"

export default {
    name: "MPTestCategory",
    props: {
        categoryType: obj.strR, 
        categoryId: obj.strR
    },
    data () {
        return {
            categoryTitle: "XSS",
            githubLink: "https://github.com/akto-api-security/pii-types/blob/master/general.json",
            arrTestcasesDetails: [
                {
                    title: "XSS-Fuzzing",
                    githubEditLink: "https://github.com/danielmiessler/SecLists/blob/master/Fuzzing/XSS-Fuzzing",
                    githubRawLink: "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Fuzzing/XSS-Fuzzing",
                    githubStars: 43735,
                    description: "An XSS collection assembled for all kinds of browsers and frameworks. The description can span multiple lines.",
                    installs: 2342
                },
                {
                    title: "xss-payload-list",
                    githubEditLink: "https://github.com/payloadbox/xss-payload-list/blob/master/Intruder/xss-payload-list.txt",
                    githubRawLink: "https://raw.githubusercontent.com/payloadbox/xss-payload-list/master/Intruder/xss-payload-list.txt",
                    githubStars: 4093,
                    description: "An XSS collection assembled for all kinds of browsers and frameworks",
                    installs: 322
                },
                {
                    title: "xss-all-list",
                    githubEditLink: "https://github.com/Proviesec/xss-payload-list/blob/main/xss-all-list.txt",
                    githubRawLink: "https://raw.githubusercontent.com/Proviesec/xss-payload-list/main/xss-all-list.txt",
                    githubStars: 61,
                    description: "An XSS collection assembled for all kinds of browsers and frameworks",
                    installs: 3
                },
                {
                    title: "XSS-BruteLogic",
                    githubEditLink: "https://github.com/danielmiessler/SecLists/blob/master/Fuzzing/XSS/XSS-BruteLogic.txt",
                    githubRawLink: "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Fuzzing/XSS/XSS-BruteLogic.txt",
                    githubStars: 43735,
                    description: "An XSS collection assembled for all kinds of browsers and frameworks",
                    installs: 4321
                },
                {
                    title: "payload",
                    githubEditLink: "https://github.com/pgaijin66/XSS-Payloads/blob/master/payload/payload.txt",
                    githubRawLink: "https://raw.githubusercontent.com/pgaijin66/XSS-Payloads/master/payload/payload.txt",
                    githubStars: 858,
                    description: "An XSS collection assembled for all kinds of browsers and frameworks. The description can span multiple lines. An XSS collection assembled for all kinds of browsers and frameworks. The description can span multiple lines.",
                    installs: 790
                }                
            ]
        }
    },
    methods: {
        prettify(num) {
            return func.prettifyShort(num)
        }  
    },
    mounted() {

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
    margin: 32px
    flex-wrap: wrap
    gap: 40px

.testcase-container
    display: flex      
    max-width: 40%
    flex-grow: 1
    flex-shrink: 0
    margin-bottom: 32px

.icon-border
    border-radius: 50%
    border: 1px solid rgba(45, 44, 87, 0.1)
    min-width: 80px    
    min-height: 80px
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