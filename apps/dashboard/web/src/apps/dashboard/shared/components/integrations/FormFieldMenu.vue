<template>
  <v-select
      v-model="currValue"
      :items="items"
      outlined
      :label="label"
      :placeholder="label"
      dense
      color="#6200EA"
      class="form-field-menu"
      @change="$emit('input', currValue)"
  >
    <template slot="selection" slot-scope="data">
      <span class="selected-col-text">{{data.item.text}}</span>
    </template>
  </v-select>
</template>

<script>
import obj from "@/util/obj";

export default {
  name: "FormFieldMenu",
  props: {
    value: {
      required: true
    },
    items: obj.arrR,
    label: obj.strR
  },
  data () {
    return {
      currValue: this.value
    }
  }
}
</script>

<style scoped>
.form-field-menu >>> .v-input__slot {
  min-height: 30px !important;
  height: 30px;
  max-height: 30px;
  color: #FFFFFF;
}

.form-field-menu >>> .v-text-field__details {
  display: none;
}

.form-field-menu {
  width: 200px;
}

.form-field-menu >>> .v-label {
  font-size: 12px;
  color: #6200EA;
  font-weight: 400;
}

.selected-col-text {
  font-size: 14px;
  color: #6200EA;
  font-weight: 500;
  margin-left: 0px !important;
}
</style>