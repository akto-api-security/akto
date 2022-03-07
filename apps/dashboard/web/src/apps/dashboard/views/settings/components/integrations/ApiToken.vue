<template>
  <div style="min-height:200px; padding-top:20px; margin-bottom:30px">
    <div class="d-flex" style="padding-bottom:10px">
      <v-avatar size="40px" style="margin-right: 15px">
        <img :src="require(`@/assets/${avatar_image}`)" />
      </v-avatar>
      <h2 style="color: #47466A; font-size: 16px; font-weight: 500" class="fd-column jc-sa">{{this.title}}</h2>
    </div>
    <div style="padding-top: 20px;">
      <div class="d-flex" v-for="item in burpTokensForTable" :key="item.id" style="padding-bottom: 30px">
        <v-hover v-slot="{ hover }">
          <div class="d-flex" style="height: 34px;   line-height: 34px;">
            <div style="width: 150px; padding-top: 20px;">
              <h4 style="font-weight: normal; color: #47466A; font-size: 13px">{{item.computedTime}}</h4>
            </div>
            <div style="width: 500px">
              <v-text-field
                  :type="openPasswordMap[item.id] ? 'text' : 'password'"
                  :value="item.key"
                  clearable
                  height="32px"
                  readonly
              >
                <template v-slot:append>
                  <v-icon @click="eyeClicked(item)" class="icon-nav-drawer">{{openPasswordMap[item.id] ? '$fas_eye' : '$fas_eye-slash'}}</v-icon>
                </template>
              
              </v-text-field>
            </div>
            <div v-if="hover" class="pt-4">
              <actions-tray :actions="actions || []" :subject=item></actions-tray>
            </div>
          </div>
        </v-hover>
      </div>
    </div>
    <div class="pt-2">
      <v-btn color="#6200EA" style="color:white" @click="addBurpToken">Generate Token</v-btn>
    </div>

  </div>

</template>

<script>
import ActionsTray from "@/apps/dashboard/shared/components/ActionsTray.vue"
import func from "@/util/func";
import obj from '@/util/obj'
export default {
  name: "ApiToken",
  props: {
    title: obj.strR,
    burp_tokens: obj.arrR,
    avatar_image: obj.str,
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
      // did this because actionsTray needs async func
      return new Promise((resolve, reject) => {
        this.$emit("deleteToken", item.id)
      })
    },
    addBurpToken(){
      this.$emit("generateToken")
    },
    eyeClicked(item) {
      this.openPasswordMap[item.id] = !this.openPasswordMap[item.id]
    },
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

<style scoped lang="sass">
.icon-nav-drawer
  padding-top: 25px  
</style>