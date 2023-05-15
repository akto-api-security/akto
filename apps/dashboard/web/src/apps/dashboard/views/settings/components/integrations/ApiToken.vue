<template>
  <div style="min-height:200px; padding-top:20px; margin-bottom:30px">
    <div class="d-flex" style="padding-bottom:10px">
      <v-icon :size="50" style="width: 50px !important">{{                       avatar_image                       }}</v-icon>
      <div class="title-text fd-column jc-sa">{{                       this.title                       }}</div>
    </div>
    <div style="padding-top: 20px;" v-if="burp_tokens.length > 0">
      <div class="d-flex" v-for="item in burpTokensForTable" :key="item.id" style="padding-bottom: 30px">
        <v-hover v-slot="{ hover }">
          <div class="d-flex" style="height: 34px;   line-height: 34px;">
            <div style="width: 150px">
              <h4 class="text-detail">{{                       item.computedTime                       }}</h4>
            </div>
            <div style="width: 350px">
              <span v-if="openPasswordMap[item.id]" class="text-detail">{{                       item.key                       }}</span>
              <span v-else class="text-detail">********************************************************</span>
            </div>
            <div v-if="hover">
              <actions-tray :actions="actions || []" :subject=item></actions-tray>
            </div>
          </div>
        </v-hover>
      </div>
    </div>
    <slot>
      <div class="pt-2">
        <v-btn color="var(--themeColor)" style="color:white" @click="addBurpToken">{{                       addTokenTitle || "Generate Token"                       }}</v-btn>
      </div>
    </slot>

  </div>

</template>

<script>
import ActionsTray from "@/apps/dashboard/shared/components/ActionsTray.vue"
import func from "@/util/func";
import obj from '@/util/obj'
export default {
  name: "ApiToken",
  props: {
    title: obj.strN,
    burp_tokens: obj.arrR,
    avatar_image: obj.str,
    addTokenTitle: obj.strN
  },
  components: {
    ActionsTray
  },
  data() {
    return {
      openPasswordMap: {},
      actions: [
        {
          isValid: item => true,
          icon: item => this.openPasswordMap[item.id] ? '$fas_eye' : '$fas_eye-slash',
          text: item => this.openPasswordMap[item.id] ? 'Show' : 'Hide',
          func: item => this.eyeClicked(item),
          success: (resp, item) => () => { console.log(item) },
          failure: (err, item) => () => { console.log(item) }
        },
        {
          isValid: item => true,
          icon: item => '$fas_trash',
          text: item => 'Delete',
          func: item => this.deleteBurpToken(item),
          success: (resp, item) => () => { console.log(item) },
          failure: (err, item) => () => { console.log(item) },
        }
      ]
    }
  },
  methods: {
    deleteBurpToken(item) {
      // did this because actionsTray needs async func
      return new Promise((resolve, reject) => {
        this.$emit("deleteToken", item.id)
      })
    },
    addBurpToken() {
      this.$emit("generateToken")
    },
    eyeClicked(item) {
      return new Promise((resolve, reject) => {
        this.openPasswordMap[item.id] = !this.openPasswordMap[item.id]
      })
    },
  },
  computed: {
    burpTokensForTable() {
      return this.burp_tokens.map(c => {
        c.computedTime = func.prettifyEpoch(c.timestamp)
        c.openPassword = false
        return c
      })
    }
  },
  watch: {
    burp_tokens: {
      handler() {
        this.openPasswordMap = this.burp_tokens.reduce((m, c) => {
          m[c.id] = false
          return m
        }, {})
      }
    }
  }
}
</script>

<style scoped lang="sass">
.icon-nav-drawer
  padding-top: 25px  
.text-detail
  font-weight: normal
  color: var(--themeColorDark)
  font-size: 13px  
.title-text
  color: var(--themeColorDark)
  font-size: 16px
  font-weight: 500  
  margin-left: 16px
  
</style>