<template>

  <v-tooltip bottom>
      <template v-slot:activator="{on,attrs}">
        <div class="upload-file-container" v-bind="attrs" v-on="on">
          <v-btn
            color="var(--themeColorDark)"
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
      {{tooltipText}}
  </v-tooltip>
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "UploadFile",
    props: {
      fileFormat: obj.strR,
      label: obj.strN,
      tooltipText: obj.strN,
      type: obj.strR
    },
    methods: {
      onPickFile () {
        this.$refs.fileInput.click()
      },
      onFilePicked (event) {
        const files = event.target.files
        
        this.$emit("fileChanged", {file: files[0], label: this.label, type: this.type})
        // If you select the same file twice... the onChange() function doesn't get activated
        // So by resetting the value it gets tricked :)
        event.target.value = null
      }
    }
}
</script>

<style lang="sass" scoped>
.label-btn
  color: var(--themeColorDark)
  font-size: 9px
  position: absolute
  right: 0px
  bottom: -10px

.upload-file-container
  position: relative  
</style>