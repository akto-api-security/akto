<template>
    <div>
      <v-btn
        color="#6200EA"
        icon
        @click="onPickFile"
      >
        <v-icon>
          $fas_upload
        </v-icon>
      </v-btn>
      <input
        type="file"
        style="display: none"
        ref="fileInput"
        :accept="fileFormat"
        @change="onFilePicked"
      />
    </div>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "UploadFile",
    props: {
      fileFormat: obj.strR
    },
    methods: {
      onPickFile () {
        this.$refs.fileInput.click()
      },
      onFilePicked (event) {
        const files = event.target.files
        
        this.$emit("fileChanged", files[0])
      }
    }
}
</script>

<style lang="sass" scoped>

</style>