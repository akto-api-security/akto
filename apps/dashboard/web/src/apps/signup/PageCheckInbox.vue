<template>
    <div class="error-background d-flex justify-space-around">
        <div style="width: 450px">
            <div class="d-flex jc-sa">
                <div class="big-icon"><v-icon size="30" color="#6200EA">$fas_envelope</v-icon></div>
            </div>
            <div class="title">Check your email</div>
            <div class="message">We sent you a temporary verification link to your email. Please click on the link to activate your account.</div>
            <div class="login-link">
                <a href="/login">
                    <v-icon size="8">$fas_arrow-left</v-icon>
                    <span>Back to login</span>
                </a>
            </div>
        </div>
    </div>
</template>

<script>

export default {
    name: "PageCheckInbox"
}

</script>

<style scoped lang="sass">
.error-background
  background: var(--errorBackgroundColor)
  height: 100%
  text-align: center
  padding-top: 200px

.big-icon
  border-radius: 50%
  min-width: 56px
  min-height: 56px
  background: #D9D8FF
  display: flex
  justify-content: space-around
  align-items: center


.title
    font-weight: 600
    color: var(--themeColorDark)
    font-size: 28px
    padding-top: 24px

.message
    font-size: 16px
    font-weight: 500
    color: #6D7175
    padding-top: 12px

.login-link
    padding-top: 32px
    color: #2C6ECB
    font-size: 14px
    font-weight: 400

</style>