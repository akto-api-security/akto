<template>
  <div class="d-flex">
      <div class="nav-column">
        <v-tabs
            v-model="tabName"
            vertical
            active-class="active-tab"
            slider-color="var(--themeColor)"
        >
          <v-tabs-slider color="var(--themeColor)" style="width: 4px"></v-tabs-slider>
          <v-tab
              v-for="(category, index) in integrationsList"
              :key="index"
              @click="onClick(index)"
              class="right-pane-tab"
          >
            {{category.name}}
          </v-tab>
        </v-tabs>

      </div>
      <div
        v-scroll.self="onScroll"
        class="integrations-list"
      >
        <div
          v-for="(category, num) in integrationsList"
          :key="num"
          :id="'num'+num"
          class="category-container"
        >
          <div class="category-header">
            {{category.name}}
          </div>
          <div class="d-flex">
            <integration-logo
              v-for="(item, index) in category.connectors"
              :key="num*100+index"
              :name="item.name"
              @clicked="addConnector(item)"
            />
          </div>
        </div>
      </div>
    </div>
</template>

<script>

import IntegrationLogo from "@/apps/dashboard/shared/components/integrations/IntegrationLogo";
import SlackIntegration from "./SlackIntegration"
import Postman from "./Postman"
import AktoAPIIntegration from "./AktoApiIntegration"
import WebhookIntegration from "./webhook/WebhookIntegration";
import func from "@/util/func";
import AktoGptConfig from "./AktoGptConfig";
import GithubSso from "./GithubSsoIntegration";

export default {
  name: "IntegrationSelector",
  components: {
    IntegrationLogo,
    SlackIntegration,
    Postman,
    AktoAPIIntegration,
    WebhookIntegration,
    AktoGptConfig,
    GithubSso
  },
  data () {
    let integrationsList = [
      {
        name:'Traffic Sources',
        connectors: [{
          name: 'BurpSuite',
          component: AktoAPIIntegration,
          props:{
            title:"Burp",
            tokenUtility:func.testingResultType().BURP,
            avatar_image:"$burpsuite"
          }
        }],
      },
      {
          name: 'API Management',
          connectors: [{
              name: 'Postman',
              component: Postman
          }]
      },
      {
          name: 'SSO',
          connectors: [{
              name: 'GitHub',
              component: GithubSso
          }]
      },
      {
        name: 'Automation',
        connectors: [{
          name: 'Akto API',
          component: AktoAPIIntegration,
          props:{
            title:"External APIs",
            tokenUtility:func.testingResultType().EXTERNAL_API,
            avatar_image:"$restapi"
          }
        },{
          name: 'CI/CD Integeration',
          component: AktoAPIIntegration,
          props:{
            title:"CI/CD Integeration",
            tokenUtility:func.testingResultType().CICD,
            avatar_image:"$cicdicon"
          }
        }]
      },
      {
        name: 'Akto GPT',
        connectors: [{
          name: 'Akto GPT',
          component: AktoGptConfig
        }]
      },
      {
        name: 'Alerts',
        connectors: [{
          name: 'Slack',
          component: SlackIntegration
        },{
          name: 'Webhooks',
          component: WebhookIntegration
        }]
      }
    ]

    return {
      integrationsList: [
          ...integrationsList,
        {
          name: 'All',
          connectors: integrationsList.flatMap(x => x.connectors)
        }
      ],
      offsets: [],
      scrolling: false,
      timeout: null,
      tabName: null
    }
  },
  mounted() {
    // this.addConnector(
    //   {
    //       name: 'Custom Webhooks',
    //       component: WebhookIntegration
    //     }
    // )
  },
  methods: {
    setOffsets() {
      this.integrationsList.forEach((x, i) => {
        this.offsets.push(document.getElementById("num"+i).offsetTop - document.getElementById("num1").offsetTop)
      })
    },
    findActiveIndex(e) {
      if (this.offsets.length !== this.integrationsList.length) {
        this.setOffsets()
      }

      let currentOffset = e.target.scrollTop
      let numIndex = this.offsets.findIndex(x => x > currentOffset - 20)

      this.tabName = numIndex
    },
    onScroll(e) {
      clearTimeout(this.timeout)

      if (this.scrolling) return

      this.timeout = setTimeout(() => this.findActiveIndex(e), 17)
    },
    async onClick(mark) {
      this.scrolling = true
      await document.getElementById("num"+mark).scrollIntoView({behavior: 'smooth', block: 'center'})

      setTimeout(() => this.scrolling = false, 500);
    },
    addConnector(type) {
      this.$emit('connectorSelected', type)
    }
  }
}
</script>

<style lang="sass" scoped>
.integrations-list
  overflow-y: scroll
  height: 500px
  max-height: 500px
  width: 100%

.nav-column
  min-width: 200px
  width: fit-content

.active-tab
  background-color: var(--themeColorDark14)
  color: var(--themeColor)

.right-pane-tab
  justify-content: left
  padding: 16px
  margin: 0px 16px 0px 0px

.category-container
  height: 150px
  width: 100%
  margin-bottom: 32px

.category-header
  width: 100%
  padding-bottom: 8px
  color: var(--themeColorDark)
  border-bottom: 1px solid var(--themeColorDark)
  margin-bottom: 16px
  font-weight: 500
</style>