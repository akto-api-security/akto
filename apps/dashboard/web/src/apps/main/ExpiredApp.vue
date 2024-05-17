<template>
     <v-app>
        <div id="centered-div">
            Hey! Your free trial has expired. To continue using Akto, please reach out to support@akto.io
        </div>
    </v-app>
</template>

<script>
export default {
    name: 'ExpiredApp',
    data() {
        return {
        }
    }
}
</script>

<style>

#centered-div {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
}

</style>