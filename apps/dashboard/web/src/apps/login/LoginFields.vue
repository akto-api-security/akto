<template>
  <v-form
      ref="form"
      lazy-validation
      v-model="formValid"
  >
    <v-text-field
        autocomplete="username"
        name="login"
        label="Email"
        placeholder=""
        type="text"
        required
        outlined
        :rules="formRule.username"
        v-model="formModel.username"
        ref="username"
        @keyup.enter="usernameEnter"
    />
    <v-text-field
        :append-icon="openPassword ? '$fas_eye' : '$fas_eye-slash'"
        autocomplete="shut-up-google"
        name="password"
        label="Password"
        placeholder=""
        :type="openPassword ? 'text' : 'password'"
        :rules="formRule.password"
        required
        outlined
        v-model="formModel.password"
        @click:append="openPassword = !openPassword"
        ref="password"
        @keyup.enter="passwordEnter"
    />
  </v-form>
</template>

<script>
import obj from "@/util/obj"
export default {
  name: "LoginFields",
  props: {
      isSignUp: obj.boolR
  },
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
          (v) => this.validPassword(v)
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
    validPassword(v) {
        if (!this.isSignUp) {
            return !!v  || 'Password cannot be blank'
        }
        if (!v) return false
        let lenFlag = v.length >= 8
        if (!lenFlag) return "at least 8 characters"
        let numFlag = false
        let alphabetFlag = false
        for (let i = 0; i < v.length; i++) {
          let c = v.charAt(i);
          c = c.toUpperCase();
          let alpha = c >= "A" && c <= "Z"
          if (alpha) {
            alphabetFlag = true
          }
          let num = c >= "0" && c <= "9"
          if (num) {
            numFlag = true
          }
        }

        if (!numFlag) return "must contain at least 1 digit"
        if (!alphabetFlag) return "must contain at least 1 alphabet"

        return true

    },
    disableButtons() {
      var valid = this.isValidEmail(this.formModel.username) && this.nonEmptyStr(this.formModel.password)
      var ret = {
        username: this.formModel.username,
        password: this.formModel.password,
        valid
      }
      this.$emit('fieldsChanged', ret)
    },
    usernameEnter() {
      if (this.$refs.username.valid) {
        this.$refs.password.focus()
      }
    },
    passwordEnter() {
      if (this.$refs.username.valid && this.$refs.password.valid ) {
        this.$emit('enterPressed')
      }
    }
  },
  watch: {
    'formModel.username': function () {
      this.disableButtons()
    },
    'formModel.password': function () {
      this.disableButtons()
    }
  },
  mounted() {
    this.$refs.username.focus()
  }
}
</script>

<style scoped>

</style>