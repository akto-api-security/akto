<template>
  <div style="min-height:200px; padding-top:20px">
    <div class="d-flex" style="padding-bottom:10px">
      <v-avatar size="40px" style="padding-right: 15px">
        <img src="data:image/jpeg;base64,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">
      </v-avatar>
      <h2>Burp</h2>
    </div>
    <div style="padding-top: 20px;">
      <div class="d-flex" v-for="item in burpTokensForTable" :key="item.id" style="padding-bottom: 30px">
        <v-hover v-slot="{ hover }">
          <div class="d-flex" style="height: 34px;   line-height: 34px;">
            <div style="width: 150px;">
              <h4 style="font-weight: normal">{{item.computedTime}}</h4>
            </div>
            <div style="width: 500px">
              <v-text-field
                  :append-icon="openPasswordMap[item.id] ? '$fas_eye' : '$fas_eye-slash'"
                  @click:append="eyeClicked(item)"
                  :type="openPasswordMap[item.id] ? 'text' : 'password'"
                  :value="item.key"
                  outlined
                  clearable
                  height="32px"
                  readonly
              />
            </div>
            <div v-if="hover">
              <actions-tray :actions="actions || []" :subject=item></actions-tray>
            </div>
          </div>
        </v-hover>
      </div>
    </div>
    <div>
      <v-btn color="green" style="color:white" @click="addBurpToken">Generate Token</v-btn>
    </div>

  </div>

</template>

<script>
import api from "../../api.js"
import ActionsTray from "@/apps/dashboard/shared/components/ActionsTray.vue"
import func from "@/util/func";
export default {
  name: "Burp",
  components: {
    ActionsTray
  },
  data() {
    return {
      openPasswordMap: {},
      burp_tokens: [],
      actions: [
        {
          isValid: item => true,
          icon: item => '$fas_trash',
          text: item => 'Delete',
          func: item => this.deleteBurpToken(item),
          success: (resp, item) => () => {console.log(item)},
          failure: (err, item) => () => {console.log(item)},

        }
      ]
    }
  },
  methods: {
    deleteBurpToken(item) {
      return api.deleteApiToken(item.id).then((resp) => {
        if (resp.apiTokenDeleted) {
          this.burp_tokens = this.burp_tokens.filter(function(el) { return el.id != item.id; })
        } else {
        }
        return resp
      })
    },
    addBurpToken(item){
      return api.addBurpToken().then((resp) => {
        this.burp_tokens.push(...resp.apiTokenList)
      })
    },
    eyeClicked(item) {
      console.log(item.id)
      this.openPasswordMap[item.id] = !this.openPasswordMap[item.id]
    },
  },
  mounted() {
    api.fetchBurpTokens().then((resp) => {
      this.burp_tokens = resp.apiTokenList
    })
  },
  computed: {
    burpTokensForTable() {
      return this.burp_tokens.map(c => {
        this.openPasswordMap[c.id] = false
        c.computedTime = func.prettifyEpoch(c.timestamp)
        c.openPassword = false
        return c
      })
    }
  }
}
</script>

<style scoped>

</style>