<template>
     <v-app>
        <div id="centered-div">
            Hey! we noticed you're on an unsupported version. Please upgrade to the latest version to continue using Akto. For any queries contact support@akto.io
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