<template>
  <v-form
      ref="form"
      lazy-validation
      v-model="formValid"
  >
    <v-text-field
        autocomplete="off"
        name="login"
        label="Email"
        placeholder=""
        type="text"
        required
        outlined
        :rules="formRule.username"
        v-model="formModel.username"
    />
    <v-text-field
        :append-icon="openPassword ? '$fas_eye' : '$fas_eye-slash'"
        autocomplete="off"
        name="password"
        label="Password"
        placeholder=""
        :type="openPassword ? 'text' : 'password'"
        :rules="formRule.password"
        required
        outlined
        v-model="formModel.password"
        @click:append="openPassword = !openPassword"
    />
  </v-form>
</template>

<script>
export default {
  name: "LoginFields",
  data() {
    return {
      openPassword: false,
      formValid: false,
      formModel: {
        username: window.window.SIGNUP_EMAIL_ID,
        password: null
      },
      formRule: {
        username: [
          (v) => this.nonEmptyStr(v) || '',
          (v) => this.isValidEmail(v) || 'Invalid e-mail id'
        ],
        password: [
          (v) => !!v || ''
        ]
      }
    }
  },
  methods: {
    isValidEmail(v) {
      const pattern = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
      return pattern.test(v)
    },
    nonEmptyStr(v) {
      return !!v
    },
    disableButtons() {
      var valid = this.isValidEmail(this.formModel.username) && this.nonEmptyStr(this.formModel.password)
      var ret = {
        username: this.formModel.username,
        password: this.formModel.password,
        valid
      }
      this.$emit('fieldsChanged', ret)
    }
  },
  watch: {
    'formModel.username': function () {
      this.disableButtons()
    },
    'formModel.password': function () {
      this.disableButtons()
    }
  }
}
</script>

<style scoped>

</style>