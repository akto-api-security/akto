<template>
    <div class="upload-file-container">
      <v-btn
        color="#6200EA"
        icon
        @click="onPickFile"
      >
        <v-icon>
          $fas_upload
        </v-icon>
        <span class="label-btn">{{label}}</span>

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
      fileFormat: obj.strR,
      label: obj.strN
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
.label-btn
  color: #6200EA
  font-size: 9px
  position: absolute
  right: 0px
  bottom: -10px

.upload-file-container
  position: relative  
</style>